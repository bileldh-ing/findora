package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/** The notifications center. Every query is scoped to a user id. */
@Dao
abstract class NotificationDao {
    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
    abstract fun observe(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL")
    abstract fun observeUnreadCount(userId: String): Flow<Int>

    @Insert
    protected abstract suspend fun insert(notification: NotificationEntity): Long

    @Query(
        """
        DELETE FROM notifications
        WHERE user_id = :userId AND kind = :kind AND conversation_id = :conversationId AND read_at IS NULL
        """,
    )
    protected abstract suspend fun deleteUnreadFor(
        userId: String,
        kind: String,
        conversationId: String,
    )

    /** Inserts [notification]; for a thread, its previous unread notification of the same kind is replaced. */
    @Transaction
    open suspend fun add(notification: NotificationEntity): Long {
        notification.conversationId?.let { deleteUnreadFor(notification.userId, notification.kind, it) }
        return insert(notification)
    }

    @Query("UPDATE notifications SET read_at = :readAtMillis WHERE id = :id AND user_id = :userId AND read_at IS NULL")
    abstract suspend fun markRead(
        userId: String,
        id: Long,
        readAtMillis: Long,
    ): Int

    @Query("UPDATE notifications SET read_at = :readAtMillis WHERE user_id = :userId AND read_at IS NULL")
    abstract suspend fun markAllRead(
        userId: String,
        readAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE notifications SET read_at = :readAtMillis
        WHERE user_id = :userId AND conversation_id = :conversationId AND read_at IS NULL
        """,
    )
    abstract suspend fun markConversationRead(
        userId: String,
        conversationId: String,
        readAtMillis: Long,
    ): Int

    @Query("DELETE FROM notifications WHERE id = :id AND user_id = :userId")
    abstract suspend fun delete(
        userId: String,
        id: Long,
    ): Int
}
