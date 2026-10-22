package com.findora.app.core.data.remote

import com.findora.app.core.domain.model.DataError
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedListings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class FakeRemoteDataSourceTest {
    private val config = FakeRemoteConfig()
    private val remote = FakeRemoteDataSource(fileSeedReader(), config, Random(7))

    @Test
    fun latencyStaysWithinTheConfiguredWindow() =
        runTest {
            repeat(20) {
                val before = currentTime
                remote.fetchRegions()
                assertTrue(currentTime - before in 150..600)
            }
        }

    @Test
    fun fixedLatencyIsHonoured() =
        runTest {
            config.latencyMillis = 250L..250L
            val before = currentTime
            remote.fetchListing(seedListings.first().id)
            assertEquals(250, currentTime - before)
        }

    @Test
    fun pagesThroughARegionNewestFirst() =
        runTest {
            val expected = seedListings.filter { it.region == "kerala" }.sortedByDescending { it.createdAt }
            val first = remote.fetchListings("kerala", page = 0, pageSize = 15)
            assertEquals(expected.size, first.totalCount)
            assertEquals(expected.take(15).map { it.id }, first.items.map { it.id })
            assertEquals(1, first.nextPage)
            val ids = mutableListOf<String>()
            var page: Int? = 0
            while (page != null) {
                val result = remote.fetchListings("kerala", page, 15)
                ids += result.items.map { it.id }
                page = result.nextPage
            }
            assertEquals(expected.map { it.id }, ids)
            val beyond = remote.fetchListings("kerala", page = 99, pageSize = 15)
            assertTrue(beyond.items.isEmpty())
            assertNull(beyond.nextPage)
        }

    @Test
    fun unknownRegionReturnsAnEmptyPageAndUnknownListingIsNotFound() =
        runTest {
            assertEquals(0, remote.fetchListings("atlantis", 0, 10).totalCount)
            try {
                remote.fetchListing("missing")
                fail("expected NotFound")
            } catch (error: RemoteException) {
                assertEquals(DataError.NotFound, error.error)
            }
        }

    @Test
    fun failNextInjectsAFixedNumberOfFailures() =
        runTest {
            config.failNext(count = 2, error = DataError.Server)
            repeat(2) {
                try {
                    remote.fetchRegions()
                    fail("expected an injected failure")
                } catch (error: RemoteException) {
                    assertEquals(DataError.Server, error.error)
                }
            }
            assertEquals(4, remote.fetchRegions().size)
        }

    @Test
    fun alwaysModeFailsEveryCallUntilReset() =
        runTest {
            config.failureMode = FailureMode.Always(DataError.Timeout)
            repeat(3) {
                val error = runCatching { remote.fetchRegions() }.exceptionOrNull()
                assertEquals(DataError.Timeout, (error as RemoteException).error)
            }
            config.reset()
            assertEquals(4, remote.fetchRegions().size)
        }

    @Test
    fun randomModeFailsAtRoughlyTheConfiguredRate() =
        runTest {
            config.failureMode = FailureMode.Random(rate = 0.3)
            val failures = (1..200).count { runCatching { remote.fetchRegions() }.isFailure }
            assertTrue("failures=$failures", failures in 35..85)
        }

    @Test
    fun invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FailureMode.Random(rate = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { config.failNext(count = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { remote.fetchListings("kerala", page = -1, pageSize = 10) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { remote.fetchListings("kerala", page = 0, pageSize = 0) }
        }
    }
}
