package com.findora.app.core.domain.chat

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.InitialsAvatar
import java.time.Instant

/** The listing agent a conversation is with, snapshotted so the thread still reads well if the listing leaves. */
@Immutable
data class ChatAgent(
    val id: String,
    val name: String,
    val avatar: InitialsAvatar,
)

/** One thread between a user and the agent of one listing. A user has at most one thread per listing. */
@Immutable
data class Conversation(
    val id: String,
    val userId: String,
    val propertyId: String,
    val propertyTitle: String,
    val agent: ChatAgent,
    val createdAt: Instant,
)

/** Delivery of a message the user sent: written locally, delivered, then read by the agent. */
enum class DeliveryStatus { Sending, Sent, Read }

@Immutable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    /** True for the user's own messages, false for the agent's. */
    val fromUser: Boolean,
    val body: String,
    val sentAt: Instant,
    /** For the user's messages: where delivery stands. For the agent's: [DeliveryStatus.Read] once the user saw it. */
    val status: DeliveryStatus,
)

/** A row of the conversation list: the thread, its newest message and how many agent messages are unread. */
@Immutable
data class ConversationSummary(
    val conversation: Conversation,
    val lastMessage: ChatMessage,
    val unreadCount: Int,
)

/** Quick replies offered above the composer; the text sent is the localized label of each. */
enum class QuickReply { StillAvailable, VisitThisWeek, PriceNegotiable }
