package com.findora.app.core.domain.compare

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Property

/** A comparable row of the compare table and which direction wins. */
enum class CompareMetric(
    val higherIsBetter: Boolean,
) {
    Price(higherIsBetter = false),
    Area(higherIsBetter = true),
    PricePerSqm(higherIsBetter = false),
    Bedrooms(higherIsBetter = true),
    Bathrooms(higherIsBetter = true),
    Amenities(higherIsBetter = true),
    Rating(higherIsBetter = true),
    Match(higherIsBetter = true),
}

/** One row: a value per column (null when unknown) and the columns holding the best value. */
data class CompareRow(
    val metric: CompareMetric,
    val values: List<Double?>,
    val best: Set<Int>,
)

/** The side-by-side comparison of [properties] (in column order). */
data class CompareTable(
    val properties: List<Property>,
    val rows: List<CompareRow>,
    /** Every amenity offered by at least one column, in catalogue order, for the checklist. */
    val amenities: List<Amenity>,
) {
    fun row(metric: CompareMetric): CompareRow? = rows.firstOrNull { it.metric == metric }

    /** True when the column at [index] offers [amenity]. */
    fun hasAmenity(
        index: Int,
        amenity: Amenity,
    ): Boolean = amenity in properties[index].amenities

    companion object {
        /**
         * Builds the table. [matchScores] (listing id to 0-100) adds the match row when the user has a profile.
         * A row highlights a best value only when at least two columns have a value and they are not all equal;
         * ties share the highlight. Prices in different currencies are never ranked against each other.
         */
        fun build(
            properties: List<Property>,
            matchScores: Map<String, Int>? = null,
        ): CompareTable {
            val sameCurrency = properties.map { it.rent.currency }.distinct().size <= 1
            val rows =
                buildList {
                    add(row(CompareMetric.Price, properties.map { it.rent.amount.toDouble() }, rankable = sameCurrency))
                    add(row(CompareMetric.Area, properties.map { it.areaSqm.toDouble() }))
                    add(row(CompareMetric.PricePerSqm, properties.map(::pricePerSqm), rankable = sameCurrency))
                    add(row(CompareMetric.Bedrooms, properties.map { it.bedrooms.toDouble() }))
                    add(row(CompareMetric.Bathrooms, properties.map { it.bathrooms.toDouble() }))
                    add(row(CompareMetric.Amenities, properties.map { it.amenities.distinct().size.toDouble() }))
                    add(row(CompareMetric.Rating, properties.map { it.rating }))
                    if (matchScores != null) {
                        add(row(CompareMetric.Match, properties.map { matchScores[it.id]?.toDouble() }))
                    }
                }
            val offered = properties.flatMapTo(HashSet()) { it.amenities }
            return CompareTable(properties, rows, Amenity.entries.filter { it in offered })
        }

        /** Monthly rent per square metre; null when the area is unknown (0). */
        fun pricePerSqm(property: Property): Double? =
            if (property.areaSqm > 0) property.rent.amount.toDouble() / property.areaSqm else null

        private fun row(
            metric: CompareMetric,
            values: List<Double?>,
            rankable: Boolean = true,
        ): CompareRow = CompareRow(metric, values, if (rankable) bestIndices(values, metric.higherIsBetter) else emptySet())

        internal fun bestIndices(
            values: List<Double?>,
            higherIsBetter: Boolean,
        ): Set<Int> {
            val known = values.withIndex().mapNotNull { (index, value) -> value?.let { index to it } }
            if (known.size < 2) return emptySet()
            val numbers = known.map { it.second }
            val target = if (higherIsBetter) numbers.max() else numbers.min()
            if (numbers.all { it == target }) return emptySet()
            return known.filter { it.second == target }.mapTo(LinkedHashSet()) { it.first }
        }
    }
}
