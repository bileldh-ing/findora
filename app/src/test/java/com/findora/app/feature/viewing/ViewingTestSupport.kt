package com.findora.app.feature.viewing

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.RecordingReminderScheduler
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared wiring for the viewing ViewModel and Compose tests: Monday 26 October 2026, 10:10 in Tunis. */
class ViewingTestEnv(
    var now: Instant = LocalDateTime.of(2026, 10, 26, 10, 10).atZone(TUNIS).toInstant(),
) {
    val clock: () -> Instant = { now }
    val auth = FakeAuthRepository()
    val viewings = FakeViewingRepository { now }
    val reminders = RecordingReminderScheduler()
    val interactions = RecordingInteractions()
    val properties: PropertyRepository = FakePropertyRepository()
    val georgie: Property = seedProperties.first { it.title == "Georgie House" }

    fun scheduleViewModel(id: String = georgie.id) =
        ScheduleViewingViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ScheduleViewingViewModel.PROPERTY_ID_KEY to id)),
            properties = properties,
            viewings = viewings,
            reminders = reminders,
            interactions = interactions,
            auth = auth,
            generator = ViewingSlotGenerator(clock, TUNIS),
            clock = clock,
            zone = TUNIS,
        )

    fun viewingsViewModel() =
        ViewingsViewModel(
            viewings = viewings,
            properties = properties,
            reminders = reminders,
            auth = auth,
            clock = clock,
            zone = TUNIS,
        )

    companion object {
        val TUNIS: ZoneId = ZoneId.of("Africa/Tunis")
    }
}

class RecordingInteractions : InteractionRepository {
    val events = MutableStateFlow<List<InteractionEvent>>(emptyList())

    override suspend fun record(event: InteractionEvent) = events.update { it + event }

    override fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEvent>> = events.map { it.takeLast(limit) }
}

/** Skips intermediate emissions until one matches. */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
