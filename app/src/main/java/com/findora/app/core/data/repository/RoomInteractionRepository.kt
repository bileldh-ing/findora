package com.findora.app.core.data.repository

import com.findora.app.core.data.local.dao.InteractionEventDao
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.data.local.toEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.repository.InteractionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInteractionRepository
    @Inject
    constructor(
        private val dao: InteractionEventDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : InteractionRepository {
        override suspend fun record(event: InteractionEvent) {
            require(event.dwellMillis >= 0) { "dwellMillis must not be negative" }
            withContext(ioDispatcher) { dao.insert(event.toEntity()) }
        }

        override suspend fun remove(event: InteractionEvent) {
            withContext(ioDispatcher) {
                dao.deleteLatest(event.userId, event.propertyId, event.type.id, event.occurredAt.toEpochMilli())
            }
        }

        override fun observePropertyIds(
            userId: String,
            types: Set<InteractionType>,
        ): Flow<List<String>> =
            dao
                .observePropertyIds(userId, types.map { it.id })
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeRecent(
            userId: String,
            limit: Int,
        ): Flow<List<InteractionEvent>> =
            dao
                .observeRecent(userId, limit)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)
    }
