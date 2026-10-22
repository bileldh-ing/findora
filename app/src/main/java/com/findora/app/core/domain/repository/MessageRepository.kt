package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.InboxMessage
import kotlinx.coroutines.flow.Flow

/** Messages addressed to a user; Home shows the unread count on the bell and lists them in its sheet. */
interface MessageRepository {
    fun observeUnread(userId: String): Flow<List<InboxMessage>>

    /** Marks one message read (swiped away in the notifications sheet); other users' messages are untouched. */
    suspend fun markRead(
        userId: String,
        messageId: String,
    )
}
