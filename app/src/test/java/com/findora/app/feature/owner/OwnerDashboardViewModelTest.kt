package com.findora.app.feature.owner

import app.cash.turbine.test
import com.findora.app.core.domain.owner.ListingSignal
import com.findora.app.core.domain.owner.ListingStats
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.domain.owner.OwnerSignals
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeOwnerListingRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.completeDraft
import com.findora.app.feature.owner.dashboard.OwnerDashboardEffect
import com.findora.app.feature.owner.dashboard.OwnerDashboardEvent
import com.findora.app.feature.owner.dashboard.OwnerDashboardViewModel
import com.findora.app.feature.owner.dashboard.OwnerMessage
import com.findora.app.feature.owner.dashboard.PendingAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OwnerDashboardViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = Instant.parse("2026-10-23T09:00:00Z")
    private val auth = FakeAuthRepository { now }
    private val repo = FakeOwnerListingRepository { now }

    private fun viewModel() = OwnerDashboardViewModel(auth, repo, { now }, ZoneId.of("Africa/Tunis"))

    private fun listing(
        id: String,
        status: ListingStatus,
        draft: com.findora.app.core.domain.owner.ListingDraft = completeDraft(),
    ) = repo.put(OwnerListing(id, auth.account.id, status, draft, now, now, null))

    @Test
    fun showsListingsWithStatsDerivedFromOtherUsersActivity() =
        runTest(main.dispatcher) {
            auth.signIn()
            listing("own-1", ListingStatus.Published)
            listing("own-2", ListingStatus.Draft)
            repo.signals.value =
                OwnerSignals(
                    views = listOf(ListingSignal("own-1", "u2", now), ListingSignal("own-1", "u3", now)),
                    saves = listOf(ListingSignal("own-1", "u2", now)),
                    viewingRequests = listOf(ListingSignal("own-1", "u3", now)),
                )
            viewModel().state.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(setOf("own-1", "own-2"), state.items.map { it.listing.id }.toSet())
                assertEquals(ListingStats(2, 1, 1), state.items.first { it.listing.id == "own-1" }.stats)
                assertEquals(ListingStats(), state.items.first { it.listing.id == "own-2" }.stats)
                assertEquals(ListingStats(2, 1, 1), state.totals)
                assertEquals(14, state.dailyViews.size)
                assertEquals(2, state.dailyViews.last().count)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun guestsSeeThatListingNeedsAnAccount() =
        runTest(main.dispatcher) {
            auth.continueAsGuest()
            viewModel().state.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertTrue(state.needsAccount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pauseAndDeleteWaitForConfirmation() =
        runTest(main.dispatcher) {
            auth.signIn()
            listing("own-1", ListingStatus.Published)
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                vm.onEvent(OwnerDashboardEvent.RequestPause("own-1"))
                assertEquals(PendingAction.Kind.Pause, awaitItem().pending?.kind)
                vm.onEvent(OwnerDashboardEvent.Dismiss)
                assertNull(awaitItem().pending)
                assertEquals(
                    ListingStatus.Published,
                    repo.listings.value
                        .getValue("own-1")
                        .status,
                )
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test {
                vm.onEvent(OwnerDashboardEvent.RequestPause("own-1"))
                vm.onEvent(OwnerDashboardEvent.Confirm)
                assertEquals(OwnerDashboardEffect.Show(OwnerMessage.Paused), awaitItem())
                assertEquals(
                    ListingStatus.Paused,
                    repo.listings.value
                        .getValue("own-1")
                        .status,
                )
                vm.onEvent(OwnerDashboardEvent.Resume("own-1"))
                assertEquals(OwnerDashboardEffect.Show(OwnerMessage.Published), awaitItem())
                vm.onEvent(OwnerDashboardEvent.RequestUnpublish("own-1"))
                vm.onEvent(OwnerDashboardEvent.Confirm)
                assertEquals(OwnerDashboardEffect.Show(OwnerMessage.Unpublished), awaitItem())
                vm.onEvent(OwnerDashboardEvent.RequestDelete("own-1"))
                vm.onEvent(OwnerDashboardEvent.Confirm)
                assertEquals(OwnerDashboardEffect.Show(OwnerMessage.Deleted), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repo.listings.value.isEmpty())
        }

    @Test
    fun pausingADraftIsNotOfferedAndResumingAnIncompleteListingOpensTheWizard() =
        runTest(main.dispatcher) {
            auth.signIn()
            listing("own-1", ListingStatus.Paused, completeDraft().copy(photos = emptyList()))
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                vm.onEvent(OwnerDashboardEvent.RequestPause("own-1"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test {
                vm.onEvent(OwnerDashboardEvent.Resume("own-1"))
                assertEquals(OwnerDashboardEffect.FinishListing("own-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
