package com.findora.app.core.domain.repository

import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.domain.model.Property
import kotlinx.coroutines.flow.Flow

/**
 * Conversations with listing agents, owned by one user id (a signed-in account or the guest id). Every read and
 * write takes the user id, so one user can never see or change another user's threads.
 *
 * Each step of a message's life (sent, delivered, read by the agent, answered, read by the user) is one call,
 * which is why the interface is wider than the detekt default.
 */
@Suppress("TooManyFunctions")
interface ChatRepository {
    /** The user's threads that have at least one message, newest activity first. */
    fun observeConversations(userId: String): Flow<List<ConversationSummary>>

    /** The thread [conversationId] when it belongs to [userId], else null. */
    fun observeConversation(
        userId: String,
        conversationId: String,
    ): Flow<Conversation?>

    /** The user's thread about [propertyId], or null before the first message. */
    fun observeConversationFor(
        userId: String,
        propertyId: String,
    ): Flow<Conversation?>

    /** Messages of the user's thread, oldest first (empty for someone else's thread). */
    fun observeMessages(
        userId: String,
        conversationId: String,
    ): Flow<List<ChatMessage>>

    /** Agent messages the user has not read yet, across all threads (the Chat tab badge). */
    fun observeUnreadCount(userId: String): Flow<Int>

    /** The user's thread about [property], created on first use with the agent's name and avatar. */
    suspend fun startConversation(
        userId: String,
        property: Property,
    ): Conversation

    /** Writes the user's message as [com.findora.app.core.domain.chat.DeliveryStatus.Sending]. */
    suspend fun sendMessage(
        userId: String,
        conversationId: String,
        body: String,
    ): ChatMessage?

    /** The message reached the agent. */
    suspend fun markDelivered(messageId: String)

    /** The agent opened the thread: every message the user sent there is read. */
    suspend fun markReadByAgent(conversationId: String)

    /** Stores an agent message addressed to the thread's user. */
    suspend fun receiveAgentMessage(
        conversationId: String,
        body: String,
    ): ChatMessage?

    /** The user saw the thread: its agent messages are read. Returns how many changed. */
    suspend fun markConversationRead(
        userId: String,
        conversationId: String,
    ): Int

    /** Whether the agent already answered in the thread (so it does not introduce itself again). */
    suspend fun hasAgentReplied(conversationId: String): Boolean
}
