package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.FavoriteDao
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFavoritesRepository
    @Inject
    constructor(
        private val dao: FavoriteDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FavoritesRepository {
        override fun observeFavoriteIds(userId: String): Flow<Set<String>> =
            dao
                .observeIds(userId)
                .map { it.toSet() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeFavorites(userId: String): Flow<List<Property>> =
            dao
                .observeProperties(userId)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override suspend fun toggle(
            userId: String,
            propertyId: String,
        ): Boolean = withContext(ioDispatcher) { dao.toggle(userId, propertyId, clock.now().toEpochMilli()) }
    }
