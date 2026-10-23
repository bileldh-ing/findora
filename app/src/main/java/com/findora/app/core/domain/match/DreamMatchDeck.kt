package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter

/** One card of the Dream Match deck; [matchPercent] is null when the user has no preference profile. */
data class DeckEntry(
    val property: Property,
    val matchPercent: Int?,
)

/**
 * Orders the Dream Match deck. With a profile the cards follow the Best match ranking (must-have failures
 * left out); without one they follow Home's "Recommended" order (verified, rating, reviews, id). Listings in
 * [excludedIds] (already saved, or skipped earlier) are left out.
 */
object DreamMatchDeck {
    private val recommended: Comparator<Property> =
        compareByDescending<Property> { it.verified }
            .thenByDescending { it.rating }
            .thenByDescending { it.reviewsCount }
            .thenBy { it.id }

    fun order(
        listings: List<Property>,
        excludedIds: Set<String>,
        context: MatchContext?,
    ): List<DeckEntry> {
        val candidates = listings.filterNot { it.id in excludedIds }
        return if (context == null) {
            candidates.sortedWith(recommended).map { DeckEntry(it, matchPercent = null) }
        } else {
            BestMatchRanking
                .rank(candidates, PropertyFilter(), context)
                .map { DeckEntry(it, context.score(it).score) }
        }
    }
}
