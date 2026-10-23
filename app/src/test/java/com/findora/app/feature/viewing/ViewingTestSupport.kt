package com.findora.app.feature.viewing

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeNotificationRepository
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
    val notifications = FakeNotificationRepository { now }
    val properties: PropertyRepository = FakePropertyRepository()
    val settings = FakeSettingsRepository()
    val georgie: Property = seedProperties.first { it.title == "Georgie House" }

    fun scheduleViewModel(id: String = georgie.id) =
        ScheduleViewingViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ScheduleViewingViewModel.PROPERTY_ID_KEY to id)),
            properties = properties,
            viewings = viewings,
            reminders = reminders,
            interactions = interactions,
            notifications = notifications,
            auth = auth,
            generator = ViewingSlotGenerator(clock, TUNIS),
            clock = clock,
            zone = TUNIS,
            settings = settings,
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

    override suspend fun remove(event: InteractionEvent) =
        events.update { list ->
            val index = list.indexOfLast { it == event }
            if (index < 0) list else list.toMutableList().apply { removeAt(index) }
        }

    override fun observePropertyIds(
        userId: String,
        types: Set<InteractionType>,
    ): Flow<List<String>> =
        events.map { list ->
            list
                .filter { it.userId == userId && it.type in types }
                .asReversed()
                .map { it.propertyId }
                .distinct()
        }

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
