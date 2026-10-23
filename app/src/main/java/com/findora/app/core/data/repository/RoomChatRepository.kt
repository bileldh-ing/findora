package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.ChatDao
import com.findora.app.core.data.local.entity.ConversationEntity
import com.findora.app.core.data.local.entity.ConversationSummaryRow
import com.findora.app.core.data.local.entity.MessageEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.model.InitialsAvatar
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed threads. Agent messages are sent by `agent:<agentId>` and addressed to the thread's user. */
@Singleton
class RoomChatRepository
    @Inject
    constructor(
        private val dao: ChatDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        override fun observeConversations(userId: String): Flow<List<ConversationSummary>> =
            dao
                .observeSummaries(userId)
                .map { rows -> rows.map { it.toDomain() } }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeConversation(
            userId: String,
            conversationId: String,
        ): Flow<Conversation?> =
            dao
                .observeConversation(userId, conversationId)
                .map { it?.toDomain() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeConversationFor(
            userId: String,
            propertyId: String,
        ): Flow<Conversation?> =
            dao
                .observeConversationFor(userId, propertyId)
                .map { it?.toDomain() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeMessages(
            userId: String,
            conversationId: String,
        ): Flow<List<ChatMessage>> =
            dao
                .observeMessages(userId, conversationId)
                .map { rows -> rows.map { it.toDomain(userId) } }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeUnreadCount(userId: String): Flow<Int> =
            dao.observeUnreadCount(userId).distinctUntilChanged().flowOn(ioDispatcher)

        override suspend fun startConversation(
            userId: String,
            property: Property,
        ): Conversation =
            withContext(ioDispatcher) {
                dao
                    .getOrCreateConversation(
                        ConversationEntity(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            propertyId = property.id,
                            propertyTitle = property.title,
                            agentId = property.agent.id,
                            agentName = property.agent.name,
                            agentInitials = property.agent.avatar.initials,
                            agentColorArgb = property.agent.avatar.backgroundArgb,
                            createdAtMillis = clock.now().toEpochMilli(),
                        ),
                    ).toDomain()
            }

        override suspend fun sendMessage(
            userId: String,
            conversationId: String,
            body: String,
        ): ChatMessage? =
            withContext(ioDispatcher) {
                val conversation = dao.conversation(conversationId)?.takeIf { it.userId == userId }
                val text = body.trim()
                if (conversation == null || text.isEmpty()) return@withContext null
                val entity =
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        propertyId = conversation.propertyId,
                        senderId = userId,
                        recipientId = agentSenderId(conversation.agentId),
                        body = text,
                        sentAtMillis = clock.now().toEpochMilli(),
                        readAtMillis = null,
                        status = MessageEntity.STATUS_SENDING,
                    )
                dao.insertMessage(entity)
                entity.toDomain(userId)
            }

        override suspend fun markDelivered(messageId: String) {
            withContext(ioDispatcher) { dao.updateStatus(messageId, MessageEntity.STATUS_SENT) }
        }

        override suspend fun markReadByAgent(conversationId: String) {
            withContext(ioDispatcher) {
                val conversation = dao.conversation(conversationId) ?: return@withContext
                dao.markReadByAgent(conversationId, conversation.userId, clock.now().toEpochMilli())
            }
        }

        override suspend fun receiveAgentMessage(
            conversationId: String,
            body: String,
        ): ChatMessage? =
            withContext(ioDispatcher) {
                val conversation = dao.conversation(conversationId) ?: return@withContext null
                val entity =
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        propertyId = conversation.propertyId,
                        senderId = agentSenderId(conversation.agentId),
                        recipientId = conversation.userId,
                        body = body,
                        sentAtMillis = clock.now().toEpochMilli(),
                        readAtMillis = null,
                    )
                dao.insertMessage(entity)
                entity.toDomain(conversation.userId)
            }

        override suspend fun markConversationRead(
            userId: String,
            conversationId: String,
        ): Int =
            withContext(ioDispatcher) {
                dao.markConversationRead(conversationId, userId, clock.now().toEpochMilli())
            }

        override suspend fun hasAgentReplied(conversationId: String): Boolean =
            withContext(ioDispatcher) { dao.hasAgentMessage(conversationId) }

        companion object {
            const val AGENT_SENDER_PREFIX = "agent:"

            fun agentSenderId(agentId: String): String = AGENT_SENDER_PREFIX + agentId
        }
    }

private fun ConversationEntity.toDomain(): Conversation =
    Conversation(
        id = id,
        userId = userId,
        propertyId = propertyId,
        propertyTitle = propertyTitle,
        agent = ChatAgent(agentId, agentName, InitialsAvatar(agentInitials, agentColorArgb)),
        createdAt = Instant.ofEpochMilli(createdAtMillis),
    )

private fun deliveryStatus(
    status: String,
    readAtMillis: Long?,
): DeliveryStatus =
    when {
        readAtMillis != null -> DeliveryStatus.Read
        status == MessageEntity.STATUS_SENDING -> DeliveryStatus.Sending
        else -> DeliveryStatus.Sent
    }

private fun MessageEntity.toDomain(userId: String): ChatMessage =
    ChatMessage(
        id = id,
        conversationId = conversationId,
        fromUser = senderId == userId,
        body = body,
        sentAt = Instant.ofEpochMilli(sentAtMillis),
        status = deliveryStatus(status, readAtMillis),
    )

private fun ConversationSummaryRow.toDomain(): ConversationSummary =
    ConversationSummary(
        conversation =
            Conversation(
                id = id,
                userId = userId,
                propertyId = propertyId,
                propertyTitle = propertyTitle,
                agent = ChatAgent(agentId, agentName, InitialsAvatar(agentInitials, agentColorArgb)),
                createdAt = Instant.ofEpochMilli(createdAtMillis),
            ),
        lastMessage =
            ChatMessage(
                id = messageId,
                conversationId = id,
                fromUser = messageSenderId == userId,
                body = messageBody,
                sentAt = Instant.ofEpochMilli(messageSentAtMillis),
                status = deliveryStatus(messageStatus, messageReadAtMillis),
            ),
        unreadCount = unreadCount,
    )
