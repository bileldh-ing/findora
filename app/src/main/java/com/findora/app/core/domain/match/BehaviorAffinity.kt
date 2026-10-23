package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

/**
 * What the user's own behaviour says about a listing. Every interaction adds its weight to the features of
 * the home it was about (the home itself, its type, neighbourhood and bedroom count), multiplied by an
 * exponential decay `0.5^(age / halfLife)`, so a favourite from today counts twice as much as one from
 * [HALF_LIFE] ago. A candidate's signal is the sum of the weights of the features it shares.
 */
class BehaviorAffinity private constructor(
    private val weights: Map<String, Double>,
) {
    /** False when there is nothing to learn from yet (no interactions, or none about known listings). */
    val hasSignal: Boolean get() = weights.isNotEmpty()

    /** Positive when the listing resembles homes the user engaged with, negative for ones they turned down. */
    fun signalFor(property: Property): Double = features(property).sumOf { weights[it] ?: 0.0 }

    companion object {
        /** Two weeks: behaviour from a fortnight ago counts half as much as today's. */
        val HALF_LIFE: Duration = Duration.ofDays(14)

        private const val DWELL_FOR_FULL_BONUS_MILLIS = 30_000.0
        private const val HALF = 0.5
        private const val VIEW = 1.0
        private const val FAVORITE = 3.0
        private const val SHARE = 2.0
        private const val CONTACT = 4.0
        private const val SCHEDULE = 5.0
        private const val DISMISS = -2.0

        /** A quick left swipe says less than an explicit dismissal, so it weighs a little less. */
        private const val DECK_SKIP = -1.5

        val None = BehaviorAffinity(emptyMap())

        /** Builds the affinity from [events]; events about listings [propertyById] cannot resolve are ignored. */
        fun from(
            events: List<InteractionEvent>,
            propertyById: (String) -> Property?,
            now: Instant,
            halfLife: Duration = HALF_LIFE,
        ): BehaviorAffinity {
            require(!halfLife.isNegative && !halfLife.isZero) { "halfLife must be positive" }
            val weights = HashMap<String, Double>()
            events.forEach { event ->
                val property = propertyById(event.propertyId) ?: return@forEach
                val weight = baseWeight(event) * decay(Duration.between(event.occurredAt, now), halfLife)
                features(property).forEach { feature -> weights.merge(feature, weight, Double::plus) }
            }
            return BehaviorAffinity(weights)
        }

        /** `0.5^(age / halfLife)`; events from the future (clock skew) count as brand new. */
        fun decay(
            age: Duration,
            halfLife: Duration = HALF_LIFE,
        ): Double {
            val millis = age.toMillis().coerceAtLeast(0).toDouble()
            return HALF.pow(millis / halfLife.toMillis())
        }

        private fun baseWeight(event: InteractionEvent): Double =
            when (event.type) {
                // A long look (dwell) is worth up to one extra view.
                InteractionType.Viewed -> VIEW + (event.dwellMillis / DWELL_FOR_FULL_BONUS_MILLIS).coerceAtMost(VIEW)
                InteractionType.Favorited -> FAVORITE
                InteractionType.Unfavorited -> -FAVORITE
                InteractionType.Shared -> SHARE
                InteractionType.ContactedAgent -> CONTACT
                InteractionType.ScheduledViewing -> SCHEDULE
                InteractionType.Dismissed -> DISMISS
                InteractionType.DeckSaved -> FAVORITE
                InteractionType.DeckSuperLiked -> CONTACT
                InteractionType.DeckSkipped -> DECK_SKIP
            }

        private fun features(property: Property): List<String> =
            listOf(
                "id:${property.id}",
                "type:${property.type.id}",
                "locality:${property.locality.lowercase()}",
                "beds:${property.bedrooms}",
            )
    }
}
