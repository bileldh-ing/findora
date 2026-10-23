package com.findora.app.feature.owner.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.DailyCount
import com.findora.app.core.domain.owner.ListingStats
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerEnquiry
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.domain.owner.OwnerListingRepository
import com.findora.app.core.domain.owner.OwnerStatsCalculator
import com.findora.app.core.domain.owner.PublishResult
import com.findora.app.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** One row of "My listings". */
data class OwnerListingItem(
    val listing: OwnerListing,
    val stats: ListingStats,
)

/** A destructive action waiting for the owner's confirmation. */
data class PendingAction(
    val kind: Kind,
    val listingId: String,
    val title: String,
) {
    enum class Kind { Pause, Unpublish, Delete }
}

data class OwnerDashboardUiState(
    val loading: Boolean = true,
    /** Signed out or a guest: listing needs an account. */
    val needsAccount: Boolean = false,
    val items: List<OwnerListingItem> = emptyList(),
    val totals: ListingStats = ListingStats(),
    val dailyViews: List<DailyCount> = emptyList(),
    val enquiries: List<OwnerEnquiry> = emptyList(),
    val pending: PendingAction? = null,
)

sealed interface OwnerDashboardEvent {
    data class RequestPause(
        val listingId: String,
    ) : OwnerDashboardEvent

    data class RequestUnpublish(
        val listingId: String,
    ) : OwnerDashboardEvent

    data class RequestDelete(
        val listingId: String,
    ) : OwnerDashboardEvent

    data object Confirm : OwnerDashboardEvent

    data object Dismiss : OwnerDashboardEvent

    /** Publishes a paused or unpublished listing again (no confirmation: it is easily undone). */
    data class Resume(
        val listingId: String,
    ) : OwnerDashboardEvent
}

enum class OwnerMessage { Paused, Unpublished, Deleted, Published }

sealed interface OwnerDashboardEffect {
    data class Show(
        val message: OwnerMessage,
    ) : OwnerDashboardEffect

    /** The listing cannot go live as it is: open it in the wizard to finish it. */
    data class FinishListing(
        val listingId: String,
    ) : OwnerDashboardEffect
}

/**
 * The owner dashboard: the signed-in user's listings with their status, views, saves and enquiries, the views of the
 * last 14 days ([OwnerStatsCalculator]) and the enquiries other users sent. Pause, unpublish and delete ask for
 * confirmation first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OwnerDashboardViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val listings: OwnerListingRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val pending = MutableStateFlow<PendingAction?>(null)

        private val content: Flow<OwnerDashboardUiState> =
            auth.observeCurrentUser().flatMapLatest { user ->
                if (user ==
                    null
                ) {
                    flowOf(OwnerDashboardUiState(loading = false, needsAccount = true))
                } else {
                    dashboardOf(user)
                }
            }

        val state: StateFlow<OwnerDashboardUiState> =
            combine(content, pending) { dashboard, action -> dashboard.copy(pending = action) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OwnerDashboardUiState())

        private val effectChannel = Channel<OwnerDashboardEffect>(Channel.BUFFERED)
        val effects: Flow<OwnerDashboardEffect> = effectChannel.receiveAsFlow()

        private fun dashboardOf(user: UserAccount): Flow<OwnerDashboardUiState> =
            listings.observeListings(user.id).flatMapLatest { owned ->
                val ids = owned.map(OwnerListing::id)
                combine(
                    listings.observeSignals(user.id, ids),
                    listings.observeEnquiries(user.id),
                ) { signals, enquiries ->
                    val stats =
                        OwnerStatsCalculator.calculate(
                            ids,
                            user.id,
                            signals,
                            clock.now().atZone(zone).toLocalDate(),
                            zone,
                        )
                    OwnerDashboardUiState(
                        loading = false,
                        items = owned.map { OwnerListingItem(it, stats.perListing[it.id] ?: ListingStats()) },
                        totals = stats.totals,
                        dailyViews = stats.dailyViews,
                        enquiries = enquiries,
                    )
                }
            }

        fun onEvent(event: OwnerDashboardEvent) {
            when (event) {
                is OwnerDashboardEvent.RequestPause -> request(PendingAction.Kind.Pause, event.listingId)
                is OwnerDashboardEvent.RequestUnpublish -> request(PendingAction.Kind.Unpublish, event.listingId)
                is OwnerDashboardEvent.RequestDelete -> request(PendingAction.Kind.Delete, event.listingId)
                OwnerDashboardEvent.Dismiss -> pending.value = null
                OwnerDashboardEvent.Confirm -> confirm()
                is OwnerDashboardEvent.Resume -> resume(event.listingId)
            }
        }

        private fun request(
            kind: PendingAction.Kind,
            listingId: String,
        ) {
            val item = state.value.items.firstOrNull { it.listing.id == listingId } ?: return
            val allowed =
                when (kind) {
                    PendingAction.Kind.Pause -> item.listing.status == ListingStatus.Published
                    PendingAction.Kind.Unpublish -> item.listing.status != ListingStatus.Draft
                    PendingAction.Kind.Delete -> true
                }
            if (allowed) pending.value = PendingAction(kind, listingId, item.listing.draft.title)
        }

        private fun confirm() {
            val action = pending.value ?: return
            pending.update { null }
            viewModelScope.launch {
                val ownerId = auth.session.value?.userId ?: return@launch
                val (done, message) =
                    when (action.kind) {
                        PendingAction.Kind.Pause -> listings.pause(ownerId, action.listingId) to OwnerMessage.Paused
                        PendingAction.Kind.Unpublish ->
                            listings.unpublish(ownerId, action.listingId) to OwnerMessage.Unpublished
                        PendingAction.Kind.Delete -> listings.delete(ownerId, action.listingId) to OwnerMessage.Deleted
                    }
                if (done) effectChannel.send(OwnerDashboardEffect.Show(message))
            }
        }

        private fun resume(listingId: String) {
            viewModelScope.launch {
                val owner = auth.observeCurrentUser().first() ?: return@launch
                when (listings.publish(owner, listingId)) {
                    is PublishResult.Published -> effectChannel.send(OwnerDashboardEffect.Show(OwnerMessage.Published))
                    is PublishResult.Invalid -> effectChannel.send(OwnerDashboardEffect.FinishListing(listingId))
                    PublishResult.NotFound -> Unit
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
