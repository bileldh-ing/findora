package com.findora.app.navigation

import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeChatRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.RecordingAlertScheduler
import com.findora.app.core.testing.RecordingWidgetRefresher
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MainShellViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = Instant.parse("2026-10-24T09:00:00Z")
    private val auth = FakeAuthRepository { now }
    private val favorites = FakeFavoritesRepository()
    private val viewings = FakeViewingRepository { now }
    private val scheduler = RecordingAlertScheduler()
    private val widget = RecordingWidgetRefresher()

    private fun viewModel() =
        MainShellViewModel(FakeChatRepository { now }, auth, favorites, viewings, scheduler, widget)

    @Test
    fun openingTheAppSchedulesTheAlertCheckAndTheWidgetRefresh() =
        runTest(main.dispatcher) {
            viewModel()
            assertEquals(1, scheduler.scheduled)
            assertEquals(1, widget.scheduled)
            assertEquals(0, widget.refreshes)
        }

    @Test
    fun favouritesViewingsAndAnotherUserRedrawTheWidget() =
        runTest(main.dispatcher) {
            viewModel()
            favorites.toggle("guest", seedProperties.first().id)
            assertEquals(1, widget.refreshes)
            viewings.add(Viewing(0, "guest", seedProperties.first().id, now.plus(Duration.ofDays(1)), 30, "", now))
            assertEquals(2, widget.refreshes)
            auth.signIn()
            assertEquals(3, widget.refreshes)
            // A change for someone else does not.
            favorites.toggle("guest", seedProperties[1].id)
            assertEquals(3, widget.refreshes)
        }
}
