package com.findora.app.core.domain.model

import java.time.Instant

/** Signals the best-match engine learns from (Phase 5). */
enum class InteractionType(
    val id: String,
) {
    Viewed("viewed"),
    Favorited("favorited"),
    Unfavorited("unfavorited"),
    Shared("shared"),
    ContactedAgent("contacted_agent"),
    Dismissed("dismissed"),
    ScheduledViewing("schedule_viewing"),
    ;

    companion object {
        fun fromId(id: String): InteractionType =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown interaction: $id")
    }
}

data class InteractionEvent(
    val userId: String,
    val propertyId: String,
    val type: InteractionType,
    val occurredAt: Instant,
    val dwellMillis: Long = 0,
)

data class RecentSearch(
    val query: String,
    val searchedAt: Instant,
)
