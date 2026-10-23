package com.findora.app.core.testing

import com.findora.app.core.domain.chat.AgentResponder
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.notification.AppNotification
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.notification.ChatNotification
import com.findora.app.core.notification.ChatNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory threads with the same per-user rules as the Room repository. */
class FakeChatRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : ChatRepository {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Messages with the user they belong to; the agent's messages are addressed to that user. */
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    var failSends = false
    private var counter = 0

    private fun owner(conversationId: String): String? =
        conversations.value.firstOrNull { it.id == conversationId }?.userId

    override fun observeConversations(userId: String): Flow<List<ConversationSummary>> =
        combineState { convs, msgs ->
            convs
                .filter { it.userId == userId }
                .mapNotNull { conv ->
                    val thread = msgs.filter { it.conversationId == conv.id }
                    thread.lastOrNull()?.let { last ->
                        ConversationSummary(
                            conv,
                            last,
                            thread.count { !it.fromUser && it.status != DeliveryStatus.Read },
                        )
                    }
                }.sortedByDescending { it.lastMessage.sentAt }
        }

    private fun <T> combineState(block: (List<Conversation>, List<ChatMessage>) -> T): Flow<T> =
        kotlinx.coroutines.flow.combine(conversations, messages, block)

    override fun observeConversation(
        userId: String,
        conversationId: String,
    ): Flow<Conversation?> =
        conversations.map { list ->
            list.firstOrNull {
                it.id == conversationId &&
                    it.userId == userId
            }
        }

    override fun observeConversationFor(
        userId: String,
        propertyId: String,
    ): Flow<Conversation?> =
        conversations.map { list ->
            list.firstOrNull {
                it.propertyId == propertyId &&
                    it.userId == userId
            }
        }

    override fun observeMessages(
        userId: String,
        conversationId: String,
    ): Flow<List<ChatMessage>> =
        combineState { _, msgs ->
            if (owner(conversationId) ==
                userId
            ) {
                msgs.filter { it.conversationId == conversationId }
            } else {
                emptyList()
            }
        }

    override fun observeUnreadCount(userId: String): Flow<Int> =
        combineState { convs, msgs ->
            val ids = convs.filter { it.userId == userId }.map { it.id }.toSet()
            msgs.count { it.conversationId in ids && !it.fromUser && it.status != DeliveryStatus.Read }
        }

    override suspend fun startConversation(
        userId: String,
        property: Property,
    ): Conversation {
        conversations.value.firstOrNull { it.userId == userId && it.propertyId == property.id }?.let { return it }
        val conversation =
            Conversation(
                id = "c${++counter}",
                userId = userId,
                propertyId = property.id,
                propertyTitle = property.title,
                agent = ChatAgent(property.agent.id, property.agent.name, property.agent.avatar),
                createdAt = now(),
            )
        conversations.update { it + conversation }
        return conversation
    }

    fun addConversation(conversation: Conversation) = conversations.update { it + conversation }

    fun addMessage(message: ChatMessage) = messages.update { it + message }

    override suspend fun sendMessage(
        userId: String,
        conversationId: String,
        body: String,
    ): ChatMessage? {
        if (failSends || owner(conversationId) != userId || body.isBlank()) return null
        val message = ChatMessage("m${++counter}", conversationId, true, body.trim(), now(), DeliveryStatus.Sending)
        messages.update { it + message }
        return message
    }

    override suspend fun markDelivered(messageId: String) =
        messages.update { list ->
            list.map {
                if (it.id == messageId &&
                    it.status == DeliveryStatus.Sending
                ) {
                    it.copy(status = DeliveryStatus.Sent)
                } else {
                    it
                }
            }
        }

    override suspend fun markReadByAgent(conversationId: String) =
        messages.update { list ->
            list.map {
                if (it.conversationId == conversationId &&
                    it.fromUser
                ) {
                    it.copy(status = DeliveryStatus.Read)
                } else {
                    it
                }
            }
        }

    override suspend fun receiveAgentMessage(
        conversationId: String,
        body: String,
    ): ChatMessage? {
        owner(conversationId) ?: return null
        val message = ChatMessage("m${++counter}", conversationId, false, body, now(), DeliveryStatus.Sent)
        messages.update { it + message }
        return message
    }

    override suspend fun markConversationRead(
        userId: String,
        conversationId: String,
    ): Int {
        if (owner(conversationId) != userId) return 0
        var changed = 0
        messages.update { list ->
            list.map {
                if (it.conversationId == conversationId && !it.fromUser && it.status != DeliveryStatus.Read) {
                    changed++
                    it.copy(status = DeliveryStatus.Read)
                } else {
                    it
                }
            }
        }
        return changed
    }

    override suspend fun hasAgentReplied(conversationId: String): Boolean =
        messages.value.any { it.conversationId == conversationId && !it.fromUser }
}

class FakeNotificationRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : NotificationRepository {
    /** Every user's notifications, newest first. */
    val byUser = MutableStateFlow<Map<String, List<AppNotification>>>(emptyMap())
    private var nextId = 1L

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> = byUser.map { it[userId].orEmpty() }

    override fun observeUnreadCount(userId: String): Flow<Int> =
        byUser.map { map ->
            map[userId].orEmpty().count {
                !it.isRead
            }
        }

    override suspend fun add(
        userId: String,
        notification: NewNotification,
    ) {
        val entry =
            AppNotification(
                id = nextId++,
                kind = notification.kind,
                subject = notification.subject,
                preview = notification.preview,
                propertyId = notification.propertyId,
                conversationId = notification.conversationId,
                eventAt = notification.eventAt,
                createdAt = now(),
                readAt = null,
            )
        edit(userId) { list ->
            val kept =
                list.filterNot {
                    notification.conversationId != null &&
                        it.conversationId == notification.conversationId &&
                        it.kind == notification.kind &&
                        !it.isRead
                }
            listOf(entry) + kept
        }
    }

    override suspend fun markRead(
        userId: String,
        notificationId: Long,
    ) = edit(userId) { list -> list.map { if (it.id == notificationId && !it.isRead) it.copy(readAt = now()) else it } }

    override suspend fun markAllRead(userId: String) =
        edit(userId) { list ->
            list.map {
                if (it.isRead) it else it.copy(readAt = now())
            }
        }

    override suspend fun markConversationRead(
        userId: String,
        conversationId: String,
    ) = edit(userId) { list ->
        list.map {
            if (it.conversationId == conversationId &&
                !it.isRead
            ) {
                it.copy(readAt = now())
            } else {
                it
            }
        }
    }

    override suspend fun delete(
        userId: String,
        notificationId: Long,
    ) = edit(userId) { list -> list.filterNot { it.id == notificationId } }

    private fun edit(
        userId: String,
        change: (List<AppNotification>) -> List<AppNotification>,
    ) = byUser.update { map -> map + (userId to change(map[userId].orEmpty())) }
}

class RecordingAgentResponder : AgentResponder {
    val calls = mutableListOf<Triple<String, String, String>>()
    override val typing: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    override fun onUserMessage(
        userId: String,
        conversationId: String,
        messageId: String,
        text: String,
    ) {
        calls += Triple(userId, conversationId, text)
    }
}

class RecordingChatNotifier(
    var allowed: Boolean = true,
) : ChatNotifier {
    val posted = mutableListOf<ChatNotification>()
    val cancelled = mutableListOf<String>()

    override fun post(notification: ChatNotification): Boolean {
        if (allowed) posted += notification
        return allowed
    }

    override fun cancel(conversationId: String) {
        cancelled += conversationId
    }
}

fun notification(
    id: Long,
    kind: NotificationKind = NotificationKind.NewMessage,
    read: Boolean = false,
    conversationId: String? = if (kind == NotificationKind.NewMessage) "c1" else null,
    createdAt: Instant = Instant.EPOCH,
): AppNotification =
    AppNotification(
        id = id,
        kind = kind,
        subject = if (kind == NotificationKind.NewMessage) "Anu Thomas" else seedProperties.first().title,
        preview = if (kind == NotificationKind.NewMessage) "Yes, it is still available." else "",
        propertyId = seedProperties.first().id,
        conversationId = conversationId,
        eventAt = if (kind == NotificationKind.NewMessage) null else createdAt.plusSeconds(3_600),
        createdAt = createdAt,
        readAt = if (read) createdAt else null,
    )
