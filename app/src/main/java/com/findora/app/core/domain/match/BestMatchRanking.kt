package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter

/** The "Best match" sort: listings that pass [filter], scored, must-have failures removed, best first. */
object BestMatchRanking {
    fun rank(
        listings: List<Property>,
        filter: PropertyFilter,
        context: MatchContext,
    ): List<Property> =
        listings
            .asSequence()
            .filter(filter::accepts)
            .map { it to context.score(it) }
            .filterNot { (_, result) -> result.excluded }
            // Ties are broken by id so the order is stable across refreshes.
            .sortedWith(compareByDescending<Pair<Property, MatchResult>> { it.second.score }.thenBy { it.first.id })
            .map { it.first }
            .toList()

    /** Card badge values: listing id to score, for every listing that keeps its must-haves. */
    fun scores(
        listings: List<Property>,
        context: MatchContext,
    ): Map<String, Int> =
        listings
            .asSequence()
            .map { it.id to context.score(it) }
            .filterNot { (_, result) -> result.excluded }
            .associate { (id, result) -> id to result.score }
}
