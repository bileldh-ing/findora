package com.findora.app.feature.viewing

import app.cash.turbine.test
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class ViewingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val env = ViewingTestEnv()
    private val other = seedProperties.first { it.id != env.georgie.id }

    private fun book(
        hoursFromNow: Long,
        propertyId: String = env.georgie.id,
        user: String = AuthRepository.GUEST_USER_ID,
    ): Viewing =
        env.viewings.add(Viewing(0, user, propertyId, env.now.plus(Duration.ofHours(hoursFromNow)), 30, "", env.now))

    private fun ViewingsUiState.content() = this as ViewingsUiState.Content

    @Test
    fun splitsUpcomingAndPastInTheRightOrder() =
        runTest(main.dispatcher) {
            val later = book(48)
            val soon = book(2, other.id)
            val yesterday = book(-24)
            val lastWeek = book(-24 * 7)
            book(5, user = "someone-else")
            env.viewingsViewModel().state.test {
                val content = awaitUntil { it is ViewingsUiState.Content }.content()
                assertEquals(listOf(soon.id, later.id), content.upcoming.map { it.viewing.id })
                assertEquals(listOf(yesterday.id, lastWeek.id), content.past.map { it.viewing.id })
                assertEquals(other, content.upcoming.first().property)
                assertNull(content.pendingCancel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun nothingBookedIsEmpty() =
        runTest(main.dispatcher) {
            env.viewingsViewModel().state.test {
                assertTrue(awaitUntil { it is ViewingsUiState.Content }.content().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aListingThatLeftTheCacheStillShowsTheViewing() =
        runTest(main.dispatcher) {
            book(3, propertyId = "gone")
            env.viewingsViewModel().state.test {
                val item = awaitUntil { it is ViewingsUiState.Content }.content().upcoming.single()
                assertNull(item.property)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun cancellingAsksFirstThenDeletesTheViewingAndItsReminder() =
        runTest(main.dispatcher) {
            val viewing = book(3)
            val vm = env.viewingsViewModel()
            vm.state.test {
                awaitUntil { it is ViewingsUiState.Content }
                vm.onEvent(ViewingsUiEvent.RequestCancel(viewing.id))
                assertEquals(
                    viewing,
                    awaitUntil { it.content().pendingCancel != null }.content().pendingCancel?.viewing,
                )
                vm.onEvent(ViewingsUiEvent.DismissCancel)
                awaitUntil { it.content().pendingCancel == null }
                assertEquals(1, env.viewings.all.value.size)

                vm.effects.test {
                    vm.onEvent(ViewingsUiEvent.RequestCancel(viewing.id))
                    vm.onEvent(ViewingsUiEvent.ConfirmCancel)
                    assertEquals(ViewingsUiEffect.Cancelled, awaitItem())
                }
                assertTrue(awaitUntil { it.content().upcoming.isEmpty() }.content().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(
                env.viewings.all.value
                    .isEmpty(),
            )
            assertEquals(listOf(viewing.id), env.reminders.cancelled)
        }

    @Test
    fun aFailedCancellationKeepsTheViewingAndSaysSo() =
        runTest(main.dispatcher) {
            val viewing = book(3)
            env.viewings.failCancel = true
            val vm = env.viewingsViewModel()
            vm.state.test {
                awaitUntil { it is ViewingsUiState.Content }
                vm.effects.test {
                    vm.onEvent(ViewingsUiEvent.RequestCancel(viewing.id))
                    vm.onEvent(ViewingsUiEvent.ConfirmCancel)
                    assertEquals(ViewingsUiEffect.CancelFailed, awaitItem())
                }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, env.viewings.all.value.size)
            assertTrue(env.reminders.cancelled.isEmpty())
        }

    @Test
    fun pastViewingsCannotBeCancelled() =
        runTest(main.dispatcher) {
            val past = book(-2)
            val vm = env.viewingsViewModel()
            vm.state.test {
                awaitUntil { it is ViewingsUiState.Content }
                vm.onEvent(ViewingsUiEvent.RequestCancel(past.id))
                assertNull(
                    vm.state.value
                        .content()
                        .pendingCancel,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSignedInUserSeesOnlyTheirOwnViewings() =
        runTest(main.dispatcher) {
            book(3)
            val mine = book(4, user = env.auth.account.id)
            env.auth.signIn()
            env.viewingsViewModel().state.test {
                val content = awaitUntil { it is ViewingsUiState.Content }.content()
                assertEquals(listOf(mine.id), content.upcoming.map { it.viewing.id })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
