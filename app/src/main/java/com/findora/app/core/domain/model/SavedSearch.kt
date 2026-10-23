package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * How often a saved search alerts. [id] is the stable value stored in the database.
 * Daily checks at most once a day; Instant checks every time the listings are refreshed.
 */
enum class AlertFrequency(
    val id: String,
) {
    Daily("daily"),
    Instant("instant"),
    ;

    companion object {
        fun fromId(id: String): AlertFrequency = entries.firstOrNull { it.id == id } ?: Daily
    }
}

/**
 * A search the user named and kept: the Home [filter] in one [regionId], with an optional new-match alert.
 * [query] is the sentence typed in Ask Findora (or the search text), shown under the name.
 */
@Immutable
data class SavedSearch(
    val id: Long,
    val userId: String,
    val name: String,
    val query: String,
    val regionId: String,
    val filter: PropertyFilter,
    val alertsEnabled: Boolean,
    val frequency: AlertFrequency,
    val createdAt: Instant,
    val lastCheckedAt: Instant?,
) {
    companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
