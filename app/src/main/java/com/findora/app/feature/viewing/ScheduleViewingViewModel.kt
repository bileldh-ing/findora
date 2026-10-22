package com.findora.app.feature.viewing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.BookingError
import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.TimeRange
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.domain.viewing.ViewingDay
import com.findora.app.core.domain.viewing.ViewingSlot
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.navigation.PropertyDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Why a booking attempt failed, as the screen explains it. */
enum class ScheduleError { SlotTaken, Overlaps, InThePast, Failed }

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState

    data object NotFound : ScheduleUiState

    data object Unavailable : ScheduleUiState

    data class Picking(
        val property: Property,
        val days: List<ViewingDay>,
        val selectedDate: LocalDate?,
        val selectedSlot: Instant?,
        val note: String,
        val isGuest: Boolean,
        val submitting: Boolean,
        val error: ScheduleError?,
        val zone: ZoneId,
    ) : ScheduleUiState {
        val slots: List<ViewingSlot> get() = days.firstOrNull { it.date == selectedDate }?.slots.orEmpty()
        val canConfirm: Boolean get() = selectedSlot != null && !submitting
    }

    data class Booked(
        val viewing: Viewing,
        val property: Property,
        val zone: ZoneId,
        /** False when the viewing starts within the reminder lead time, so no reminder was scheduled. */
        val reminderScheduled: Boolean,
    ) : ScheduleUiState
}

sealed interface ScheduleUiEvent {
    data class SelectDate(
        val date: LocalDate,
    ) : ScheduleUiEvent

    data class SelectSlot(
        val start: Instant,
    ) : ScheduleUiEvent

    data class NoteChanged(
        val note: String,
    ) : ScheduleUiEvent

    data object Confirm : ScheduleUiEvent

    data object Retry : ScheduleUiEvent

    data object AddToCalendar : ScheduleUiEvent
}

sealed interface ScheduleUiEffect {
    data class AddToCalendar(
        val propertyTitle: String,
        val location: String,
        val begin: Instant,
        val end: Instant,
        val note: String,
        val link: String,
    ) : ScheduleUiEffect
}

/**
 * Books a viewing of one listing: a 14-day date strip and a slot grid computed by [ViewingSlotGenerator] from
 * the listing's bookings and the user's own viewings (both observed, so a slot taken elsewhere disappears live).
 * Confirming saves to Room, schedules the reminder and records a "schedule_viewing" interaction. Guests book
 * under the guest id, like their favourites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewingViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val properties: PropertyRepository,
        private val viewings: ViewingRepository,
        private val reminders: ViewingReminderScheduler,
        private val interactions: InteractionRepository,
        private val auth: AuthRepository,
        private val generator: ViewingSlotGenerator,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        val propertyId: String = requireNotNull(savedStateHandle.get<String>(PROPERTY_ID_KEY)) { "propertyId missing" }

        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val isGuest: Flow<Boolean> = auth.session.map { it == null || it.isGuest }.distinctUntilChanged()
        private val form = MutableStateFlow(Form())
        private val attempt = MutableStateFlow(0)
        private val effectChannel = Channel<ScheduleUiEffect>(Channel.BUFFERED)

        val effects: Flow<ScheduleUiEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<ScheduleUiState> =
            attempt
                .flatMapLatest {
                    content()
                        .onStart { emit(ScheduleUiState.Loading) }
                        .catch { error ->
                            if (error is CancellationException) throw error
                            emit(ScheduleUiState.Unavailable)
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ScheduleUiState.Loading)

        fun onEvent(event: ScheduleUiEvent) {
            when (event) {
                is ScheduleUiEvent.SelectDate -> form.update { it.copy(date = event.date, slot = null, error = null) }
                is ScheduleUiEvent.SelectSlot -> form.update { it.copy(slot = event.start, error = null) }
                is ScheduleUiEvent.NoteChanged -> form.update { it.copy(note = event.note.take(MAX_NOTE_LENGTH)) }
                ScheduleUiEvent.Confirm -> confirm()
                ScheduleUiEvent.Retry -> attempt.value += 1
                ScheduleUiEvent.AddToCalendar -> addToCalendar()
            }
        }

        private fun content(): Flow<ScheduleUiState> =
            properties.observeProperty(propertyId).flatMapLatest { property ->
                if (property == null) {
                    flowOf(ScheduleUiState.NotFound)
                } else {
                    combine(
                        userId.flatMapLatest { viewings.observeViewings(it) },
                        viewings.observePropertyBookings(propertyId, clock.now()),
                        isGuest,
                        form,
                    ) { mine, atProperty, guest, current ->
                        toState(property, mine.map { it.range }, atProperty, guest, current)
                    }
                }
            }

        private fun toState(
            property: Property,
            mine: List<TimeRange>,
            atProperty: List<TimeRange>,
            guest: Boolean,
            form: Form,
        ): ScheduleUiState {
            form.booked?.let { return ScheduleUiState.Booked(it, property, zone, form.reminderScheduled) }
            val days = generator.days(propertyBookings = atProperty, userBookings = mine)
            val date =
                form.date?.takeIf { wanted -> days.any { it.date == wanted } }
                    ?: days.firstOrNull { it.availableCount > 0 }?.date
                    ?: days.firstOrNull()?.date
            // A selection that was booked meanwhile (or has started) silently drops out.
            val slot =
                form.slot?.takeIf { wanted ->
                    days.firstOrNull { it.date == date }?.slots.orEmpty().any { it.start == wanted && it.isAvailable }
                }
            return ScheduleUiState.Picking(
                property = property,
                days = days,
                selectedDate = date,
                selectedSlot = slot,
                note = form.note,
                isGuest = guest,
                submitting = form.submitting,
                error = form.error,
                zone = zone,
            )
        }

        @Suppress("TooGenericExceptionCaught")
        private fun confirm() {
            val picking = state.value as? ScheduleUiState.Picking
            val start = picking?.selectedSlot
            if (picking == null || start == null || picking.submitting) return
            form.update { it.copy(submitting = true, error = null) }
            viewModelScope.launch {
                val user = userId.first()
                val result =
                    try {
                        viewings.schedule(user, propertyId, start, generator.config.durationMinutes, picking.note)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w(TAG, "Booking failed", error)
                        null
                    }
                when (result) {
                    is BookingResult.Booked -> onBooked(user, result.viewing)
                    is BookingResult.Rejected ->
                        form.update { it.copy(submitting = false, slot = null, error = result.error.toUi()) }
                    null -> form.update { it.copy(submitting = false, error = ScheduleError.Failed) }
                }
            }
        }

        private suspend fun onBooked(
            user: String,
            viewing: Viewing,
        ) {
            val reminder = bestEffort("schedule reminder") { reminders.schedule(viewing) } ?: false
            bestEffort("record interaction") {
                interactions.record(InteractionEvent(user, propertyId, InteractionType.ScheduledViewing, clock.now()))
            }
            form.update { it.copy(submitting = false, booked = viewing, reminderScheduled = reminder) }
        }

        private fun addToCalendar() {
            val booked = state.value as? ScheduleUiState.Booked ?: return
            viewModelScope.launch {
                effectChannel.send(
                    ScheduleUiEffect.AddToCalendar(
                        propertyTitle = booked.property.title,
                        location = booked.property.locality,
                        begin = booked.viewing.startAt,
                        end = booked.viewing.endAt,
                        note = booked.viewing.note,
                        link = PropertyDeepLink.uriFor(booked.property.id),
                    ),
                )
            }
        }

        /** Side effects after a booking never undo it: a failed reminder or signal is logged and skipped. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> bestEffort(
            what: String,
            block: suspend () -> T,
        ): T? =
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Could not $what", error)
                null
            }

        private data class Form(
            val date: LocalDate? = null,
            val slot: Instant? = null,
            val note: String = "",
            val submitting: Boolean = false,
            val error: ScheduleError? = null,
            val booked: Viewing? = null,
            val reminderScheduled: Boolean = false,
        )

        companion object {
            /** The `FindoraRoute.ScheduleViewing` field the id is stored under. */
            const val PROPERTY_ID_KEY = "propertyId"
            const val MAX_NOTE_LENGTH = 280
            private const val STOP_TIMEOUT_MILLIS = 5_000L
            private const val TAG = "ScheduleViewing"
        }
    }

private fun BookingError.toUi(): ScheduleError =
    when (this) {
        BookingError.SlotTaken -> ScheduleError.SlotTaken
        BookingError.Overlaps -> ScheduleError.Overlaps
        BookingError.InThePast -> ScheduleError.InThePast
    }
