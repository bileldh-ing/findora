package com.findora.app.feature.viewing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** A viewing with its listing; [property] is null when the listing left the cache. */
data class ViewingItem(
    val viewing: Viewing,
    val property: Property?,
)

sealed interface ViewingsUiState {
    data object Loading : ViewingsUiState

    data class Content(
        /** Not started yet, soonest first. */
        val upcoming: List<ViewingItem>,
        /** Started or over, most recent first. */
        val past: List<ViewingItem>,
        /** The viewing whose cancellation is awaiting confirmation. */
        val pendingCancel: ViewingItem?,
        val zone: ZoneId,
    ) : ViewingsUiState {
        val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
    }
}

sealed interface ViewingsUiEvent {
    data class RequestCancel(
        val viewingId: Long,
    ) : ViewingsUiEvent

    data object DismissCancel : ViewingsUiEvent

    data object ConfirmCancel : ViewingsUiEvent
}

sealed interface ViewingsUiEffect {
    data object Cancelled : ViewingsUiEffect

    data object CancelFailed : ViewingsUiEffect
}

/** The user's viewings split into upcoming and past; cancelling deletes the viewing and its reminder. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewingsViewModel
    @Inject
    constructor(
        private val viewings: ViewingRepository,
        private val properties: PropertyRepository,
        private val reminders: ViewingReminderScheduler,
        private val auth: AuthRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val pendingCancelId = MutableStateFlow<Long?>(null)
        private val effectChannel = Channel<ViewingsUiEffect>(Channel.BUFFERED)

        val effects: Flow<ViewingsUiEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<ViewingsUiState> =
            combine(userId.flatMapLatest { items(it) }, pendingCancelId) { items, pendingId ->
                val now = clock.now()
                val (upcoming, past) = items.partition { it.viewing.startAt.isAfter(now) }
                ViewingsUiState.Content(
                    upcoming = upcoming.sortedBy { it.viewing.startAt },
                    past = past.sortedByDescending { it.viewing.startAt },
                    pendingCancel = upcoming.firstOrNull { it.viewing.id == pendingId },
                    zone = zone,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ViewingsUiState.Loading)

        fun onEvent(event: ViewingsUiEvent) {
            when (event) {
                is ViewingsUiEvent.RequestCancel -> pendingCancelId.value = event.viewingId
                ViewingsUiEvent.DismissCancel -> pendingCancelId.value = null
                ViewingsUiEvent.ConfirmCancel -> confirmCancel()
            }
        }

        private fun items(user: String): Flow<List<ViewingItem>> =
            viewings.observeViewings(user).flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        list.map { viewing ->
                            properties.observeProperty(viewing.propertyId).map { ViewingItem(viewing, it) }
                        },
                    ) { it.toList() }
                }
            }

        @Suppress("TooGenericExceptionCaught")
        private fun confirmCancel() {
            val id = pendingCancelId.value ?: return
            pendingCancelId.value = null
            viewModelScope.launch {
                val cancelled =
                    try {
                        viewings.cancel(userId.first(), id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w(TAG, "Cancel failed", error)
                        false
                    }
                if (cancelled) {
                    try {
                        reminders.cancel(id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // The worker is a no-op for a deleted viewing, so a stale request is harmless.
                        android.util.Log.w(TAG, "Could not cancel reminder", error)
                    }
                }
                effectChannel.send(if (cancelled) ViewingsUiEffect.Cancelled else ViewingsUiEffect.CancelFailed)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TAG = "ViewingsViewModel"
        }
    }
