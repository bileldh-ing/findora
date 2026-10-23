package com.findora.app.core.domain.search

import java.text.Normalizer
import java.util.Locale

internal enum class TokenKind { Number, Word, Symbol }

/**
 * One piece of a search sentence. [start] and [end] (exclusive) index the original text so the UI can point at
 * what was understood; [norm] is the folded form used for matching (lower case, no accents, Arabic letter
 * variants unified). A number carries its [value]; [scale] is the multiplier it was written with ("2k" is 2,000
 * at scale 1,000) and [countWord] marks spelled-out counts ("two", "deux", "ثلاث").
 */
internal data class QueryToken(
    val kind: TokenKind,
    val start: Int,
    val end: Int,
    val norm: String,
    val value: Double? = null,
    val scale: Int = 1,
    val countWord: Boolean = false,
) {
    val isNumber: Boolean get() = kind == TokenKind.Number
    val isWord: Boolean get() = kind == TokenKind.Word
    val isSymbol: Boolean get() = kind == TokenKind.Symbol
    val intValue: Int? get() = value?.takeIf { it % 1.0 == 0.0 && it <= Int.MAX_VALUE }?.toInt()
}

/** Splits text into [QueryToken]s and folds words for accent-, case- and script-variant-insensitive matching. */
internal object QueryText {
    private const val ARABIC_INDIC_ZERO = '\u0660'
    private const val EXTENDED_ARABIC_INDIC_ZERO = '\u06F0'
    private const val DIGITS = 10
    private const val ARABIC_DECIMAL_SEPARATOR = '\u066B'
    private const val ARABIC_THOUSANDS_SEPARATOR = '\u066C'
    private const val TATWEEL = '\u0640'
    private const val ARABIC_BLOCK_START = '\u0600'
    private const val ARABIC_BLOCK_END = '\u06FF'
    private const val ARABIC_PREFIX_LETTERS = "وبلفك"
    private const val ARABIC_ARTICLE = "ال"
    private const val MIN_PREFIXED_LENGTH = 4

    /** 1 000 / 1,000 / 1.000 thousands groups, then plain or decimal numbers, then words, then useful symbols. */
    private val tokenPattern =
        Regex(
            "(?<![\\p{L}\\d])\\d{1,3}(?:[ \\u00A0\\u202F.,]\\d{3})+(?!\\d)" +
                "|\\d+(?:[.,]\\d+)?" +
                "|[\\p{L}\\p{M}]+" +
                "|[€$₹£+\\-–—~²/]",
        )
    private val groupedNumber = Regex("\\d{1,3}(?:[ \\u00A0\\u202F.,]\\d{3})+")
    private val groupSeparators = Regex("[ \\u00A0\\u202F.,]")

    fun tokenize(query: String): List<QueryToken> {
        val prepared = buildString(query.length) { query.forEach { append(asciiDigit(it)) } }
        val raw =
            tokenPattern
                .findAll(prepared)
                .map { match ->
                    val text = match.value
                    val start = match.range.first
                    val end = match.range.last + 1
                    when {
                        text.first().isDigit() -> QueryToken(TokenKind.Number, start, end, text, value = number(text))
                        text.first().isLetter() || Character.getType(text.first()) == Character.NON_SPACING_MARK.toInt() ->
                            QueryToken(TokenKind.Word, start, end, fold(text))
                        else -> QueryToken(TokenKind.Symbol, start, end, text)
                    }
                }.toList()
        return mergeNumbers(raw)
    }

    /** Lower case, accents and Arabic diacritics removed, alef/teh marbuta/alef maqsura/hamza seats unified. */
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            decomposed.forEach { char ->
                when {
                    Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
                    char == TATWEEL -> Unit
                    char == 'ة' -> append('ه')
                    char == 'ى' -> append('ي')
                    char == 'œ' -> append("oe")
                    else -> append(char)
                }
            }
        }
    }

    fun isArabic(word: String): Boolean = word.isNotEmpty() && word.first() in ARABIC_BLOCK_START..ARABIC_BLOCK_END

    /**
     * The forms an Arabic word may take once its attached conjunction or preposition (و ب ل ف ك) and article (ال)
     * are dropped, so "بالمرسى" and "مفروشة" meet "المرسى" and "مفروش" in the lexicon. Latin words match as is.
     */
    fun variants(word: String): Set<String> {
        if (!isArabic(word)) return setOf(word)
        val forms = mutableSetOf(word)
        if (word.length >= MIN_PREFIXED_LENGTH && word.first() in ARABIC_PREFIX_LETTERS) forms += word.substring(1)
        if (word.startsWith("لل") && word.length > MIN_PREFIXED_LENGTH) forms += word.substring(2)
        forms.toList().forEach { form ->
            if (form.startsWith(ARABIC_ARTICLE) && form.length >= MIN_PREFIXED_LENGTH) forms += form.substring(2)
        }
        forms.toList().forEach { form -> if (form.endsWith('ه') && form.length >= MIN_PREFIXED_LENGTH) forms += form.dropLast(1) }
        return forms
    }

    fun wordMatches(
        token: String,
        keyword: String,
    ): Boolean = token == keyword || (isArabic(token) && variants(token).any { it in variants(keyword) })

    private fun asciiDigit(char: Char): Char =
        when (char) {
            in ARABIC_INDIC_ZERO until ARABIC_INDIC_ZERO + DIGITS -> '0' + (char - ARABIC_INDIC_ZERO)
            in EXTENDED_ARABIC_INDIC_ZERO until EXTENDED_ARABIC_INDIC_ZERO + DIGITS -> '0' + (char - EXTENDED_ARABIC_INDIC_ZERO)
            ARABIC_DECIMAL_SEPARATOR -> '.'
            ARABIC_THOUSANDS_SEPARATOR -> ','
            else -> char
        }

    private fun number(text: String): Double? =
        if (groupedNumber.matches(text)) {
            text.replace(groupSeparators, "").toDoubleOrNull()
        } else {
            text.replace(',', '.').toDoubleOrNull()
        }

    /** Turns count words into numbers and folds multipliers into the number before them ("2 k", "deux mille"). */
    private fun mergeNumbers(raw: List<QueryToken>): List<QueryToken> {
        val out = ArrayList<QueryToken>(raw.size)
        var index = 0
        while (index < raw.size) {
            val token = raw[index]
            val base = asNumber(token)
            val next = raw.getOrNull(index + 1)
            val multiplier = next?.takeIf { it.isWord }?.let { word -> QueryLexicon.multiplier(word.norm) }
            if (base != null && base.scale == 1 && multiplier != null && base.value != null) {
                out += base.copy(end = next.end, value = base.value * multiplier, scale = multiplier, countWord = false)
                index += 2
            } else {
                out += base ?: token
                index += 1
            }
        }
        return out
    }

    private fun asNumber(token: QueryToken): QueryToken? =
        when {
            token.isNumber -> token
            !token.isWord -> null
            else ->
                QueryLexicon.countWord(token.norm)?.let { token.copy(kind = TokenKind.Number, value = it.toDouble(), countWord = true) }
                    ?: QueryLexicon.spelledAmount(token.norm)?.let { (value, scale) ->
                        token.copy(kind = TokenKind.Number, value = value.toDouble(), scale = scale)
                    }
        }
}
