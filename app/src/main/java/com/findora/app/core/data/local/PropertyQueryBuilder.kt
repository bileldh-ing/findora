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
        val clauses = mutableListOf("region_id = ?")
        val args = mutableListOf<Any>(regionId)
        fun add(
            clause: String,
            vararg values: Any,
        ) {
            clauses += clause
            args += values
        }
        filter.type?.let { add("type = ?", it.id) }
        filter.query.trim().takeIf(String::isNotEmpty)?.let { text ->
            val pattern = "%${escapeLike(text)}%"
            add("(title LIKE ? ESCAPE '$ESCAPE' OR locality LIKE ? ESCAPE '$ESCAPE')", pattern, pattern)
        }
        filter.minRent?.let { add("rent_amount >= ?", it) }
        filter.maxRent?.let { add("rent_amount <= ?", it) }
        filter.minBedrooms?.let { add("bedrooms >= ?", it) }
        filter.minBathrooms?.let { add("bathrooms >= ?", it) }
        filter.minAreaSqm?.let { add("area_sqm >= ?", it) }
        filter.maxAreaSqm?.let { add("area_sqm <= ?", it) }
        if (filter.furnishedOnly) clauses += "furnished = 1"
        if (filter.petFriendlyOnly) clauses += "pet_friendly = 1"
        if (filter.parkingOnly) clauses += "parking = 1"
        if (filter.verifiedOnly) clauses += "verified = 1"
        filter.amenities.sortedBy { it.id }.forEach { add("amenities LIKE ?", "%\"${it.id}\"%") }
        val sql = "SELECT * FROM properties WHERE ${clauses.joinToString(" AND ")} ORDER BY ${orderBy(filter.sort)}"
        return SqlQuery(sql, args)
    }

    fun orderBy(sort: SortOrder): String =
        when (sort) {
            SortOrder.BestMatch -> "verified DESC, rating DESC, reviews_count DESC, id ASC"
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
