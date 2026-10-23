package com.findora.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.notification.AppNotification
import com.findora.app.core.domain.notification.NotificationTarget
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState

    data class Content(
        /** Newest first. */
        val notifications: List<AppNotification>,
        val zone: ZoneId,
        val today: LocalDate,
    ) : NotificationsUiState {
        val unreadCount: Int get() = notifications.count { !it.isRead }
    }
}

sealed interface NotificationsUiEvent {
    /** Tapped: marked read, then its target opens. */
    data class Open(
        val notification: AppNotification,
    ) : NotificationsUiEvent

    data class MarkRead(
        val id: Long,
    ) : NotificationsUiEvent

    data object MarkAllRead : NotificationsUiEvent

    /** Swiped away (or the TalkBack "Delete" action). */
    data class Delete(
        val id: Long,
    ) : NotificationsUiEvent
}

sealed interface NotificationsUiEffect {
    data class Navigate(
        val target: NotificationTarget,
    ) : NotificationsUiEffect

    data object Deleted : NotificationsUiEffect
}

/** The notifications center: new messages and viewing notifications, per user, from Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        auth: AuthRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val effectChannel = Channel<NotificationsUiEffect>(Channel.BUFFERED)

        val effects: Flow<NotificationsUiEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<NotificationsUiState> =
            userId
                .flatMapLatest { notifications.observeNotifications(it) }
                .map { list ->
                    NotificationsUiState.Content(list, zone, clock.now().atZone(zone).toLocalDate())
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    NotificationsUiState.Loading,
                )

        fun onEvent(event: NotificationsUiEvent) {
            viewModelScope.launch {
                val user = userId.first()
                when (event) {
                    is NotificationsUiEvent.Open -> {
                        if (!event.notification.isRead) {
                            bestEffort { notifications.markRead(user, event.notification.id) }
                        }
                        effectChannel.send(NotificationsUiEffect.Navigate(event.notification.target))
                    }
                    is NotificationsUiEvent.MarkRead -> bestEffort { notifications.markRead(user, event.id) }
                    NotificationsUiEvent.MarkAllRead -> bestEffort { notifications.markAllRead(user) }
                    is NotificationsUiEvent.Delete ->
                        if (bestEffort { notifications.delete(user, event.id) } != null) {
                            effectChannel.send(NotificationsUiEffect.Deleted)
                        }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> bestEffort(block: suspend () -> T): T? =
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Notification update failed", error)
                null
            }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TAG = "Notifications"
        }
    }
