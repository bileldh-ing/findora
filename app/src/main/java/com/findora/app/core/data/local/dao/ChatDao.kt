package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.ConversationEntity
import com.findora.app.core.data.local.entity.ConversationSummaryRow
import com.findora.app.core.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** Conversations and their messages. Reads that serve a user always filter by that user's id. */
@Suppress("TooManyFunctions")
@Dao
abstract class ChatDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertConversationIfAbsent(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE user_id = :userId AND property_id = :propertyId")
    abstract suspend fun conversationFor(
        userId: String,
        propertyId: String,
    ): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    abstract suspend fun conversation(conversationId: String): ConversationEntity?

    /** Inserts [candidate] unless the user already has a thread for that listing; returns the stored thread. */
    @Transaction
    open suspend fun getOrCreateConversation(candidate: ConversationEntity): ConversationEntity {
        insertConversationIfAbsent(candidate)
        return checkNotNull(conversationFor(candidate.userId, candidate.propertyId))
    }

    @Query("SELECT * FROM conversations WHERE id = :conversationId AND user_id = :userId")
    abstract fun observeConversation(
        userId: String,
        conversationId: String,
    ): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE user_id = :userId AND property_id = :propertyId")
    abstract fun observeConversationFor(
        userId: String,
        propertyId: String,
    ): Flow<ConversationEntity?>

    @Query(
        """
        SELECT c.*, m.id AS message_id, m.sender_id AS message_sender_id, m.body AS message_body,
            m.sent_at AS message_sent_at, m.read_at AS message_read_at, m.status AS message_status,
            (SELECT COUNT(*) FROM messages u
                WHERE u.conversation_id = c.id AND u.recipient_id = c.user_id AND u.read_at IS NULL) AS unread_count
        FROM conversations c
        JOIN messages m ON m.id = (
            SELECT l.id FROM messages l WHERE l.conversation_id = c.id ORDER BY l.sent_at DESC, l.rowid DESC LIMIT 1
        )
        WHERE c.user_id = :userId
        ORDER BY m.sent_at DESC, m.rowid DESC
        """,
    )
    abstract fun observeSummaries(userId: String): Flow<List<ConversationSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMessage(message: MessageEntity)

    @Query(
        """
        SELECT m.* FROM messages m JOIN conversations c ON c.id = m.conversation_id
        WHERE m.conversation_id = :conversationId AND c.user_id = :userId
        ORDER BY m.sent_at ASC, m.rowid ASC
        """,
    )
    abstract fun observeMessages(
        userId: String,
        conversationId: String,
    ): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id
        WHERE c.user_id = :userId AND m.recipient_id = :userId AND m.read_at IS NULL
        """,
    )
    abstract fun observeUnreadCount(userId: String): Flow<Int>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    abstract suspend fun updateStatus(
        messageId: String,
        status: String,
    )

    /** The agent read the thread: the user's messages there become read (and therefore delivered). */
    @Query(
        """
        UPDATE messages SET read_at = :readAtMillis, status = 'sent'
        WHERE conversation_id = :conversationId AND sender_id = :userId AND read_at IS NULL
        """,
    )
    abstract suspend fun markReadByAgent(
        conversationId: String,
        userId: String,
        readAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE messages SET read_at = :readAtMillis
        WHERE conversation_id = :conversationId AND recipient_id = :readerId AND read_at IS NULL
        """,
    )
    abstract suspend fun markConversationRead(
        conversationId: String,
        readerId: String,
        readAtMillis: Long,
    ): Int

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversation_id = :conversationId AND sender_id LIKE 'agent:%')")
    abstract suspend fun hasAgentMessage(conversationId: String): Boolean
}
