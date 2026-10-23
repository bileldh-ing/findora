package com.findora.app.feature.notifications

import app.cash.turbine.test
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.notification.NotificationTarget
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.feature.viewing.awaitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NotificationsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-10-26T09:10:00Z")
    private val auth = FakeAuthRepository()
    private val repo = FakeNotificationRepository { now }
    private val guest = AuthRepository.GUEST_USER_ID

    private fun viewModel() = NotificationsViewModel(repo, auth, { now }, ZoneId.of("Africa/Tunis"))

    private fun NotificationsUiState.content() = this as NotificationsUiState.Content

    private suspend fun seed() {
        repo.add(guest, NewNotification(NotificationKind.ViewingBooked, "Georgie House", "p1", eventAt = now))
        repo.add(guest, NewNotification(NotificationKind.NewMessage, "Anu Thomas", "p1", "Yes", "c1"))
        repo.add("someone-else", NewNotification(NotificationKind.ViewingReminder, "Other", "p2", eventAt = now))
    }

    @Test
    fun listsOnlyTheUsersNotificationsNewestFirst() =
        runTest(main.dispatcher) {
            seed()
            viewModel().state.test {
                val content = awaitUntil { it is NotificationsUiState.Content }.content()
                assertEquals(
                    listOf(NotificationKind.NewMessage, NotificationKind.ViewingBooked),
                    content.notifications.map { it.kind },
                )
                assertEquals(2, content.unreadCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun openingMarksReadAndNavigatesToTheTarget() =
        runTest(main.dispatcher) {
            seed()
            val vm = viewModel()
            vm.effects.test {
                val list = repo.observeNotifications(guest).first()
                vm.onEvent(NotificationsUiEvent.Open(list.first { it.kind == NotificationKind.NewMessage }))
                assertEquals(NotificationsUiEffect.Navigate(NotificationTarget.Chat("c1")), awaitItem())
                vm.onEvent(NotificationsUiEvent.Open(list.first { it.kind == NotificationKind.ViewingBooked }))
                assertEquals(NotificationsUiEffect.Navigate(NotificationTarget.Property("p1")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, repo.observeUnreadCount(guest).first())
            assertEquals(1, repo.observeUnreadCount("someone-else").first())
        }

    @Test
    fun markAllReadAndMarkOneRead() =
        runTest(main.dispatcher) {
            seed()
            val vm = viewModel()
            vm.state.test {
                val first = awaitUntil { it is NotificationsUiState.Content }.content().notifications.first()
                vm.onEvent(NotificationsUiEvent.MarkRead(first.id))
                assertEquals(1, awaitUntil { it.content().unreadCount == 1 }.content().unreadCount)
                vm.onEvent(NotificationsUiEvent.MarkAllRead)
                assertTrue(awaitUntil { it.content().unreadCount == 0 }.content().notifications.all { it.isRead })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repo.observeUnreadCount("someone-else").first())
        }

    @Test
    fun swipingDeletesAndConfirms() =
        runTest(main.dispatcher) {
            seed()
            val vm = viewModel()
            val target = repo.observeNotifications(guest).first().first()
            vm.effects.test {
                vm.onEvent(NotificationsUiEvent.Delete(target.id))
                assertEquals(NotificationsUiEffect.Deleted, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repo.observeNotifications(guest).first().size)
        }
}
