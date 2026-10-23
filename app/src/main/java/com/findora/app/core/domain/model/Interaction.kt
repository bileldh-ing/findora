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

    /** Swiped right (or "Save") in the Dream Match deck: saved and added to the Matches collection. */
    DeckSaved("save_deck"),

    /** Swiped up (or "Super like") in the Dream Match deck: saved, then the viewing booking opens. */
    DeckSuperLiked("super_like_deck"),

    /** Swiped left (or "Skip") in the Dream Match deck: a mild negative signal. */
    DeckSkipped("skip_deck"),
    ;

    companion object {
        /** The deck actions that put a home in the user's Matches collection. */
        val DeckMatches: Set<InteractionType> = setOf(DeckSaved, DeckSuperLiked)

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
