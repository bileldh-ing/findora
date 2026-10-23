package com.findora.app.core.testing

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory profiles keyed by user id. */
class FakeMatchProfileRepository(
    initial: Map<String, PreferenceProfile> = emptyMap(),
) : MatchProfileRepository {
    val profiles = MutableStateFlow(initial)
    val cleared = mutableListOf<String>()

    override fun observe(userId: String): Flow<PreferenceProfile?> = profiles.map { it[userId] }

    override suspend fun save(
        userId: String,
        profile: PreferenceProfile,
    ) = profiles.update { it + (userId to profile) }

    override suspend fun clear(userId: String) {
        cleared += userId
        profiles.update { it - userId }
    }
}

/** Records interactions and serves them back, newest last. */
class FakeInteractionRepository : InteractionRepository {
    val events = MutableStateFlow<List<InteractionEvent>>(emptyList())

    override suspend fun record(event: InteractionEvent) = events.update { it + event }

    override fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEvent>> = events.map { list -> list.filter { it.userId == userId }.takeLast(limit) }
}

/** The real [ObserveMatchContext] over fakes and the bundled region packs. */
fun observeMatchContext(
    auth: AuthRepository = FakeAuthRepository(),
    profiles: MatchProfileRepository = FakeMatchProfileRepository(),
    interactions: InteractionRepository = FakeInteractionRepository(),
    properties: PropertyRepository = FakePropertyRepository(),
    settings: SettingsRepository = FakeSettingsRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    clock: AppClock = AppClock { Instant.parse("2026-10-01T10:00:00Z") },
): ObserveMatchContext =
    ObserveMatchContext(
        auth = auth,
        profiles = profiles,
        interactions = interactions,
        properties = properties,
        currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), dispatcher)),
        clock = clock,
    )
