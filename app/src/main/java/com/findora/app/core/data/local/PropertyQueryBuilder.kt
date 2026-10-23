package com.findora.app.core.data.local

import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.SortOrder

/** A parameterised SQL statement; arguments are always bound, never concatenated. */
data class SqlQuery(
    val sql: String,
    val args: List<Any>,
)

/**
 * Builds the Home feed query from a [PropertyFilter]. Pure Kotlin so every clause is unit-tested.
 * Text search matches title or locality (LIKE is case-insensitive for ASCII in SQLite); user input is escaped
 * so "%" and "_" are literal. Amenities live in a JSON array column, matched as quoted ids.
 */
object PropertyQueryBuilder {
    private const val ESCAPE = '\\'

    fun build(
        regionId: String,
        filter: PropertyFilter,
    ): SqlQuery {
        val where = Where()
        where.add("region_id = ?", regionId)
        filter.type?.let { where.add("type = ?", it.id) }
        filter.query.trim().takeIf(String::isNotEmpty)?.let { text ->
            val pattern = "%${escapeLike(text)}%"
            where.add("(title LIKE ? ESCAPE '$ESCAPE' OR locality LIKE ? ESCAPE '$ESCAPE')", pattern, pattern)
        }
        addRanges(where, filter)
        addFlags(where, filter)
        filter.amenities.sortedBy { it.id }.forEach { where.add("amenities LIKE ?", "%\"${it.id}\"%") }
        val sql = "SELECT * FROM properties WHERE ${where.clauses.joinToString(
            " AND ",
        )} ORDER BY ${orderBy(filter.sort)}"
        return SqlQuery(sql, where.args)
    }

    private fun addRanges(
        where: Where,
        filter: PropertyFilter,
    ) {
        filter.minRent?.let { where.add("rent_amount >= ?", it) }
        filter.maxRent?.let { where.add("rent_amount <= ?", it) }
        filter.minBedrooms?.let { where.add("bedrooms >= ?", it) }
        filter.minBathrooms?.let { where.add("bathrooms >= ?", it) }
        filter.minAreaSqm?.let { where.add("area_sqm >= ?", it) }
        filter.maxAreaSqm?.let { where.add("area_sqm <= ?", it) }
    }

    private fun addFlags(
        where: Where,
        filter: PropertyFilter,
    ) {
        if (filter.furnishedOnly) where.add("furnished = 1")
        if (filter.petFriendlyOnly) where.add("pet_friendly = 1")
        if (filter.parkingOnly) where.add("parking = 1")
        if (filter.verifiedOnly) where.add("verified = 1")
    }

    /** Collects AND-ed clauses with their bound arguments in order. */
    private class Where {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        fun add(
            clause: String,
            vararg values: Any,
        ) {
            clauses += clause
            args.addAll(values)
        }
    }

    fun orderBy(sort: SortOrder): String =
        when (sort) {
            // Best match is ranked in Kotlin by the match engine; without a profile it reads like Recommended.
            SortOrder.Recommended, SortOrder.BestMatch -> "verified DESC, rating DESC, reviews_count DESC, id ASC"
            SortOrder.PriceLowToHigh -> "rent_amount ASC, id ASC"
            SortOrder.PriceHighToLow -> "rent_amount DESC, id ASC"
            SortOrder.Newest -> "created_at DESC, id ASC"
            SortOrder.Rating -> "rating DESC, reviews_count DESC, id ASC"
        }

    /** Escapes LIKE wildcards so user text is matched literally. */
    fun escapeLike(text: String): String =
        buildString {
            text.forEach { char ->
                if (char == ESCAPE || char == '%' || char == '_') append(ESCAPE)
                append(char)
            }
        }
}
