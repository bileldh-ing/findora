package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.NotificationDao
import com.findora.app.core.data.local.entity.NotificationEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.notification.AppNotification
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomNotificationRepository
    @Inject
    constructor(
        private val dao: NotificationDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : NotificationRepository {
        override fun observeNotifications(userId: String): Flow<List<AppNotification>> =
            dao
                .observe(userId)
                // A kind written by a newer app version is skipped rather than shown wrongly.
                .map { rows -> rows.mapNotNull { it.toDomain() } }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeUnreadCount(userId: String): Flow<Int> =
            dao.observeUnreadCount(userId).distinctUntilChanged().flowOn(ioDispatcher)

        override suspend fun add(
            userId: String,
            notification: NewNotification,
        ) {
            withContext(ioDispatcher) {
                dao.add(
                    NotificationEntity(
                        userId = userId,
                        kind = notification.kind.id,
                        subject = notification.subject,
                        preview = notification.preview,
                        propertyId = notification.propertyId,
                        conversationId = notification.conversationId,
                        eventAtMillis = notification.eventAt?.toEpochMilli(),
                        createdAtMillis = clock.now().toEpochMilli(),
                        readAtMillis = null,
                        savedSearchId = notification.savedSearchId,
                        itemCount = notification.count,
                    ),
                )
            }
        }

        override suspend fun markRead(
            userId: String,
            notificationId: Long,
        ) {
            withContext(ioDispatcher) { dao.markRead(userId, notificationId, clock.now().toEpochMilli()) }
        }

        override suspend fun markAllRead(userId: String) {
            withContext(ioDispatcher) { dao.markAllRead(userId, clock.now().toEpochMilli()) }
        }

        override suspend fun markConversationRead(
            userId: String,
            conversationId: String,
        ) {
            withContext(ioDispatcher) {
                dao.markConversationRead(userId, conversationId, clock.now().toEpochMilli())
            }
        }

        override suspend fun delete(
            userId: String,
            notificationId: Long,
        ) {
            withContext(ioDispatcher) { dao.delete(userId, notificationId) }
        }
    }

private fun NotificationEntity.toDomain(): AppNotification? {
    val kind = NotificationKind.fromIdOrNull(kind) ?: return null
    return AppNotification(
        id = id,
        kind = kind,
        subject = subject,
        preview = preview,
        propertyId = propertyId,
        conversationId = conversationId,
        eventAt = eventAtMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        readAt = readAtMillis?.let(Instant::ofEpochMilli),
        savedSearchId = savedSearchId,
        count = itemCount,
    )
}
