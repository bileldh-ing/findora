package com.findora.app.core.domain.model

/** Structured listing filter; null fields do not constrain the result. */
data class PropertyFilter(
    val type: PropertyType? = null,
    val minRent: Long? = null,
    val maxRent: Long? = null,
    val minBedrooms: Int? = null,
    val verifiedOnly: Boolean = false,
) {
    init {
        if (minRent != null && maxRent != null) require(minRent <= maxRent) { "minRent must not exceed maxRent" }
    }
}
