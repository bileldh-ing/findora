package com.findora.app.core.domain.search

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SortOrder

/**
 * Lifestyle words in a search ("quiet", "family", "near the metro"). Listings carry no field for them, so they
 * never filter anything: they ask for the Best match order, which weighs the user's own lifestyle answers.
 */
enum class LifestyleHint { Quiet, Family, Transport, Nightlife, Nature, Student, RemoteWork }

/** One thing the parser understood in a search sentence; each becomes a removable chip. */
sealed interface QueryMeaning {
    /** Monthly rent bounds in the listing currency; [currency] is set when the sentence named one. */
    data class Budget(
        val min: Long?,
        val max: Long?,
        val currency: Currency? = null,
    ) : QueryMeaning

    data class Bedrooms(
        val min: Int,
    ) : QueryMeaning

    data class Bathrooms(
        val min: Int,
    ) : QueryMeaning

    data class Area(
        val minSqm: Int?,
        val maxSqm: Int?,
    ) : QueryMeaning

    data class Type(
        val type: PropertyType,
    ) : QueryMeaning

    /** A neighbourhood from the region packs, by its canonical name. */
    data class Place(
        val name: String,
        val regionId: String,
    ) : QueryMeaning

    /** A whole region ("Paris", "تونس"). */
    data class Region(
        val regionId: String,
        val name: String,
    ) : QueryMeaning

    data object Furnished : QueryMeaning

    data object PetFriendly : QueryMeaning

    data object Parking : QueryMeaning

    data object Verified : QueryMeaning

    data class Feature(
        val amenity: Amenity,
    ) : QueryMeaning

    data class Lifestyle(
        val hint: LifestyleHint,
    ) : QueryMeaning

    data class Sort(
        val order: SortOrder,
    ) : QueryMeaning
}

/** A span of the original sentence ([start] inclusive, [end] exclusive) and what it was read as. */
data class RecognizedToken(
    val start: Int,
    val end: Int,
    val text: String,
    val meaning: QueryMeaning,
)

/**
 * The result of parsing a search sentence: what was understood (with spans), the words that were not, and the
 * [PropertyFilter] it amounts to. [currencyRegions] maps a currency to the one region priced in it, so a budget
 * in dinars points at Tunis when no place was named.
 */
data class ParsedIntent(
    val query: String,
    val tokens: List<RecognizedToken>,
    val unrecognized: List<String>,
    val currencyRegions: Map<Currency, String> = emptyMap(),
) {
    /** Distinct meanings in the order they appear in the sentence. */
    val meanings: List<QueryMeaning> get() = tokens.sortedBy { it.start }.map { it.meaning }.distinct()

    val isEmpty: Boolean get() = tokens.isEmpty()

    val budget: QueryMeaning.Budget? get() = first()

    val place: QueryMeaning.Place? get() = first()

    val region: QueryMeaning.Region? get() = first()

    val lifestyle: Set<LifestyleHint> get() = meanings.filterIsInstance<QueryMeaning.Lifestyle>().map { it.hint }.toSet()

    /** The order the sentence asked for ("cheapest", "newest"), if any. */
    val sort: SortOrder? get() = first<QueryMeaning.Sort>()?.order

    /** The region the sentence points at: a named place, a named region, or the currency of the budget. */
    val regionId: String?
        get() = place?.regionId ?: region?.regionId ?: budget?.currency?.let(currencyRegions::get)

    val filter: PropertyFilter
        get() {
            val all = meanings
            val area = first<QueryMeaning.Area>()
            return PropertyFilter(
                type = first<QueryMeaning.Type>()?.type,
                minRent = budget?.min,
                maxRent = budget?.max,
                minBedrooms = first<QueryMeaning.Bedrooms>()?.min,
                verifiedOnly = QueryMeaning.Verified in all,
                query = place?.name.orEmpty(),
                minBathrooms = first<QueryMeaning.Bathrooms>()?.min,
                minAreaSqm = area?.minSqm,
                maxAreaSqm = area?.maxSqm,
                furnishedOnly = QueryMeaning.Furnished in all,
                petFriendlyOnly = QueryMeaning.PetFriendly in all,
                parkingOnly = QueryMeaning.Parking in all,
                amenities = all.filterIsInstance<QueryMeaning.Feature>().map { it.amenity }.toSet(),
                sort = sort ?: SortOrder.Recommended,
            )
        }

    /** The same intent with one chip removed (every span that meant it goes with it). */
    fun without(meaning: QueryMeaning): ParsedIntent = copy(tokens = tokens.filterNot { it.meaning == meaning })

    private inline fun <reified T : QueryMeaning> first(): T? = meanings.firstOrNull { it is T } as T?
}
