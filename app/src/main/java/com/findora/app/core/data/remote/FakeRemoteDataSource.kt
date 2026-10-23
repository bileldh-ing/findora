package com.findora.app.core.data.remote

import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.data.seed.RegionDto
import com.findora.app.core.data.seed.SeedReader
import com.findora.app.core.domain.model.DataError
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Backend stand-in that serves the bundled seed with realistic latency (150 to 600 ms by default)
 * and switchable failures, so the whole app works with no server. [random] is injected so tests
 * get deterministic latency and failures; delays use coroutine time, so tests skip them instantly.
 * With a [market] the listings move with time (new units, weekly offers; see [FakeMarket]); without one the seed
 * is served as is.
 */
@Singleton
class FakeRemoteDataSource
    @Inject
    constructor(
        private val seedReader: SeedReader,
        private val config: FakeRemoteConfig,
        private val random: Random,
        private val market: FakeMarket? = null,
    ) : RemoteDataSource {
        private val mutex = Mutex()
        private var listings: List<ListingDto>? = null
        private var regions: List<RegionDto>? = null

        override suspend fun fetchRegions(): List<RegionDto> =
            simulate {
                regions ?: seedReader.readRegions().regions.also { regions = it }
            }

        override suspend fun fetchListings(
            regionId: String,
            page: Int,
            pageSize: Int,
        ): ListingPage {
            require(page >= 0) { "page must not be negative" }
            require(pageSize > 0) { "pageSize must be positive" }
            return simulate {
                val all = allListings().filter { it.region == regionId }.sortedByDescending(ListingDto::createdAt)
                val from = page * pageSize
                val items = if (from >= all.size) emptyList() else all.subList(from, minOf(from + pageSize, all.size))
                ListingPage(
                    items = items,
                    page = page,
                    totalCount = all.size,
                    nextPage = if (from + pageSize < all.size) page + 1 else null,
                )
            }
        }

        override suspend fun fetchListing(id: String): ListingDto =
            simulate {
                allListings().firstOrNull { it.id == id }
                    ?: throw RemoteException(DataError.NotFound, "Listing $id not found")
            }

        private fun allListings(): List<ListingDto> {
            val seed = listings ?: seedReader.readListings().listings.also { listings = it }
            return market?.apply(seed) ?: seed
        }

        private suspend fun <T> simulate(block: () -> T): T {
            val range = config.latencyMillis
            delay(if (range.first >= range.last) range.first else random.nextLong(range.first, range.last + 1))
            failureFor()?.let { throw RemoteException(it, "Simulated ${it.name.lowercase()} failure") }
            return mutex.withLock { block() }
        }

        private fun failureFor(): DataError? {
            config.takePendingFailure()?.let { return it }
            return when (val mode = config.failureMode) {
                FailureMode.None -> null
                is FailureMode.Always -> mode.error
                is FailureMode.Random -> if (random.nextDouble() < mode.rate) mode.error else null
            }
        }
    }
