package com.findora.app.core.domain.search

import com.findora.app.core.domain.model.RegionPack

/** A name a place can be called by, folded and split into words. */
internal data class PlaceName(
    val words: List<String>,
    val meaning: QueryMeaning,
    /** Aliases match exactly only, so a short nickname ("marina") never swallows an ordinary word ("marine"). */
    val allowsTypos: Boolean,
) {
    val joined: String = words.joinToString(" ")
}

/**
 * Every neighbourhood and region of the region packs under the names people type: the canonical name, the part
 * before a comma ("Kowdiar"), the name without its article ("Marsa", "Marais"), and well-known English, French
 * and Arabic aliases. Exact matches always win; a Latin-script name of five letters or more also accepts a
 * typo (one edit, two for names of nine letters or more) when the first letter agrees.
 */
internal class PlaceIndex private constructor(
    private val names: List<PlaceName>,
) {
    /** The longest place name that starts at [words] exactly (0 when none), and its meaning. */
    fun exact(words: List<String>): Pair<Int, QueryMeaning>? =
        names
            .filter { it.words.size <= words.size }
            .filter { name -> name.words.indices.all { QueryText.wordMatches(words[it], name.words[it]) } }
            .maxByOrNull { it.words.size }
            ?.let { it.words.size to it.meaning }

    /** The closest place name within typo distance of [words] taken as a whole (Latin script only). */
    fun fuzzy(words: List<String>): QueryMeaning? {
        val text = words.joinToString(" ")
        if (text.length < MIN_FUZZY_LENGTH || words.any(QueryText::isArabic)) return null
        val allowed = if (text.length >= LONG_NAME_LENGTH) 2 else 1
        return names
            .asSequence()
            .filter {
                it.allowsTypos &&
                    it.words.size == words.size &&
                    it.joined.length >= MIN_FUZZY_LENGTH &&
                    it.joined[0] == text[0]
            }.map { it to editDistance(text, it.joined, allowed) }
            .filter { (_, distance) -> distance in 1..allowed }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.meaning
    }

    companion object {
        private const val MIN_FUZZY_LENGTH = 5
        private const val LONG_NAME_LENGTH = 9
        private const val MIN_ARTICLE_STRIPPED_LENGTH = 4
        private val articles = setOf("la", "le", "les", "el", "al", "the")
        private val splitter = Regex("[^\\p{L}\\p{N}]+")

        /** Aliases by canonical locality or region id, for names the seed data does not spell out. */
        private val aliases: Map<String, List<String>> =
            mapOf(
                "kerala" to listOf("keralam", "كيرالا"),
                "tunis" to listOf("tunisia", "tunisie", "تونس"),
                "paris" to listOf("باريس"),
                "dubai" to listOf("دبي", "uae"),
                "Kochi" to listOf("cochin", "كوتشي"),
                "Fort Kochi" to listOf("fort cochin"),
                "Kozhikode Beach" to listOf("kozhikode", "calicut"),
                "Thrissur Round" to listOf("thrissur"),
                "La Marsa" to listOf("المرسى"),
                "Carthage" to listOf("قرطاج"),
                "Sidi Bou Said" to listOf("sidi bousaid", "سيدي بوسعيد", "سيدي بو سعيد"),
                "Gammarth" to listOf("قمرت"),
                "Les Berges du Lac" to listOf("lac", "البحيرة", "ضفاف البحيرة"),
                "Ennasr" to listOf("el nasr", "nasr", "النصر"),
                "El Menzah" to listOf("المنزه"),
                "Lafayette" to listOf("لافايات", "لافاييت"),
                "Montmartre" to listOf("مونمارتر"),
                "Canal Saint-Martin" to listOf("canal st martin"),
                "Saint-Germain-des-Prés" to listOf("saint germain", "st germain"),
                "Dubai Marina" to listOf("marina", "مرسى دبي", "دبي مارينا"),
                "Jumeirah Lake Towers" to listOf("jlt"),
                "Downtown Dubai" to listOf("downtown", "وسط دبي", "داون تاون"),
                "Business Bay" to listOf("الخليج التجاري"),
                "Jumeirah Village Circle" to listOf("jvc"),
                "Al Barsha" to listOf("البرشاء"),
                "Palm Jumeirah" to listOf("the palm", "palm", "نخلة جميرا"),
                "Deira" to listOf("ديرة"),
            )

        private fun words(text: String): List<String> = QueryText.fold(text).split(splitter).filter(String::isNotEmpty)

        private fun forms(name: String): Set<List<String>> {
            val full = words(name)
            val forms = mutableSetOf(full)
            if (name.contains(',')) forms += words(name.substringBefore(','))
            forms.toList().forEach { form ->
                val rest = form.drop(1)
                if (form.first() in articles &&
                    rest.joinToString(" ").length >= MIN_ARTICLE_STRIPPED_LENGTH
                ) {
                    forms += rest
                }
            }
            return forms
        }

        private fun named(
            canonical: Set<List<String>>,
            aliasNames: List<String>?,
            meaning: QueryMeaning,
        ): List<PlaceName> =
            canonical.map { PlaceName(it, meaning, allowsTypos = true) } +
                aliasNames.orEmpty().map { PlaceName(words(it), meaning, allowsTypos = false) }

        fun of(regions: List<RegionPack>): PlaceIndex {
            val names = mutableListOf<PlaceName>()
            regions.forEach { region ->
                val regionMeaning = QueryMeaning.Region(region.id, region.name)
                names += named(forms(region.name), aliases[region.id], regionMeaning)
                region.localities.forEach { locality ->
                    names +=
                        named(
                            forms(locality.name),
                            aliases[locality.name],
                            QueryMeaning.Place(locality.name, region.id),
                        )
                }
            }
            // A neighbourhood named like its region ("Kochi") stays a neighbourhood; region names never shadow it.
            return PlaceIndex(names.sortedBy { if (it.meaning is QueryMeaning.Place) 0 else 1 })
        }

        /** Levenshtein distance, stopping early once it exceeds [limit] (then returns limit + 1). */
        internal fun editDistance(
            a: String,
            b: String,
            limit: Int,
        ): Int {
            if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                if (current.min() > limit) return limit + 1
                previous = current
            }
            return previous[b.length]
        }
    }
}
