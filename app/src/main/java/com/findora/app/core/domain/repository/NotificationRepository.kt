package com.findora.app.core.domain.repository

import com.findora.app.core.domain.notification.AppNotification
import com.findora.app.core.domain.notification.NewNotification
import kotlinx.coroutines.flow.Flow

/** The in-app notifications center, per user id. Every call is scoped to its user. */
interface NotificationRepository {
    /** Newest first. */
    fun observeNotifications(userId: String): Flow<List<AppNotification>>

    /** Drives the Home bell badge. */
    fun observeUnreadCount(userId: String): Flow<Int>

    /**
     * Adds a notification. A new message notification replaces the thread's previous unread one, so a chatty
     * agent never floods the list.
     */
    suspend fun add(
        userId: String,
        notification: NewNotification,
    )

    suspend fun markRead(
        userId: String,
        notificationId: Long,
    )

    suspend fun markAllRead(userId: String)

    /** Opening a thread settles its message notifications. */
    suspend fun markConversationRead(
        userId: String,
        conversationId: String,
    )

    suspend fun delete(
        userId: String,
        notificationId: Long,
    )
}
