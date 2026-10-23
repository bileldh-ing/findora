package com.findora.app.core.domain.chat

import java.text.Normalizer

/** What a renter's message asks about. One message can touch several topics. */
enum class AgentTopic { Greeting, Availability, Viewing, Price, Pets, Parking, Furnished, Size, Thanks }

/** When the renter wants to visit, when the message says so. */
enum class VisitWindow { Any, Tomorrow, ThisWeek }

/**
 * Finds the topics of an English, French or Arabic message with a small keyword lexicon. Matching is done on
 * folded tokens: lower case, accents and Arabic diacritics removed, Arabic letter variants unified and the
 * article or a one-letter clitic stripped. A keyword ending in `*` matches any token it starts.
 */
object AgentTopics {
    private val lexicon: Map<AgentTopic, List<String>> =
        mapOf(
            AgentTopic.Greeting to
                listOf("hi", "hello", "hey", "bonjour", "salut", "bonsoir", "coucou", "مرحبا", "اهلا", "سلام", "salam"),
            AgentTopic.Availability to
                listOf(
                    "available*",
                    "availab*",
                    "still",
                    "vacant",
                    "taken",
                    "disponib*",
                    "libre",
                    "toujours",
                    "encore",
                    "متاح*",
                    "متوفر*",
                    "شاغر*",
                    "مازال",
                    "لازال",
                    "موجود*",
                ),
            AgentTopic.Viewing to
                listOf(
                    "visit*",
                    "viewing*",
                    "tour*",
                    "see",
                    "appointment*",
                    "come",
                    "visite*",
                    "visiter",
                    "voir",
                    "rdv",
                    "rendez*",
                    "passer",
                    "زيار*",
                    "ازور",
                    "نزور",
                    "معاين*",
                    "اشوف",
                    "موعد*",
                ),
            AgentTopic.Price to
                listOf(
                    "price*",
                    "rent",
                    "cost*",
                    "negotia*",
                    "discount*",
                    "cheaper",
                    "deal",
                    "offer",
                    "budget",
                    "prix",
                    "loyer*",
                    "negoci*",
                    "cout*",
                    "remise",
                    "reduction*",
                    "سعر*",
                    "اسعار",
                    "ايجار*",
                    "تفاوض*",
                    "تخفيض*",
                    "خصم*",
                    "ثمن*",
                ),
            AgentTopic.Pets to
                listOf(
                    "pet",
                    "pets",
                    "dog*",
                    "cat",
                    "cats",
                    "kitten*",
                    "pupp*",
                    "animal*",
                    "chien*",
                    "chiot*",
                    "animaux",
                    "حيوان*",
                    "قطه",
                    "قطط",
                    "كلب*",
                ),
            AgentTopic.Parking to
                listOf(
                    "parking*",
                    "park",
                    "car",
                    "cars",
                    "garage*",
                    "stationnement",
                    "voiture*",
                    "موقف*",
                    "سياره*",
                    "جراج",
                    "كراج",
                    "باركينغ",
                ),
            AgentTopic.Furnished to listOf("furnish*", "furniture", "meubl*", "mobilier", "مفروش*", "اثاث*"),
            AgentTopic.Size to
                listOf(
                    "bedroom*",
                    "bathroom*",
                    "rooms",
                    "size",
                    "sqm",
                    "m2",
                    "area",
                    "chambre*",
                    "surface",
                    "pieces",
                    "superficie",
                    "غرف*",
                    "غرفه",
                    "مساحه",
                    "حمام*",
                ),
            AgentTopic.Thanks to listOf("thank*", "thx", "merci", "شكرا*"),
        )

    private val tomorrowWords = listOf("tomorrow", "demain", "غدا", "بكره", "بكرة")
    private val weekWords = listOf("week", "semaine", "اسبوع*", "الاسبوع")

    fun topics(message: String): Set<AgentTopic> {
        val tokens = tokens(message)
        return lexicon.filterValues { keywords -> keywords.any { keyword -> tokens.any { matches(it, keyword) } } }.keys
    }

    fun visitWindow(message: String): VisitWindow {
        val tokens = tokens(message)
        return when {
            tokenMatchesAny(tokens, tomorrowWords) -> VisitWindow.Tomorrow
            tokenMatchesAny(tokens, weekWords) -> VisitWindow.ThisWeek
            else -> VisitWindow.Any
        }
    }

    private fun tokenMatchesAny(
        tokens: List<String>,
        keywords: List<String>,
    ): Boolean = keywords.any { keyword -> tokens.any { matches(it, keyword) } }

    private fun matches(
        token: String,
        keyword: String,
    ): Boolean {
        val folded = fold(keyword.removeSuffix("*"))
        val prefix = keyword.endsWith("*")
        return variants(token).any { if (prefix) it.startsWith(folded) else it == folded }
    }

    /** The token itself and, for Arabic, the token without its article or a leading conjunction / preposition. */
    private fun variants(token: String): List<String> {
        if (token.none { it in ARABIC_RANGE }) return listOf(token)
        val out = mutableListOf(token)
        ARABIC_PREFIXES.filter { token.startsWith(it) && token.length > it.length + 1 }.forEach {
            out += token.removePrefix(it)
        }
        return out
    }

    internal fun tokens(message: String): List<String> =
        fold(message)
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.isNotEmpty() }

    /** Lower case, no accents or Arabic diacritics, unified alef / teh marbuta / alef maksura. */
    internal fun fold(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("²", "2")

    private val ARABIC_RANGE = '\u0600'..'\u06FF'
    private val ARABIC_PREFIXES = listOf("وال", "بال", "فال", "كال", "لل", "ال", "و", "ب", "ف", "ل")
}
