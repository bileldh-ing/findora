package com.findora.app.core.data.repository

import com.findora.app.core.data.seed.SeedReader
import com.findora.app.core.data.seed.toDomain
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.RegionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Region packs ship with the app (they define currency, locale and map bounds), so they are read locally. */
@Singleton
class SeedRegionRepository
    @Inject
    constructor(
        private val seedReader: SeedReader,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : RegionRepository {
        private val mutex = Mutex()
        private var cached: List<RegionPack>? = null

        override suspend fun regions(): List<RegionPack> =
            mutex.withLock {
                cached ?: withContext(ioDispatcher) {
                    seedReader
                        .readRegions()
                        .regions
                        .map { it.toDomain() }
                        .sortedByDescending(RegionPack::isDefault)
                }.also { cached = it }
            }

        override suspend fun region(id: String): RegionPack? = regions().firstOrNull { it.id == id }
    }
