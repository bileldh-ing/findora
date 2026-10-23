package com.findora.app.core.data.local

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import com.findora.app.core.data.local.entity.UserEntity
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class UserDataDaoTest {
    private lateinit var db: FindoraDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun favoritesAreIsolatedPerUserAndToggle() =
        runTest {
            val dao = db.favoriteDao()
            db.propertyDao().upsertAll(listOf(seedEntity(0, id = "p1"), seedEntity(1, id = "p2")))
            assertTrue(dao.toggle("alice", "p1", nowMillis = 1))
            assertTrue(dao.toggle("alice", "p2", nowMillis = 2))
            assertTrue(dao.toggle("bob", "p1", nowMillis = 3))
            dao.observeIds("alice").test {
                assertEquals(listOf("p2", "p1"), awaitItem())
                assertFalse(dao.toggle("alice", "p2", nowMillis = 4))
                assertEquals(listOf("p1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, dao.count("bob"))
            assertTrue(dao.isFavorite("bob", "p1"))
            assertFalse(dao.isFavorite("bob", "p2"))
        }

    @Test
    fun favoritePropertiesJoinOnlyCachedListings() =
        runTest {
            val dao = db.favoriteDao()
            db.propertyDao().upsertAll(listOf(seedEntity(0, id = "p1")))
            dao.toggle("alice", "p1", 1)
            dao.toggle("alice", "gone", 2)
            dao.observeProperties("alice").test {
                assertEquals(listOf("p1"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun usersHaveUniqueEmails() =
        runTest {
            val dao = db.userDao()
            val user = UserEntity("u1", "maya@example.com", "Maya", "hash", "salt", 1)
            dao.insert(user)
            try {
                dao.insert(user.copy(id = "u2"))
                fail("duplicate e-mail must be rejected")
            } catch (expected: SQLiteConstraintException) {
                assertTrue(expected.message.orEmpty().isNotEmpty())
            }
            assertEquals("u1", dao.findByEmail("maya@example.com")?.id)
            assertNull(dao.findByEmail("nobody@example.com"))
            assertEquals(1, dao.updatePassword("u1", "new-hash", "new-salt"))
            dao.observeById("u1").test {
                assertEquals("new-hash", awaitItem()?.passwordHash)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
