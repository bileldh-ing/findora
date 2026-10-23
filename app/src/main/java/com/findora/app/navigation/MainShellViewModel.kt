package com.findora.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.HomeWidgetRefresher
import com.findora.app.core.domain.repository.SearchAlertScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the tab shell itself: the unread badge on the Chat tab. While the app is open it also keeps the
 * background work in place: the periodic saved-search check is scheduled, and the home-screen widget is redrawn
 * whenever the user's favourites or viewings change (and when another user signs in).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainShellViewModel
    @Inject
    constructor(
        chats: ChatRepository,
        auth: AuthRepository,
        favorites: FavoritesRepository,
        viewings: ViewingRepository,
        alertScheduler: SearchAlertScheduler,
        private val widgetRefresher: HomeWidgetRefresher,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        val unreadMessages: StateFlow<Int> =
            userId
                .flatMapLatest { chats.observeUnreadCount(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

        init {
            alertScheduler.ensureScheduled()
            widgetRefresher.ensureScheduled()
            viewModelScope.launch {
                userId
                    .flatMapLatest { user ->
                        combine(favorites.observeFavoriteIds(user), viewings.observeViewings(user)) { ids, booked ->
                            user to (ids to booked.map { it.id to it.startAt })
                        }
                    }.distinctUntilChanged()
                    // The first value is what the widget already shows; only changes need a redraw.
                    .drop(1)
                    .collect { widgetRefresher.requestRefresh() }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
