package com.findora.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.chat.AgentResponder
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface ConversationsUiState {
    data object Loading : ConversationsUiState

    data class Content(
        /** Newest activity first. */
        val conversations: List<ConversationSummary>,
        /** Threads where the agent is typing, shown in place of the preview. */
        val typing: Set<String>,
        val zone: ZoneId,
        val today: LocalDate,
    ) : ConversationsUiState {
        val unreadTotal: Int get() = conversations.sumOf { it.unreadCount }
    }
}

/** The Chat tab: the signed-in user's (or guest's) threads with listing agents. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        chats: ChatRepository,
        responder: AgentResponder,
        auth: AuthRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        val state: StateFlow<ConversationsUiState> =
            combine(
                auth.session
                    .map { it?.userId ?: AuthRepository.GUEST_USER_ID }
                    .distinctUntilChanged()
                    .flatMapLatest { chats.observeConversations(it) },
                responder.typing,
            ) { conversations, typing ->
                ConversationsUiState.Content(
                    conversations = conversations,
                    typing = typing,
                    zone = zone,
                    today = clock.now().atZone(zone).toLocalDate(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConversationsUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
