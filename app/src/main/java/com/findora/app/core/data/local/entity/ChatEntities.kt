package com.findora.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user's thread with the agent of one listing (one per user and listing). The agent and listing title are
 * snapshotted so the conversation list still reads well when the listing leaves the cache.
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["user_id", "property_id"], unique = true)],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "property_title") val propertyTitle: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "agent_name") val agentName: String,
    @ColumnInfo(name = "agent_initials") val agentInitials: String,
    @ColumnInfo(name = "agent_color") val agentColorArgb: Long,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)

/**
 * A chat message. [readAtMillis] is when the recipient read it (the agent for the user's messages, the user for
 * the agent's); [status] is "sending" until the message is delivered, then "sent". Existing v2 rows default to
 * "sent".
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation_id", "sent_at"]), Index(value = ["recipient_id", "read_at"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "recipient_id") val recipientId: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAtMillis: Long,
    @ColumnInfo(name = "read_at") val readAtMillis: Long?,
    @ColumnInfo(defaultValue = STATUS_SENT) val status: String = STATUS_SENT,
) {
    companion object {
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
    }
}

/** A conversation row joined with its newest message and the user's unread count (conversation list). */
data class ConversationSummaryRow(
    val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "property_title") val propertyTitle: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "agent_name") val agentName: String,
    @ColumnInfo(name = "agent_initials") val agentInitials: String,
    @ColumnInfo(name = "agent_color") val agentColorArgb: Long,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "message_sender_id") val messageSenderId: String,
    @ColumnInfo(name = "message_body") val messageBody: String,
    @ColumnInfo(name = "message_sent_at") val messageSentAtMillis: Long,
    @ColumnInfo(name = "message_read_at") val messageReadAtMillis: Long?,
    @ColumnInfo(name = "message_status") val messageStatus: String,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
)

/** An entry of the in-app notifications center, owned by one user. */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["user_id", "created_at"]), Index(value = ["user_id", "read_at"])],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val kind: String,
    val subject: String,
    val preview: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String?,
    @ColumnInfo(name = "event_at") val eventAtMillis: Long?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "read_at") val readAtMillis: Long?,
    /** v4: the saved search a new-matches notification opens. */
    @ColumnInfo(name = "saved_search_id") val savedSearchId: Long? = null,
    /** v4: how many new listings a new-matches notification reports. */
    @ColumnInfo(name = "item_count", defaultValue = "0") val itemCount: Int = 0,
)
