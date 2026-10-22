package com.findora.app.core.data.local

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import com.findora.app.core.data.local.entity.InteractionEventEntity
import com.findora.app.core.data.local.entity.SavedSearchEntity
import com.findora.app.core.data.local.entity.ViewingEntity
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ActivityDaoTest {
    private lateinit var db: FindoraDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savedSearchesPerUserWithAlerts() =
        runTest {
            val dao = db.savedSearchDao()
            val id =
                dao.insert(
                    SavedSearchEntity(
                        userId = "u",
                        name = "2 BHK Kakkanad",
                        query = "2 bhk kakkanad",
                        filterJson = "{}",
                        alertsEnabled = true,
                        createdAtMillis = 1,
                        lastNotifiedAtMillis = null,
                    ),
                )
            dao.insert(
                SavedSearchEntity(
                    userId = "u",
                    name = "Studio",
                    query = "studio",
                    filterJson = "{}",
                    alertsEnabled = false,
                    createdAtMillis = 2,
                    lastNotifiedAtMillis = null,
                ),
            )
            dao.insert(
                SavedSearchEntity(
                    userId = "other",
                    name = "Villa",
                    query = "villa",
                    filterJson = "{}",
                    alertsEnabled = true,
                    createdAtMillis = 3,
                    lastNotifiedAtMillis = null,
                ),
            )
            dao.observeForUser("u").test {
                assertEquals(listOf("Studio", "2 BHK Kakkanad"), awaitItem().map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, dao.withAlertsEnabled().size)
            assertEquals(1, dao.setAlertsEnabled(id, false))
            assertEquals(1, dao.markNotified(id, 99))
            assertEquals(1, dao.withAlertsEnabled().size)
            assertEquals(0, dao.delete(id, "other"))
            assertEquals(1, dao.delete(id, "u"))
        }

    @Test
    fun viewingsCannotBeDoubleBooked() =
        runTest {
            val dao = db.viewingDao()
            val slot =
                ViewingEntity(
                    userId = "u",
                    propertyId = "p",
                    startAtMillis = 1_000,
                    durationMinutes = 30,
                    status = "booked",
                    note = "",
                    createdAtMillis = 1,
                )
            val id = dao.insert(slot)
            try {
                dao.insert(slot.copy(userId = "someone-else"))
                fail("the same slot must not be booked twice")
            } catch (expected: SQLiteConstraintException) {
                assertTrue(expected.message.orEmpty().isNotEmpty())
            }
            dao.insert(slot.copy(startAtMillis = 5_000))
            assertEquals(listOf(1_000L, 5_000L), dao.bookedSlots("p", 0, 10_000).sorted())
            dao.observeUpcoming("u", fromMillis = 2_000).test {
                assertEquals(listOf(5_000L), awaitItem().map { it.startAtMillis })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, dao.updateStatus(id, "u", "cancelled"))
            assertEquals(0, dao.updateStatus(id, "intruder", "cancelled"))
            assertEquals(1, dao.delete(id, "u"))
        }

    @Test
    fun recentSearchesDeduplicateAndTrim() =
        runTest {
            val dao = db.recentSearchDao()
            dao.record("u", "Kakkanad", "kakkanad", 1, keep = 3)
            dao.record("u", "Fort Kochi", "fort kochi", 2, keep = 3)
            dao.record("u", "KAKKANAD", "kakkanad", 3, keep = 3)
            dao.record("u", "2 bhk", "2 bhk", 4, keep = 3)
            dao.record("u", "villa", "villa", 5, keep = 3)
            dao.record("other", "paris", "paris", 6, keep = 3)
            dao.observeRecent("u", limit = 10).test {
                assertEquals(listOf("villa", "2 bhk", "KAKKANAD"), awaitItem().map { it.query })
                dao.clear("u")
                assertEquals(emptyList<String>(), awaitItem().map { it.query })
                cancelAndIgnoreRemainingEvents()
            }
            dao.observeRecent("other", limit = 10).test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun interactionEventsCountAndExpire() =
        runTest {
            val dao = db.interactionEventDao()
            dao.insert(
                InteractionEventEntity(
                    userId = "u",
                    propertyId = "p1",
                    type = "viewed",
                    occurredAtMillis = 10,
                    dwellMillis = 4_000,
                ),
            )
            dao.insert(
                InteractionEventEntity(
                    userId = "u",
                    propertyId = "p2",
                    type = "viewed",
                    occurredAtMillis = 20,
                    dwellMillis = 1_000,
                ),
            )
            dao.insert(
                InteractionEventEntity(
                    userId = "u",
                    propertyId = "p1",
                    type = "favorited",
                    occurredAtMillis = 30,
                    dwellMillis = 0,
                ),
            )
            assertEquals(2, dao.countByType("u", "viewed"))
            dao.observeRecent("u", limit = 2).test {
                assertEquals(listOf(30L, 20L), awaitItem().map { it.occurredAtMillis })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, dao.deleteOlderThan(15))
            assertEquals(1, dao.countByType("u", "viewed"))
        }
}
