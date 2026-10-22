package com.findora.app.core.data.repository

import com.findora.app.core.data.local.dao.MessageDao
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.InboxMessage
import com.findora.app.core.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMessageRepository
    @Inject
    constructor(
        private val dao: MessageDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MessageRepository {
        override fun observeUnread(userId: String): Flow<List<InboxMessage>> =
            dao
                .observeUnread(userId)
                .map { rows -> rows.map { InboxMessage(it.id, it.propertyId, it.body, Instant.ofEpochMilli(it.sentAtMillis)) } }
                .flowOn(ioDispatcher)
    }
