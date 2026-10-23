package com.findora.app.core.domain.search

import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Turns a search sentence in English, French or Arabic into a [ParsedIntent]: a structured filter plus the spans
 * it was read from. It is a deterministic, rule-based parser that runs on the device: no network, no model, no
 * keys. The same sentence always gives the same result, which is what makes it unit-testable.
 *
 * Grammar, in the order rules are tried at each word: room counts ("2 bedrooms", "3BHK", "at least 2 baths",
 * "غرفتين", "T3"), ranges ("between 1 500 and 2 000 DT", "1.5-2k"), bounded amounts ("under 2k", "moins de 900 €",
 * "أقل من ٢٠٠٠"), amounts with a currency or multiplier, areas ("80 m²"), then the lexicon of types, must-haves,
 * amenities, lifestyle and sort words. Neighbourhoods and regions are matched last, exactly or with a small typo.
 * Anything left that is not filler is reported back as not understood instead of being guessed at.
 */
object NaturalLanguageQueryParser {
    fun parse(
        query: String,
        regions: List<RegionPack>,
    ): ParsedIntent {
        val currencyRegions =
            regions
                .groupBy { it.currency }
                .filterValues { it.size == 1 }
                .mapValues { (_, packs) -> packs.single().id }
        return ParseSession(query, QueryText.tokenize(query), PlaceIndex.of(regions)).run(currencyRegions)
    }
}

private data class Found(
    val meaning: QueryMeaning,
    val from: Int,
    val to: Int,
)

private data class Quantity(
    val value: Double,
    val scale: Int,
    val currency: Currency?,
    val countWord: Boolean,
    val end: Int,
)

private data class Hit<T>(
    val value: T,
    val end: Int,
)

/** One parse: [consumed] marks tokens a rule has claimed; [found] holds what they meant. */
@Suppress("TooManyFunctions", "ReturnCount") // One small function per grammar rule keeps each rule readable.
private class ParseSession(
    private val query: String,
    private val tokens: List<QueryToken>,
    private val places: PlaceIndex,
) {
    private val consumed = BooleanArray(tokens.size)
    private val found = mutableListOf<Found>()
    private val ignored = mutableListOf<IntRange>()
    private val pendingCurrencies = mutableListOf<Hit<Currency>>()
    private val pendingStarts = mutableListOf<Int>()

    fun run(currencyRegions: Map<Currency, String>): ParsedIntent {
        scan(::matchAt)
        matchPlaces()
        matchBareAmount()
        attachPendingCurrencies()
        val recognized =
            found
                .map { RecognizedToken(tokens[it.from].start, tokens[it.to - 1].end, text(it.from, it.to), it.meaning) }
                .sortedBy { it.start }
        return ParsedIntent(query, recognized, unrecognized(), currencyRegions)
    }

    private fun scan(rule: (Int) -> Int?) {
        var index = 0
        while (index < tokens.size) {
            index = if (consumed[index]) index + 1 else rule(index) ?: (index + 1)
        }
    }

    private fun matchAt(index: Int): Int? =
        rooms(index)
            ?: dualRooms(index)
            ?: frenchRoomCount(index)
            ?: range(index)
            ?: bound(index)
            ?: amount(index)
            ?: area(index)
            ?: lexicon(index)

    // region Token access

    private fun free(index: Int): QueryToken? = tokens.getOrNull(index)?.takeIf { !consumed[index] }

    private fun numberAt(index: Int): QueryToken? = free(index)?.takeIf { it.isNumber && it.value != null }

    private fun symbolIs(
        index: Int,
        symbols: String,
    ): Boolean = free(index)?.let { it.isSymbol && it.norm in symbols } == true

    private fun text(
        from: Int,
        to: Int,
    ): String = query.substring(tokens[from].start, tokens[to - 1].end)

    /** The longest phrase of [table] starting at [start]; a hyphen or slash between two words is skipped. */
    private fun <T> phraseAt(
        start: Int,
        table: List<Phrase<T>>,
    ): Hit<T>? = table.firstNotNullOfOrNull { phrase -> phraseEnd(start, phrase.words)?.let { Hit(phrase.value, it) } }

    private fun phraseEnd(
        start: Int,
        words: List<String>,
    ): Int? {
        var index = start
        words.forEachIndexed { position, word ->
            if (position > 0) while (symbolIs(index, JOINERS)) index++
            val token = free(index) ?: return null
            if (token.isNumber || !QueryText.wordMatches(token.norm, word)) return null
            index++
        }
        return index
    }

    /** An optional currency, a number, an optional "+", and an optional currency after it. */
    private fun quantity(start: Int): Quantity? {
        var index = start
        val before = phraseAt(index, QueryLexicon.currencies)
        if (before != null) index = before.end
        val number = numberAt(index) ?: return null
        index++
        if (symbolIs(index, "+")) index++
        val after = if (before == null) phraseAt(index, QueryLexicon.currencies) else null
        if (after != null) index = after.end
        return Quantity(number.value ?: return null, number.scale, before?.value ?: after?.value, number.countWord, index)
    }

    private fun roomUnitAt(start: Int): Hit<RoomUnit>? {
        val unit = phraseAt(start, QueryLexicon.roomUnits) ?: return null
        val tail = phraseAt(unit.end, QueryLexicon.roomUnitTails)
        return Hit(unit.value, tail?.end ?: unit.end)
    }

    /** An area unit and its factor to square metres: "m2", "m²", "sq ft", "متر مربع". */
    private fun areaUnitAt(start: Int): Hit<Double>? {
        val token = free(start)
        val squared = token?.takeIf { it.isWord }?.let { QueryLexicon.squaredUnits[it.norm] }
        val next = free(start + 1)
        val isSquare =
            next != null &&
                token != null &&
                next.start == token.end &&
                ((next.isNumber && next.value == SQUARE) || (next.isSymbol && next.norm == "²"))
        return if (squared != null && isSquare) Hit(squared, start + 2) else phraseAt(start, QueryLexicon.areaUnits)
    }

    // endregion

    // region Rules

    /** "2 bedrooms", "3BHK", "at least 2 baths", "3+ chambres", "4 pièces", "ثلاث غرف". */
    private fun rooms(index: Int): Int? {
        val qualifier = phraseAt(index, QueryLexicon.bounds)
        var next = qualifier?.end ?: index
        val count = numberAt(next)?.intValue?.takeIf { it in 1..MAX_ROOMS } ?: return null
        next++
        if (symbolIs(next, "+")) next++
        if (symbolIs(next, JOINERS)) next++
        val unit = roomUnitAt(next) ?: return null
        val min = if (qualifier?.value == Bound.MoreThan) count + 1 else count
        when {
            // The filter has no upper bound on rooms: say so rather than pretend "max 2" means "2+".
            qualifier?.value == Bound.AtMost -> ignore(index, unit.end)
            unit.value == RoomUnit.Bedroom -> record(QueryMeaning.Bedrooms(min), index, unit.end)
            unit.value == RoomUnit.Bathroom -> record(QueryMeaning.Bathrooms(min), index, unit.end)
            else -> pieces(min, index, unit.end, apartment = false)
        }
        return unit.end
    }

    /** Arabic duals: "غرفتين" (two bedrooms), "حمامين" (two bathrooms). */
    private fun dualRooms(index: Int): Int? {
        val word = free(index)?.takeIf { it.isWord }?.norm ?: return null
        val forms = QueryText.variants(word)
        val meaning =
            when {
                forms.any { it in QueryLexicon.dualBedrooms } -> QueryMeaning.Bedrooms(2)
                forms.any { it in QueryLexicon.dualBathrooms } -> QueryMeaning.Bathrooms(2)
                else -> return null
            }
        val end = phraseAt(index + 1, QueryLexicon.roomUnitTails)?.end ?: (index + 1)
        record(meaning, index, end)
        return end
    }

    /** French "T3" / "F3": rooms counted with the living room, so T3 is a two-bedroom apartment. */
    private fun frenchRoomCount(index: Int): Int? {
        val letter = free(index)?.takeIf { it.isWord && it.norm in FRENCH_ROOM_LETTERS } ?: return null
        val number = numberAt(index + 1)?.takeIf { it.start == letter.end } ?: return null
        val rooms = number.intValue?.takeIf { it in 1..MAX_FRENCH_ROOMS } ?: return null
        pieces(rooms, index, index + 2, apartment = true)
        return index + 2
    }

    /** French room counts include the living room: "4 pièces" and "T4" both mean three bedrooms. */
    private fun pieces(
        rooms: Int,
        from: Int,
        to: Int,
        apartment: Boolean,
    ) {
        if (rooms > 1) record(QueryMeaning.Bedrooms(rooms - 1), from, to)
        if (apartment) record(QueryMeaning.Type(PropertyType.Apartment), from, to)
        if (rooms <= 1 && !apartment) ignore(from, to)
    }

    /** "between 1 500 and 2 000 DT", "1.5-2k", "de 800 à 1200 €", "2-3 bedrooms", "80 to 120 m²". */
    private fun range(index: Int): Int? {
        val opener = phraseAt(index, QueryLexicon.rangeStarts)
        val first = quantity(opener?.end ?: index) ?: return null
        free(first.end)?.takeIf { it.norm in QueryLexicon.rangeJoins } ?: return null
        val second = quantity(first.end + 1) ?: return null
        val scaledLow = first.value * second.scale
        val low = if (first.scale == 1 && second.scale > 1 && scaledLow <= second.value) scaledLow else first.value
        val high = second.value
        roomUnitAt(second.end)?.let { unit ->
            val min = low.toInt().takeIf { low % 1.0 == 0.0 && it in 1..MAX_ROOMS } ?: return null
            val meaning = if (unit.value == RoomUnit.Bathroom) QueryMeaning.Bathrooms(min) else QueryMeaning.Bedrooms(min)
            record(meaning, index, unit.end)
            return unit.end
        }
        areaUnitAt(second.end)?.let { unit ->
            val (lo, hi) = sorted(low * unit.value, high * unit.value)
            record(QueryMeaning.Area(lo.roundToInt(), hi.roundToInt()), index, unit.end)
            return unit.end
        }
        val currency = first.currency ?: second.currency
        val isMoney =
            currency != null ||
                second.scale > 1 ||
                (opener != null && high >= MIN_AMOUNT_WITH_KEYWORD) ||
                (low >= MIN_BARE_AMOUNT && high >= MIN_BARE_AMOUNT)
        if (!isMoney || first.countWord) return null
        val (lo, hi) = sorted(low, high)
        recordBudget(QueryMeaning.Budget(lo.roundToLong(), hi.roundToLong(), currency), index, second.end)
        return second.end
    }

    /** "under 2k", "moins de 900 €", "أقل من ٢٠٠٠ دينار", "at least 1000", "under 80 m²". */
    private fun bound(index: Int): Int? {
        val keyword = phraseAt(index, QueryLexicon.bounds) ?: return null
        val amount = quantity(keyword.end)?.takeIf { !it.countWord } ?: return null
        areaUnitAt(amount.end)?.let { unit ->
            val sqm = (amount.value * unit.value).roundToInt()
            val meaning =
                if (keyword.value == Bound.AtMost) QueryMeaning.Area(null, sqm) else QueryMeaning.Area(sqm, null)
            record(meaning, index, unit.end)
            return unit.end
        }
        if (roomUnitAt(amount.end) != null) return null
        if (amount.currency == null && amount.scale == 1 && amount.value < MIN_AMOUNT_WITH_KEYWORD) return null
        val value = amount.value.roundToLong()
        val budget =
            if (keyword.value == Bound.AtMost) {
                QueryMeaning.Budget(null, value, amount.currency)
            } else {
                QueryMeaning.Budget(value, null, amount.currency)
            }
        recordBudget(budget, index, amount.end)
        return amount.end
    }

    /** An amount that names a currency or a multiplier on its own: "2000 TND", "€1500", "15k", "2 lakh". */
    private fun amount(index: Int): Int? {
        val amount = quantity(index)?.takeIf { !it.countWord && (it.currency != null || it.scale > 1) } ?: return null
        if (roomUnitAt(amount.end) != null || areaUnitAt(amount.end) != null) return null
        recordBudget(QueryMeaning.Budget(null, amount.value.roundToLong(), amount.currency), index, amount.end)
        return amount.end
    }

    /** "120 m²", "900 sq ft": a size with no bound word reads as a minimum. */
    private fun area(index: Int): Int? {
        val amount = quantity(index)?.takeIf { it.currency == null && !it.countWord } ?: return null
        val unit = areaUnitAt(amount.end) ?: return null
        record(QueryMeaning.Area((amount.value * unit.value).roundToInt(), null), index, unit.end)
        return unit.end
    }

    private fun lexicon(index: Int): Int? {
        phraseAt(index, meaningTable)?.let { hit ->
            val negation = negationBefore(index)
            if (negation != null) ignore(negation, hit.end) else record(hit.value, index, hit.end)
            return hit.end
        }
        phraseAt(index, QueryLexicon.currencies)?.let { hit ->
            pendingCurrencies += hit
            pendingStarts += index
            claim(index, hit.end)
            return hit.end
        }
        return null
    }

    /** "no pets", "sans parking", "pas de balcon", "غير مفروش": the index of the negation word, if any. */
    private fun negationBefore(index: Int): Int? {
        fun isWord(
            at: Int,
            set: Set<String>,
        ): Boolean = free(at)?.let { it.isWord && QueryText.variants(it.norm).any(set::contains) } == true
        return when {
            isWord(index - 1, QueryLexicon.negations) -> index - 1
            isWord(index - 1, NEGATION_LINKS) && isWord(index - 2, QueryLexicon.negations) -> index - 2
            else -> null
        }
    }

    private fun matchPlaces() {
        scan { index -> wordWindow(index).takeIf { it.isNotEmpty() }?.let { exactPlace(index, it) } }
        scan { index -> wordWindow(index).takeIf { it.isNotEmpty() }?.let { fuzzyPlace(it) } }
    }

    private fun exactPlace(
        index: Int,
        window: List<Int>,
    ): Int? {
        val (size, meaning) = places.exact(window.map { tokens[it].norm }) ?: return null
        val end = window[size - 1] + 1
        recordPlace(meaning, index, end)
        return end
    }

    private fun fuzzyPlace(window: List<Int>): Int? {
        for (size in window.size downTo 1) {
            val meaning = places.fuzzy(window.take(size).map { tokens[it].norm }) ?: continue
            val end = window[size - 1] + 1
            recordPlace(meaning, window.first(), end)
            return end
        }
        return null
    }

    /** Up to [MAX_PLACE_WORDS] consecutive free words from [start] ("Saint-Germain-des-Prés" is one window). */
    private fun wordWindow(start: Int): List<Int> {
        val indices = mutableListOf<Int>()
        var index = start
        while (indices.size < MAX_PLACE_WORDS) {
            val token = free(index)
            when {
                token == null -> break
                token.isWord -> indices += index
                !(indices.isNotEmpty() && token.isSymbol && token.norm in JOINERS) -> break
            }
            index++
        }
        return indices
    }

    /** A lone number of 100 or more with nothing else to explain it is read as the budget ceiling. */
    private fun matchBareAmount() {
        if (found.any { it.meaning is QueryMeaning.Budget }) return
        val index =
            tokens.indices.firstOrNull { index ->
                val token = numberAt(index)
                token != null && !token.countWord && (token.value ?: 0.0) >= MIN_BARE_AMOUNT
            } ?: return
        val amount = quantity(index) ?: return
        recordBudget(QueryMeaning.Budget(null, amount.value.roundToLong(), amount.currency), index, amount.end)
    }

    // endregion

    // region Recording

    private fun claim(
        from: Int,
        to: Int,
    ) {
        for (index in from until to) consumed[index] = true
    }

    private fun ignore(
        from: Int,
        to: Int,
    ) {
        claim(from, to)
        ignored += from until to
    }

    /** Records [meaning]; a second, different value for a single-valued slot (two types, two sorts) is ignored. */
    private fun record(
        meaning: QueryMeaning,
        from: Int,
        to: Int,
    ) {
        val clash =
            meaning::class in SINGLE_SLOTS && found.any { it.meaning::class == meaning::class && it.meaning != meaning }
        if (clash) ignore(from, to) else found += Found(meaning, from, to).also { claim(from, to) }
    }

    /** A place or region: one neighbourhood at a time, and a region only if it agrees with it. */
    private fun recordPlace(
        meaning: QueryMeaning,
        from: Int,
        to: Int,
    ) {
        val regionOf: (QueryMeaning) -> String? = {
            when (it) {
                is QueryMeaning.Place -> it.regionId
                is QueryMeaning.Region -> it.regionId
                else -> null
            }
        }
        val existing = found.map { it.meaning }.filter { regionOf(it) != null }
        val conflicts =
            existing.any { regionOf(it) != regionOf(meaning) } ||
                (meaning is QueryMeaning.Place && existing.any { it is QueryMeaning.Place && it != meaning })
        if (conflicts) ignore(from, to) else record(meaning, from, to)
    }

    /** Budgets merge: "over 1000 under 2000" is one 1,000–2,000 chip; contradictions are reported, not applied. */
    private fun recordBudget(
        budget: QueryMeaning.Budget,
        from: Int,
        to: Int,
    ) {
        val existing = found.firstOrNull { it.meaning is QueryMeaning.Budget }?.meaning as QueryMeaning.Budget?
        if (existing == null) {
            record(budget, from, to)
            return
        }
        val overlaps = (existing.min != null && budget.min != null) || (existing.max != null && budget.max != null)
        val merged =
            QueryMeaning.Budget(
                min = existing.min ?: budget.min,
                max = existing.max ?: budget.max,
                currency = existing.currency ?: budget.currency,
            )
        val clashes =
            overlaps ||
                (merged.min != null && merged.max != null && merged.min > merged.max) ||
                (existing.currency != null && budget.currency != null && existing.currency != budget.currency)
        if (clashes) {
            ignore(from, to)
        } else {
            replaceBudget(merged)
            found += Found(merged, from, to)
            claim(from, to)
        }
    }

    private fun replaceBudget(budget: QueryMeaning.Budget) {
        found.replaceAll { if (it.meaning is QueryMeaning.Budget) it.copy(meaning = budget) else it }
    }

    /** A currency named apart from the number ("2000, in dinars") joins the budget; on its own it means nothing. */
    private fun attachPendingCurrencies() {
        pendingCurrencies.forEachIndexed { position, pending ->
            val start = pendingStarts[position]
            val currency = pending.value
            val budget = found.firstOrNull { it.meaning is QueryMeaning.Budget }?.meaning as QueryMeaning.Budget?
            if (budget == null || (budget.currency != null && budget.currency != currency)) {
                ignored += start until pending.end
            } else {
                val merged = budget.copy(currency = currency)
                replaceBudget(merged)
                found += Found(merged, start, pending.end)
            }
        }
    }

    /** Runs of words nobody claimed (filler words aside) plus ignored spans, as they were typed. */
    private fun unrecognized(): List<String> {
        val flagged =
            BooleanArray(tokens.size) { index ->
                val token = tokens[index]
                !consumed[index] &&
                    when {
                        token.isWord -> QueryText.variants(token.norm).none(QueryLexicon.stopwords::contains)
                        token.isNumber -> !token.countWord
                        else -> false
                    }
            }
        ignored.forEach { range -> range.forEach { flagged[it] = true } }
        val runs = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            if (!flagged[index]) {
                index++
                continue
            }
            val start = index
            while (index + 1 < tokens.size && (flagged[index + 1] || isGlue(index + 1) && flagged.getOrElse(index + 2) { false })) index++
            runs += text(start, index + 1)
            index++
        }
        return runs.distinct()
    }

    private fun isGlue(index: Int): Boolean = tokens[index].isSymbol && tokens[index].norm in JOINERS

    private fun sorted(
        a: Double,
        b: Double,
    ): Pair<Double, Double> = if (a <= b) a to b else b to a

    // endregion

    private companion object {
        const val JOINERS = "-–—/"
        const val SQUARE = 2.0
        const val MAX_ROOMS = 20
        const val MAX_FRENCH_ROOMS = 9
        const val MAX_PLACE_WORDS = 5
        const val MIN_BARE_AMOUNT = 100.0
        const val MIN_AMOUNT_WITH_KEYWORD = 50.0
        val FRENCH_ROOM_LETTERS = setOf("t", "f")
        val NEGATION_LINKS = setOf("de", "d", "any", "of", "a")
        val SINGLE_SLOTS =
            setOf(
                QueryMeaning.Bedrooms::class,
                QueryMeaning.Bathrooms::class,
                QueryMeaning.Type::class,
                QueryMeaning.Sort::class,
                QueryMeaning.Area::class,
            )

        /** Types, must-haves, amenities, lifestyle and sort words in one table, longest phrase first. */
        val meaningTable: List<Phrase<QueryMeaning>> =
            (
                QueryLexicon.features +
                    QueryLexicon.types.map { Phrase<QueryMeaning>(it.words, QueryMeaning.Type(it.value)) } +
                    QueryLexicon.lifestyle.map { Phrase<QueryMeaning>(it.words, QueryMeaning.Lifestyle(it.value)) } +
                    QueryLexicon.sorts.map { Phrase<QueryMeaning>(it.words, QueryMeaning.Sort(it.value)) }
            ).sortedByDescending { it.words.size }
    }
}
