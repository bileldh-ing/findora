package com.findora.app.core.domain.search

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SortOrder

/** What a number counts when a unit follows it. */
internal enum class RoomUnit { Bedroom, Bathroom, Piece }

/** How a budget or size keyword bounds the number after it. */
internal enum class Bound { AtMost, AtLeast, MoreThan }

/** A lexicon phrase, already folded and split into words. */
internal data class Phrase<T>(
    val words: List<String>,
    val value: T,
)

/**
 * The English, French and Arabic vocabulary of the parser. Every phrase is folded with [QueryText.fold] when the
 * object loads, so matching is case-, accent- and Arabic-variant-insensitive. Lists are ordered longest phrase
 * first so "pet friendly" wins over "pet" and "salle de bain" over "salle".
 */
internal object QueryLexicon {
    private val splitter = Regex("[^\\p{L}\\p{N}€$₹£~]+")

    private fun words(phrase: String): List<String> = QueryText.fold(phrase).split(splitter).filter(String::isNotEmpty)

    private fun <T> table(vararg groups: Pair<T, List<String>>): List<Phrase<T>> =
        groups
            .flatMap { (value, phrases) -> phrases.map { Phrase(words(it), value) } }
            .filter { it.words.isNotEmpty() }
            .sortedByDescending { it.words.size }

    private fun wordSet(vararg items: String): Set<String> = items.map(QueryText::fold).toSet()

    private val counts: Map<String, Int> =
        listOf(
            1 to "one un une واحد واحدة",
            2 to "two deux اثنين اثنتين اثنان إثنين",
            3 to "three trois ثلاث ثلاثة",
            4 to "four quatre أربع أربعة",
            5 to "five cinq خمس خمسة",
            6 to "six ست ستة",
        ).flatMap { (value, list) -> list.split(' ').map { QueryText.fold(it) to value } }.toMap()

    private const val THOUSAND = 1_000
    private const val LAKH = 100_000

    private val multipliers: Map<String, Int> =
        (
            listOf("k", "thousand", "thousands", "mille", "milles", "ألف", "آلاف", "الف").map { it to THOUSAND } +
                listOf("lakh", "lakhs", "lac", "lacs").map { it to LAKH }
        ).associate { (word, value) -> QueryText.fold(word) to value }

    private val spelledAmounts: Map<String, Pair<Int, Int>> =
        mapOf(
            QueryText.fold("ألف") to (THOUSAND to THOUSAND),
            QueryText.fold("ألفين") to (2 * THOUSAND to THOUSAND),
            QueryText.fold("ألفان") to (2 * THOUSAND to THOUSAND),
        )

    fun countWord(norm: String): Int? = QueryText.variants(norm).firstNotNullOfOrNull { counts[it] }

    fun multiplier(norm: String): Int? = multipliers[norm]

    fun spelledAmount(norm: String): Pair<Int, Int>? =
        QueryText.variants(norm).firstNotNullOfOrNull { spelledAmounts[it] }

    val bounds: List<Phrase<Bound>> =
        table(
            Bound.AtMost to
                listOf(
                    "under",
                    "below",
                    "max",
                    "maximum",
                    "less than",
                    "up to",
                    "upto",
                    "within",
                    "budget",
                    "budget of",
                    "my budget is",
                    "budget is",
                    "not more than",
                    "no more than",
                    "at most",
                    "cheaper than",
                    "max of",
                    "around",
                    "about",
                    "approx",
                    "approximately",
                    "roughly",
                    "~",
                    "moins de",
                    "sous",
                    "jusqu a",
                    "pas plus de",
                    "budget de",
                    "budget max",
                    "inferieur a",
                    "au plus",
                    "max de",
                    "maximum de",
                    "environ",
                    "autour de",
                    "أقل من",
                    "تحت",
                    "لا يتجاوز",
                    "بحدود",
                    "ميزانية",
                    "ميزانيتي",
                    "حد أقصى",
                    "بحد أقصى",
                    "أقصى",
                    "حوالي",
                    "تقريبا",
                ),
            Bound.AtLeast to
                listOf(
                    "over",
                    "above",
                    "at least",
                    "min",
                    "minimum",
                    "starting at",
                    "starting from",
                    "min of",
                    "au moins",
                    "a partir de",
                    "superieur a",
                    "minimum de",
                    "au minimum",
                    "على الأقل",
                    "فوق",
                    "بدءا من",
                ),
            Bound.MoreThan to listOf("more than", "plus de", "أكثر من"),
        )

    val rangeStarts: List<Phrase<Unit>> = table(Unit to listOf("between", "from", "entre", "de", "بين", "من"))

    val rangeJoins: Set<String> = wordSet("and", "to", "et", "a", "au", "و", "إلى", "الى", "حتى", "-", "–", "—")

    val roomUnits: List<Phrase<RoomUnit>> =
        table(
            RoomUnit.Bedroom to
                listOf(
                    "bhk",
                    "bed",
                    "beds",
                    "bedroom",
                    "bedrooms",
                    "bed room",
                    "bed rooms",
                    "br",
                    "bd",
                    "bdr",
                    "bdrm",
                    "room",
                    "rooms",
                    "chambre",
                    "chambres",
                    "ch",
                    "غرفة",
                    "غرف",
                    "غرفة نوم",
                    "غرف نوم",
                ),
            RoomUnit.Bathroom to
                listOf(
                    "bath",
                    "baths",
                    "bathroom",
                    "bathrooms",
                    "ba",
                    "salle de bain",
                    "salles de bain",
                    "salle de bains",
                    "salles de bains",
                    "sdb",
                    "salle d eau",
                    "حمام",
                    "حمامات",
                    "دورة مياه",
                    "دورات مياه",
                ),
            RoomUnit.Piece to listOf("piece", "pieces"),
        )

    /** Words that may follow a room unit and belong to it ("غرفة نوم", "chambre a coucher"). */
    val roomUnitTails: List<Phrase<Unit>> = table(Unit to listOf("نوم", "a coucher"))

    val dualBedrooms: Set<String> = wordSet("غرفتين", "غرفتان", "غرفتي")
    val dualBathrooms: Set<String> = wordSet("حمامين", "حمامان")

    /** Letters that make an area unit when "2" or "²" follows them ("m2", "م2", "ft²"), with their factor. */
    val squaredUnits: Map<String, Double> = mapOf("m" to 1.0, QueryText.fold("م") to 1.0, "ft" to SQ_FT_IN_SQM)

    /** Area units with the factor that turns them into square metres. */
    val areaUnits: List<Phrase<Double>> =
        table(
            1.0 to
                listOf(
                    "m",
                    "sqm",
                    "sq m",
                    "square meters",
                    "square metres",
                    "square meter",
                    "square metre",
                    "metres carres",
                    "metre carre",
                    "meters",
                    "metres",
                    "متر",
                    "متر مربع",
                    "م",
                    "مترمربع",
                ),
            SQ_FT_IN_SQM to listOf("sqft", "sq ft", "square feet", "square foot", "ft", "feet"),
        )

    val currencies: List<Phrase<Currency>> =
        table(
            Currency.TND to listOf("tnd", "dt", "dinar", "dinars", "د ت", "دينار", "دنانير", "مليم"),
            Currency.EUR to listOf("eur", "euro", "euros", "€", "يورو"),
            Currency.USD to listOf("usd", "dollar", "dollars", "$", "دولار"),
            Currency.AED to listOf("aed", "dirham", "dirhams", "dhs", "dh", "درهم", "دراهم"),
            Currency.INR to listOf("inr", "rs", "rupee", "rupees", "₹", "روبية"),
        )

    /** Furnished/pets/parking/verified flags and amenities; a preceding negation ("no pets") cancels them. */
    val features: List<Phrase<QueryMeaning>> =
        table(
            QueryMeaning.PetFriendly to
                listOf(
                    "pet friendly",
                    "pets ok",
                    "pets allowed",
                    "pets welcome",
                    "pet",
                    "pets",
                    "dog",
                    "dogs",
                    "cat",
                    "cats",
                    "animaux",
                    "animaux acceptes",
                    "animaux admis",
                    "animal",
                    "chien",
                    "chat",
                    "حيوانات",
                    "حيوانات أليفة",
                    "حيوان أليف",
                    "حيوان",
                    "قطة",
                    "كلب",
                ),
            QueryMeaning.Parking to
                listOf(
                    "parking",
                    "car park",
                    "garage",
                    "covered parking",
                    "stationnement",
                    "place de parking",
                    "موقف",
                    "موقف سيارات",
                    "مرآب",
                    "كراج",
                    "جراج",
                    "باركينغ",
                ),
            QueryMeaning.Furnished to
                listOf(
                    "furnished",
                    "fully furnished",
                    "meuble",
                    "meublee",
                    "meubles",
                    "meublees",
                    "entierement meuble",
                    "مفروش",
                    "مفروشة",
                ),
            QueryMeaning.Verified to
                listOf("verified", "verifie", "verifiee", "verifies", "certifie", "موثق", "موثقة", "موثوق"),
            QueryMeaning.Feature(Amenity.Balcony) to
                listOf("balcony", "balconies", "terrace", "balcon", "terrasse", "شرفة", "بلكون", "بلكونة", "تراس"),
            QueryMeaning.Feature(Amenity.SwimmingPool) to
                listOf("pool", "swimming pool", "piscine", "مسبح", "حوض سباحة"),
            QueryMeaning.Feature(Amenity.Gym) to
                listOf("gym", "fitness", "salle de sport", "salle de gym", "نادي رياضي", "جيم"),
            QueryMeaning.Feature(Amenity.Wifi) to
                listOf("wifi", "wi fi", "internet", "fibre", "fiber", "واي فاي", "انترنت", "إنترنت"),
            QueryMeaning.Feature(Amenity.AirConditioning) to
                listOf(
                    "ac",
                    "a c",
                    "air conditioning",
                    "air conditioned",
                    "aircon",
                    "climatisation",
                    "climatise",
                    "clim",
                    "مكيف",
                    "تكييف",
                    "مكيفة",
                ),
            QueryMeaning.Feature(Amenity.Garden) to listOf("garden", "jardin", "حديقة"),
            QueryMeaning.Feature(Amenity.SeaView) to
                listOf(
                    "sea view",
                    "ocean view",
                    "vue mer",
                    "vue sur mer",
                    "vue sur la mer",
                    "إطلالة بحرية",
                    "إطلالة على البحر",
                ),
            QueryMeaning.Feature(Amenity.Lift) to listOf("lift", "elevator", "ascenseur", "مصعد"),
            QueryMeaning.Feature(Amenity.Security) to
                listOf("security", "gated", "gated community", "securite", "gardien", "gardiennage", "حراسة", "أمن"),
            QueryMeaning.Feature(Amenity.HomeOffice) to
                listOf("home office", "workspace", "study room", "bureau a domicile", "مكتب منزلي"),
            QueryMeaning.Feature(Amenity.WashingMachine) to
                listOf("washing machine", "washer", "lave linge", "machine a laver", "غسالة"),
        )

    val types: List<Phrase<PropertyType>> =
        table(
            PropertyType.Apartment to
                listOf(
                    "apartment",
                    "apartments",
                    "apt",
                    "apts",
                    "flat",
                    "flats",
                    "condo",
                    "penthouse",
                    "duplex",
                    "studio",
                    "studios",
                    "appartement",
                    "appartements",
                    "appart",
                    "شقة",
                    "شقق",
                    "استوديو",
                    "ستوديو",
                    "دوبلكس",
                ),
            PropertyType.House to
                listOf(
                    "house",
                    "houses",
                    "villa",
                    "villas",
                    "bungalow",
                    "cottage",
                    "townhouse",
                    "independent house",
                    "maison",
                    "maisons",
                    "pavillon",
                    "فيلا",
                    "فيلة",
                    "فلل",
                    "منزل",
                    "بيت",
                    "دار",
                ),
            PropertyType.Room to
                listOf(
                    "room",
                    "rooms",
                    "shared room",
                    "private room",
                    "colocation",
                    "coloc",
                    "chambre",
                    "paying guest",
                    "pg",
                    "غرفة",
                    "سكن مشترك",
                ),
        )

    val lifestyle: List<Phrase<LifestyleHint>> =
        table(
            LifestyleHint.Quiet to
                listOf(
                    "quiet",
                    "calm",
                    "peaceful",
                    "silent",
                    "calme",
                    "tranquille",
                    "paisible",
                    "هادئ",
                    "هادئة",
                    "هدوء",
                ),
            LifestyleHint.Family to
                listOf(
                    "family",
                    "family friendly",
                    "families",
                    "kids",
                    "children",
                    "familial",
                    "familiale",
                    "famille",
                    "enfants",
                    "عائلي",
                    "عائلية",
                    "عائلة",
                    "أطفال",
                    "عائلات",
                ),
            LifestyleHint.Transport to
                listOf(
                    "near metro",
                    "near the metro",
                    "metro",
                    "subway",
                    "tube",
                    "tram",
                    "train station",
                    "station",
                    "bus",
                    "public transport",
                    "transport",
                    "near transport",
                    "pres du metro",
                    "proche metro",
                    "proche du metro",
                    "rer",
                    "مترو",
                    "قرب المترو",
                    "مواصلات",
                    "محطة",
                    "ترام",
                ),
            LifestyleHint.Nightlife to
                listOf("nightlife", "bars", "nightclubs", "vie nocturne", "sorties", "حياة ليلية"),
            LifestyleHint.Nature to listOf("nature", "green", "park", "parks", "verdure", "parc", "طبيعة", "خضراء"),
            LifestyleHint.Student to
                listOf(
                    "student",
                    "students",
                    "university",
                    "campus",
                    "etudiant",
                    "etudiants",
                    "etudiante",
                    "universite",
                    "طالب",
                    "طلاب",
                    "جامعة",
                ),
            LifestyleHint.RemoteWork to
                listOf("remote work", "work from home", "wfh", "remote worker", "teletravail", "عمل عن بعد"),
        )

    val sorts: List<Phrase<SortOrder>> =
        table(
            SortOrder.PriceLowToHigh to
                listOf(
                    "cheapest",
                    "cheap",
                    "affordable",
                    "budget friendly",
                    "low cost",
                    "inexpensive",
                    "pas cher",
                    "moins cher",
                    "le moins cher",
                    "abordable",
                    "economique",
                    "رخيص",
                    "رخيصة",
                    "أرخص",
                    "اقتصادي",
                ),
            SortOrder.PriceHighToLow to
                listOf(
                    "luxury",
                    "luxurious",
                    "high end",
                    "premium",
                    "upscale",
                    "luxe",
                    "luxueux",
                    "haut de gamme",
                    "prestige",
                    "فاخر",
                    "فاخرة",
                    "فخم",
                ),
            SortOrder.Newest to
                listOf(
                    "newest",
                    "new",
                    "latest",
                    "recent",
                    "just listed",
                    "nouveau",
                    "nouvelle",
                    "nouveaux",
                    "جديد",
                    "جديدة",
                    "أحدث",
                ),
            SortOrder.Rating to
                listOf(
                    "top rated",
                    "best rated",
                    "highly rated",
                    "well rated",
                    "mieux note",
                    "bien note",
                    "الأعلى تقييما",
                    "تقييم عالي",
                ),
        )

    val negations: Set<String> =
        wordSet("no", "not", "without", "non", "sans", "pas", "ni", "except", "غير", "بدون", "بلا", "ممنوع")

    /** Filler words that are neither understood nor worth reporting as "not understood". */
    val stopwords: Set<String> =
        wordSet(
            "a",
            "an",
            "the",
            "with",
            "in",
            "at",
            "on",
            "near",
            "nearby",
            "close",
            "to",
            "for",
            "and",
            "or",
            "of",
            "i",
            "im",
            "me",
            "my",
            "we",
            "want",
            "need",
            "looking",
            "look",
            "find",
            "show",
            "search",
            "searching",
            "please",
            "some",
            "any",
            "rent",
            "rental",
            "rentals",
            "renting",
            "lease",
            "month",
            "monthly",
            "per",
            "pm",
            "mo",
            "place",
            "property",
            "properties",
            "listing",
            "listings",
            "that",
            "which",
            "has",
            "have",
            "having",
            "is",
            "are",
            "something",
            "like",
            "would",
            "prefer",
            "by",
            "it",
            "s",
            "all",
            "one",
            "ideally",
            "good",
            "nice",
            "only",
            "also",
            "around",
            "area",
            "neighbourhood",
            "neighborhood",
            "home",
            "homes",
            "je",
            "j",
            "cherche",
            "recherche",
            "veux",
            "voudrais",
            "un",
            "une",
            "des",
            "le",
            "la",
            "les",
            "l",
            "d",
            "de",
            "du",
            "au",
            "aux",
            "avec",
            "dans",
            "en",
            "pour",
            "et",
            "ou",
            "louer",
            "location",
            "loyer",
            "par",
            "mois",
            "mensuel",
            "pres",
            "proche",
            "quartier",
            "sur",
            "qui",
            "est",
            "bien",
            "logement",
            "cote",
            "vers",
            "في",
            "مع",
            "من",
            "إلى",
            "الى",
            "على",
            "عن",
            "ابحث",
            "أبحث",
            "أريد",
            "اريد",
            "ابغى",
            "نحب",
            "عندي",
            "أو",
            "و",
            "ل",
            "للإيجار",
            "للايجار",
            "إيجار",
            "ايجار",
            "كراء",
            "للكراء",
            "شهر",
            "شهريا",
            "بالشهر",
            "قريب",
            "قرب",
            "منطقة",
            "حي",
            "ذات",
            "فيها",
            "به",
            "بها",
            "يا",
            "تكون",
            "يكون",
            "لو",
            "سمحت",
        )

    private const val SQ_FT_IN_SQM = 0.092903
}
