package com.findora.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.chat.ActiveConversation
import com.findora.app.core.domain.chat.AgentResponder
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.chat.QuickReply
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.notification.ChatNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** A row of the thread: a day header or a message. */
sealed interface ChatItem {
    val key: String

    data class DaySeparator(
        val date: LocalDate,
    ) : ChatItem {
        override val key: String get() = "day-$date"
    }

    data class Bubble(
        val message: ChatMessage,
    ) : ChatItem {
        override val key: String get() = message.id
    }
}

sealed interface ChatUiState {
    data object Loading : ChatUiState

    /** The thread does not exist, belongs to someone else, or its listing is gone before any message. */
    data object NotFound : ChatUiState

    data class Content(
        /** Null until the first message creates the thread. */
        val conversationId: String?,
        val agent: ChatAgent,
        val propertyId: String,
        val propertyTitle: String,
        /** The listing for the card at the top; null when it left the cache. */
        val property: Property?,
        val items: List<ChatItem>,
        val agentTyping: Boolean,
        val draft: String,
        val zone: ZoneId,
        val today: LocalDate,
    ) : ChatUiState {
        val quickReplies: List<QuickReply> get() = QuickReply.entries
        val canSend: Boolean get() = draft.isNotBlank() && (conversationId != null || property != null)
        val isEmpty: Boolean get() = items.isEmpty()
    }
}

sealed interface ChatUiEvent {
    data class DraftChanged(
        val text: String,
    ) : ChatUiEvent

    /** Sends the draft. */
    data object Send : ChatUiEvent

    /** Sends a quick reply's localized text as it is. */
    data class SendQuickReply(
        val text: String,
    ) : ChatUiEvent

    /** The screen is visible (resumed) or not: drives read receipts and notification suppression. */
    data class VisibilityChanged(
        val visible: Boolean,
    ) : ChatUiEvent
}

/**
 * One thread with a listing's demo agent. Opened from a thread id (list, notification link) or from a listing id
 * ("Message agent"); in the latter case the thread is created by the first message so the list never shows an
 * empty one. While the screen is visible the thread is the active one: agent messages are read on arrival and
 * no notification is posted for them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val chats: ChatRepository,
        private val properties: PropertyRepository,
        private val notifications: NotificationRepository,
        private val responder: AgentResponder,
        private val interactions: InteractionRepository,
        private val active: ActiveConversation,
        private val notifier: ChatNotifier,
        private val auth: AuthRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val requestedConversationId: String? = savedStateHandle.get<String>(CONVERSATION_ID_KEY)
        private val requestedPropertyId: String? = savedStateHandle.get<String>(PROPERTY_ID_KEY)

        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val draft = MutableStateFlow("")
        private val visible = MutableStateFlow(false)

        private val conversation: Flow<Conversation?> =
            userId.flatMapLatest { user ->
                when {
                    requestedConversationId != null -> chats.observeConversation(user, requestedConversationId)
                    requestedPropertyId != null -> chats.observeConversationFor(user, requestedPropertyId)
                    else -> flowOf(null)
                }
            }

        private val thread: Flow<Thread> =
            conversation.flatMapLatest { found ->
                val propertyId = found?.propertyId ?: requestedPropertyId.takeIf { requestedConversationId == null }
                val property = propertyId?.let { properties.observeProperty(it) } ?: flowOf(null)
                val messages =
                    found?.let { conv -> userId.flatMapLatest { chats.observeMessages(it, conv.id) } }
                        ?: flowOf(emptyList())
                combine(property, messages) { listing, list -> Thread(found, listing, list) }
            }

        val state: StateFlow<ChatUiState> =
            combine(thread, responder.typing, draft) { current, typing, text ->
                content(current, typing, text)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatUiState.Loading)

        /** The thread this screen registered as active, so it can step down when hidden or cleared. */
        private var enteredId: String? = null

        init {
            viewModelScope.launch {
                combine(visible, thread, userId) { shown, current, user -> Triple(shown, current, user) }
                    .collect { (shown, current, user) -> onThreadSeen(shown, current, user) }
            }
        }

        private suspend fun onThreadSeen(
            shown: Boolean,
            current: Thread,
            user: String,
        ) {
            val id = current.conversation?.id
            if (!shown || id == null) {
                enteredId?.let(active::leave)
                enteredId = null
                return
            }
            val entering = enteredId != id
            if (entering) {
                enteredId?.let(active::leave)
                active.enter(id)
                enteredId = id
            }
            // Entering settles the thread (and its notifications); later, any agent message that lands is read.
            val unread = current.messages.any { !it.fromUser && it.status != DeliveryStatus.Read }
            if (entering || unread) settle(user, id)
        }

        fun onEvent(event: ChatUiEvent) {
            when (event) {
                is ChatUiEvent.DraftChanged -> draft.value = event.text.take(MAX_MESSAGE_LENGTH)
                ChatUiEvent.Send -> send(draft.value, clearDraft = true)
                is ChatUiEvent.SendQuickReply -> send(event.text, clearDraft = false)
                is ChatUiEvent.VisibilityChanged -> visible.value = event.visible
            }
        }

        private fun content(
            current: Thread,
            typing: Set<String>,
            text: String,
        ): ChatUiState {
            val found = current.conversation
            val property = current.property
            val agent = found?.agent ?: property?.let { ChatAgent(it.agent.id, it.agent.name, it.agent.avatar) }
            val propertyId = found?.propertyId ?: property?.id
            val title = found?.propertyTitle ?: property?.title
            if (agent == null || propertyId == null || title == null) return ChatUiState.NotFound
            return ChatUiState.Content(
                conversationId = found?.id,
                agent = agent,
                propertyId = propertyId,
                propertyTitle = title,
                property = property,
                items = ChatItems.build(current.messages, zone),
                agentTyping = found != null && found.id in typing,
                draft = text,
                zone = zone,
                today = clock.now().atZone(zone).toLocalDate(),
            )
        }

        private fun send(
            raw: String,
            clearDraft: Boolean,
        ) {
            val text = raw.trim().take(MAX_MESSAGE_LENGTH)
            val content = state.value as? ChatUiState.Content
            if (text.isEmpty() || content == null) return
            if (clearDraft) draft.value = ""
            viewModelScope.launch {
                val user = userId.first()
                val conversationId =
                    content.conversationId
                        ?: content.property?.let { bestEffort { chats.startConversation(user, it).id } }
                        ?: return@launch
                val message = bestEffort { chats.sendMessage(user, conversationId, text) }
                if (message == null) {
                    if (clearDraft) draft.update { it.ifEmpty { text } }
                    return@launch
                }
                responder.onUserMessage(user, conversationId, message.id, text)
                if (content.conversationId == null) {
                    bestEffort {
                        interactions.record(
                            InteractionEvent(user, content.propertyId, InteractionType.ContactedAgent, clock.now()),
                        )
                    }
                }
            }
        }

        private suspend fun settle(
            user: String,
            conversationId: String,
        ) {
            bestEffort { chats.markConversationRead(user, conversationId) }
            bestEffort { notifications.markConversationRead(user, conversationId) }
            bestEffort { notifier.cancel(conversationId) }
        }

        override fun onCleared() {
            enteredId?.let(active::leave)
            super.onCleared()
        }

        /** Storage hiccups are logged and surface as "nothing happened"; they never crash the screen. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> bestEffort(block: suspend () -> T): T? =
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Chat operation failed", error)
                null
            }

        private data class Thread(
            val conversation: Conversation?,
            val property: Property?,
            val messages: List<ChatMessage>,
        )

        companion object {
            /** The `FindoraRoute.Chat` field. */
            const val CONVERSATION_ID_KEY = "conversationId"

            /** The `FindoraRoute.ChatWithAgent` field. */
            const val PROPERTY_ID_KEY = "propertyId"
            const val MAX_MESSAGE_LENGTH = 1_000
            private const val STOP_TIMEOUT_MILLIS = 5_000L
            private const val TAG = "ChatViewModel"
        }
    }

/** Inserts a day header before the first message of each calendar day (in [zone]). */
internal object ChatItems {
    fun build(
        messages: List<ChatMessage>,
        zone: ZoneId,
    ): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        var lastDay: LocalDate? = null
        messages.forEach { message ->
            val day = message.sentAt.atZone(zone).toLocalDate()
            if (day != lastDay) {
                items += ChatItem.DaySeparator(day)
                lastDay = day
            }
            items += ChatItem.Bubble(message)
        }
        return items
    }
}
