package com.findora.app.core.domain.match

import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Everything needed to score a listing for the current user, captured at one instant ([now]). */
class MatchContext(
    val profile: PreferenceProfile,
    val affinity: BehaviorAffinity,
    val now: Instant,
) {
    fun score(property: Property): MatchResult = MatchScoreCalculator.score(property, profile, affinity, now)
}

/**
 * The current user's [MatchContext]: their profile plus a [BehaviorAffinity] learned from their recent
 * interactions with listings of the current region. Emits null while the user has no profile, which is
 * how every screen knows to hide match badges and offer the quiz instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatchContext
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val profiles: MatchProfileRepository,
        private val interactions: InteractionRepository,
        private val properties: PropertyRepository,
        private val currentRegion: CurrentRegion,
        private val clock: AppClock,
    ) {
        operator fun invoke(): Flow<MatchContext?> =
            auth.session
                .map { it?.userId ?: AuthRepository.GUEST_USER_ID }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    profiles.observe(userId).flatMapLatest { profile ->
                        if (profile == null) flowOf(null) else contextFor(userId, profile)
                    }
                }

        private fun contextFor(
            userId: String,
            profile: PreferenceProfile,
        ): Flow<MatchContext> =
            combine(
                interactions.observeRecent(userId, EVENT_LIMIT),
                currentRegion.region.flatMapLatest { properties.observeProperties(it.id) },
            ) { events, listings ->
                val byId = listings.associateBy(Property::id)
                val now = clock.now()
                MatchContext(profile, BehaviorAffinity.from(events, byId::get, now), now)
            }

        companion object {
            /** Older events have decayed to almost nothing; capping the window keeps scoring cheap. */
            const val EVENT_LIMIT = 300
        }
    }
