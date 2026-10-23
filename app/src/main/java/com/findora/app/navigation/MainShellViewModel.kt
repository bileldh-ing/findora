package com.findora.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** State of the tab shell itself: the unread badge on the Chat tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainShellViewModel
    @Inject
    constructor(
        chats: ChatRepository,
        auth: AuthRepository,
    ) : ViewModel() {
        val unreadMessages: StateFlow<Int> =
            auth.session
                .map { it?.userId ?: AuthRepository.GUEST_USER_ID }
                .distinctUntilChanged()
                .flatMapLatest { chats.observeUnreadCount(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
