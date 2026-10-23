package com.findora.app.core.data.repository

import android.app.Application
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.dao.BookingOutcome
import com.findora.app.core.data.local.entity.ViewingEntity
import com.findora.app.core.domain.model.BookingError
import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RoomViewingRepositoryTest {
    private lateinit var db: FindoraDatabase
    private lateinit var repository: RoomViewingRepository
    private val now = Instant.parse("2026-10-26T09:10:00Z")
    private val tomorrow10 = Instant.parse("2026-10-27T09:00:00Z")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = RoomViewingRepository(db.viewingDao(), { now }, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun book(
        user: String,
        property: String,
        start: Instant = tomorrow10,
        note: String = "",
    ) = repository.schedule(user, property, start, 30, note)

    @Test
    fun viewingsAreIsolatedPerUser() =
        runTest {
            val booked = book("ada", "p1", note = "  Ring twice  ") as BookingResult.Booked
            book("bob", "p2", tomorrow10.plus(Duration.ofHours(2)))

            val adas = repository.observeViewings("ada").first()
            assertEquals(listOf(booked.viewing), adas)
            assertEquals("Ring twice", adas.single().note)
            assertEquals(now, adas.single().createdAt)
            assertEquals(listOf("p2"), repository.observeViewings("bob").first().map { it.propertyId })
            assertTrue(repository.observeViewings("guest").first().isEmpty())

            // Another user cannot cancel Ada's viewing.
            assertFalse(repository.cancel("bob", booked.viewing.id))
            assertEquals(1, repository.observeViewings("ada").first().size)
        }

    @Test
    fun theSameListingSlotCannotBeBookedTwiceByAnyone() =
        runTest {
            assertTrue(book("ada", "p1") is BookingResult.Booked)
            assertEquals(BookingResult.Rejected(BookingError.SlotTaken), book("bob", "p1"))
            assertEquals(BookingResult.Rejected(BookingError.SlotTaken), book("ada", "p1"))
            // An overlapping start at the listing is taken as well.
            assertEquals(
                BookingResult.Rejected(BookingError.SlotTaken),
                book("bob", "p1", tomorrow10.plus(Duration.ofMinutes(15))),
            )
        }

    @Test
    fun aUserCannotBeInTwoViewingsAtOnce() =
        runTest {
            assertTrue(book("ada", "p1") is BookingResult.Booked)
            assertEquals(BookingResult.Rejected(BookingError.Overlaps), book("ada", "p2"))
            // Back to back is fine (half-open ranges).
            assertTrue(book("ada", "p2", tomorrow10.plus(Duration.ofMinutes(30))) is BookingResult.Booked)
        }

    @Test
    fun pastSlotsAreRejected() =
        runTest {
            assertEquals(BookingResult.Rejected(BookingError.InThePast), book("ada", "p1", now))
            assertEquals(BookingResult.Rejected(BookingError.InThePast), book("ada", "p1", now.minusSeconds(60)))
        }

    @Test
    fun cancellingFreesTheSlot() =
        runTest {
            val first = book("ada", "p1") as BookingResult.Booked
            assertEquals(1, repository.observePropertyBookings("p1", now).first().size)
            assertTrue(repository.cancel("ada", first.viewing.id))
            assertNull(repository.viewing(first.viewing.id))
            assertTrue(repository.observePropertyBookings("p1", now).first().isEmpty())
            val again = book("bob", "p1") as BookingResult.Booked
            assertEquals(again.viewing, repository.viewing(again.viewing.id))
            assertFalse(repository.cancel("ada", first.viewing.id))
        }

    @Test
    fun propertyBookingsLeaveOutViewingsThatAlreadyEnded() =
        runTest {
            db.viewingDao().insert(
                ViewingEntity(
                    userId = "ada",
                    propertyId = "p1",
                    startAtMillis = now.minus(Duration.ofHours(2)).toEpochMilli(),
                    durationMinutes = 30,
                    status = "booked",
                    note = "",
                    createdAtMillis = 0,
                ),
            )
            book("bob", "p1")
            val ranges = repository.observePropertyBookings("p1", now).first()
            assertEquals(listOf(tomorrow10), ranges.map { it.start })
            assertEquals(tomorrow10.plus(Duration.ofMinutes(30)), ranges.single().end)
        }

    @Test
    fun theDaoReportsWhyABookingWasRefused() =
        runTest {
            val dao = db.viewingDao()
            val entity =
                ViewingEntity(
                    userId = "ada",
                    propertyId = "p1",
                    startAtMillis = tomorrow10.toEpochMilli(),
                    durationMinutes = 30,
                    status = "booked",
                    note = "",
                    createdAtMillis = 0,
                )
            assertTrue(dao.bookIfFree(entity) is BookingOutcome.Booked)
            assertEquals(BookingOutcome.SlotTaken, dao.bookIfFree(entity.copy(userId = "bob")))
            assertEquals(BookingOutcome.Overlaps, dao.bookIfFree(entity.copy(propertyId = "p2")))
            assertEquals(1, dao.observeForUser("ada").first().size)
        }
}
