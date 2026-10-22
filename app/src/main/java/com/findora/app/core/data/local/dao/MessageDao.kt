package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.findora.app.core.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY sent_at ASC, id ASC")
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE recipient_id = :userId AND read_at IS NULL")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE recipient_id = :userId AND read_at IS NULL ORDER BY sent_at DESC, id DESC")
    fun observeUnread(userId: String): Flow<List<MessageEntity>>

    @Query(
        """
        UPDATE messages SET read_at = :readAtMillis
        WHERE conversation_id = :conversationId AND recipient_id = :readerId AND read_at IS NULL
        """,
    )
    suspend fun markConversationRead(
        conversationId: String,
        readerId: String,
        readAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE messages SET read_at = :readAtMillis
        WHERE id = :messageId AND recipient_id = :readerId AND read_at IS NULL
        """,
    )
    suspend fun markRead(
        messageId: String,
        readerId: String,
        readAtMillis: Long,
    ): Int
}
