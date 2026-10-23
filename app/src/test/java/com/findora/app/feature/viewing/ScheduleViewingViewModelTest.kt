package com.findora.app.feature.viewing

import app.cash.turbine.test
import com.findora.app.core.domain.model.BookingError
import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.navigation.PropertyDeepLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class ScheduleViewingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = ViewingTestEnv()
    private val monday = LocalDate.of(2026, 10, 26)

    private fun ScheduleUiState.Picking.firstFree() = slots.first { it.isAvailable }.start

    @Test
    fun opensOnTodayWithFreeSlotsAndNothingSelected() =
        runTest(main.dispatcher) {
            env.scheduleViewModel().state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                assertEquals(env.georgie, picking.property)
                assertEquals(monday, picking.selectedDate)
                assertEquals(14, picking.days.size)
                assertEquals(
                    LocalTime.of(10, 30),
                    picking.slots
                        .first()
                        .start
                        .atZone(ViewingTestEnv.TUNIS)
                        .toLocalTime(),
                )
                assertNull(picking.selectedSlot)
                assertFalse(picking.canConfirm)
                assertTrue(picking.isGuest)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun confirmingBooksSchedulesTheReminderAndRecordsTheInteraction() =
        runTest(main.dispatcher) {
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                val slot = picking.firstFree()
                vm.onEvent(ScheduleUiEvent.SelectSlot(slot))
                vm.onEvent(ScheduleUiEvent.NoteChanged("Can I bring my dog?"))
                assertTrue(
                    (awaitUntil { (it as? ScheduleUiState.Picking)?.canConfirm == true }) is ScheduleUiState.Picking,
                )
                vm.onEvent(ScheduleUiEvent.Confirm)
                val booked = awaitUntil { it is ScheduleUiState.Booked } as ScheduleUiState.Booked
                assertEquals(slot, booked.viewing.startAt)
                assertEquals(Duration.ofMinutes(30), Duration.between(booked.viewing.startAt, booked.viewing.endAt))
                assertEquals("Can I bring my dog?", booked.viewing.note)
                assertEquals(AuthRepository.GUEST_USER_ID, booked.viewing.userId)
                assertTrue(booked.reminderScheduled)
                cancelAndIgnoreRemainingEvents()
            }
            val stored =
                env.viewings.all.value
                    .single()
            assertEquals(listOf(stored.id), env.reminders.scheduled)
            val event =
                env.interactions.events.value
                    .single()
            assertEquals(InteractionType.ScheduledViewing, event.type)
            assertEquals("schedule_viewing", event.type.id)
            assertEquals(env.georgie.id, event.propertyId)
        }

    @Test
    fun aSignedInUserBooksUnderTheirAccount() =
        runTest(main.dispatcher) {
            env.auth.signIn()
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                assertFalse(picking.isGuest)
                vm.onEvent(ScheduleUiEvent.SelectSlot(picking.firstFree()))
                awaitUntil { (it as? ScheduleUiState.Picking)?.canConfirm == true }
                vm.onEvent(ScheduleUiEvent.Confirm)
                val booked = awaitUntil { it is ScheduleUiState.Booked } as ScheduleUiState.Booked
                assertEquals(env.auth.account.id, booked.viewing.userId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSlotTakenMeanwhileDropsOutOfTheSelection() =
        runTest(main.dispatcher) {
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                val slot = picking.firstFree()
                vm.onEvent(ScheduleUiEvent.SelectSlot(slot))
                awaitUntil { (it as? ScheduleUiState.Picking)?.selectedSlot == slot }
                env.viewings.add(Viewing(0, "someone-else", env.georgie.id, slot, 30, "", env.now))
                val updated =
                    awaitUntil {
                        (it as? ScheduleUiState.Picking)?.selectedSlot == null
                    } as ScheduleUiState.Picking
                assertFalse(updated.slots.first { it.start == slot }.isAvailable)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRejectedBookingExplainsWhyAndClearsTheSlot() =
        runTest(main.dispatcher) {
            val vm = env.scheduleViewModel()
            env.viewings.nextResult = BookingResult.Rejected(BookingError.Overlaps)
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                vm.onEvent(ScheduleUiEvent.SelectSlot(picking.firstFree()))
                awaitUntil { (it as? ScheduleUiState.Picking)?.canConfirm == true }
                vm.onEvent(ScheduleUiEvent.Confirm)
                val rejected = awaitUntil { (it as? ScheduleUiState.Picking)?.error != null } as ScheduleUiState.Picking
                assertEquals(ScheduleError.Overlaps, rejected.error)
                assertNull(rejected.selectedSlot)
                assertFalse(rejected.submitting)
                // Picking another slot clears the message.
                vm.onEvent(ScheduleUiEvent.SelectSlot(rejected.firstFree()))
                assertNull(
                    (awaitUntil { (it as? ScheduleUiState.Picking)?.selectedSlot != null } as ScheduleUiState.Picking)
                        .error,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(env.reminders.scheduled.isEmpty())
            assertTrue(
                env.interactions.events.value
                    .isEmpty(),
            )
        }

    @Test
    fun choosingAnotherDayShowsItsSlotsAndClearsTheTime() =
        runTest(main.dispatcher) {
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                vm.onEvent(ScheduleUiEvent.SelectSlot(picking.firstFree()))
                awaitUntil { (it as? ScheduleUiState.Picking)?.selectedSlot != null }
                vm.onEvent(ScheduleUiEvent.SelectDate(monday.plusDays(3)))
                val thursday = awaitUntil { (it as? ScheduleUiState.Picking)?.selectedDate == monday.plusDays(3) }
                thursday as ScheduleUiState.Picking
                assertNull(thursday.selectedSlot)
                assertEquals(18, thursday.slots.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theNoteIsCappedAndAViewingStartingSoonHasNoReminder() =
        runTest(main.dispatcher) {
            env.reminders.schedules = false
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                vm.onEvent(ScheduleUiEvent.NoteChanged("x".repeat(400)))
                vm.onEvent(ScheduleUiEvent.SelectSlot(picking.firstFree()))
                val ready =
                    awaitUntil {
                        (it as? ScheduleUiState.Picking)?.canConfirm == true
                    } as ScheduleUiState.Picking
                assertEquals(ScheduleViewingViewModel.MAX_NOTE_LENGTH, ready.note.length)
                vm.onEvent(ScheduleUiEvent.Confirm)
                assertFalse((awaitUntil { it is ScheduleUiState.Booked } as ScheduleUiState.Booked).reminderScheduled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun addToCalendarCarriesTheViewing() =
        runTest(main.dispatcher) {
            val vm = env.scheduleViewModel()
            vm.state.test {
                val picking = awaitUntil { it is ScheduleUiState.Picking } as ScheduleUiState.Picking
                vm.onEvent(ScheduleUiEvent.SelectSlot(picking.firstFree()))
                awaitUntil { (it as? ScheduleUiState.Picking)?.canConfirm == true }
                vm.onEvent(ScheduleUiEvent.Confirm)
                val booked = awaitUntil { it is ScheduleUiState.Booked } as ScheduleUiState.Booked
                vm.effects.test {
                    vm.onEvent(ScheduleUiEvent.AddToCalendar)
                    val effect = awaitItem() as ScheduleUiEffect.AddToCalendar
                    assertEquals(env.georgie.title, effect.propertyTitle)
                    assertEquals(env.georgie.locality, effect.location)
                    assertEquals(booked.viewing.startAt, effect.begin)
                    assertEquals(booked.viewing.endAt, effect.end)
                    assertEquals(PropertyDeepLink.uriFor(env.georgie.id), effect.link)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anUnknownListingIsNotFound() =
        runTest(main.dispatcher) {
            env.scheduleViewModel(id = "missing").state.test {
                assertEquals(ScheduleUiState.NotFound, awaitUntil { it != ScheduleUiState.Loading })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
