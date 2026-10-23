package com.findora.app.core.data.owner

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.PublishResult
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.testing.FakeListingPhotoStore
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.completeDraft
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RoomOwnerListingRepositoryTest {
    private lateinit var db: FindoraDatabase
    private lateinit var repo: RoomOwnerListingRepository
    private val clock = MutableClock()
    private val photos = FakeListingPhotoStore()
    private val owner = UserAccount("owner-1", "ada@findora.app", "Ada Lovelace")
    private var ids = 0

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo =
            RoomOwnerListingRepository(
                dao = db.ownerListingDao(),
                regions = SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined),
                photos = photos,
                clock = clock,
                ids = OwnerListingIds { "own-${++ids}" },
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun draftsAreCreatedSavedAndScopedToTheirOwner() =
        runTest {
            val created = repo.createDraft(owner.id)
            assertEquals("own-1", created.id)
            assertEquals(ListingStatus.Draft, created.status)
            clock.advance(Duration.ofMinutes(1))
            assertTrue(repo.saveDraft(owner.id, created.id, completeDraft()))
            val saved = repo.observeListing(owner.id, created.id).first()
            assertEquals(completeDraft(), saved?.draft)
            assertTrue(saved!!.updatedAt.isAfter(created.updatedAt))
            // Someone else can neither read nor change it.
            assertNull(repo.observeListing("intruder", created.id).first())
            assertFalse(repo.saveDraft("intruder", created.id, completeDraft().copy(title = "Hijacked listing")))
            assertFalse(repo.delete("intruder", created.id))
            assertEquals(listOf(created.id), repo.observeListings(owner.id).first().map { it.id })
            assertTrue(repo.observeListings("intruder").first().isEmpty())
        }

    @Test
    fun publishingWritesALiveListingThatSurvivesAMarketRefresh() =
        runTest {
            val id = repo.createDraft(owner.id).id
            assertEquals(PublishResult.Invalid(WizardStep.Basics), repo.publish(owner, id))
            repo.saveDraft(owner.id, id, completeDraft())
            assertEquals(PublishResult.Published(id), repo.publish(owner, id))
            val live = db.propertyDao().findById(id)
            assertNotNull(live)
            assertEquals(owner.id, live!!.ownerId)
            assertEquals(50_000L, live.depositAmount)
            assertEquals("kerala", live.regionId)
            // A refresh of the region replaces market listings only.
            db.propertyDao().replaceRegion("kerala", listOf(seedEntity(0, id = "market-1", regionId = "kerala")))
            assertNotNull(db.propertyDao().findById(id))
            db.propertyDao().replaceRegion("kerala", emptyList())
            assertNotNull(db.propertyDao().findById(id))
            assertNull(db.propertyDao().findById("market-1"))
            val listing = repo.observeListing(owner.id, id).first()!!
            assertEquals(ListingStatus.Published, listing.status)
            assertFalse(listing.hasUnpublishedChanges)
        }

    @Test
    fun editsToALiveListingStayPrivateUntilPublishedAgain() =
        runTest {
            val id = repo.createDraft(owner.id).id
            repo.saveDraft(owner.id, id, completeDraft())
            repo.publish(owner, id)
            val firstPublished = repo.observeListing(owner.id, id).first()!!.publishedAt
            clock.advance(Duration.ofDays(1))
            repo.saveDraft(owner.id, id, completeDraft().copy(rent = 30_000))
            assertTrue(repo.observeListing(owner.id, id).first()!!.hasUnpublishedChanges)
            assertEquals(25_000L, db.propertyDao().findById(id)!!.rentAmount)
            repo.publish(owner, id)
            assertEquals(30_000L, db.propertyDao().findById(id)!!.rentAmount)
            val listing = repo.observeListing(owner.id, id).first()!!
            assertFalse(listing.hasUnpublishedChanges)
            // Republishing keeps the first publication time (the listing does not jump to the top of "newest").
            assertEquals(firstPublished, listing.publishedAt)
        }

    @Test
    fun pauseUnpublishAndDeleteTakeTheListingOffTheMarket() =
        runTest {
            val id = repo.createDraft(owner.id).id
            repo.saveDraft(owner.id, id, completeDraft())
            assertFalse(repo.pause(owner.id, id))
            repo.publish(owner, id)
            assertTrue(repo.pause(owner.id, id))
            assertNull(db.propertyDao().findById(id))
            assertEquals(ListingStatus.Paused, repo.observeListing(owner.id, id).first()!!.status)
            assertEquals(PublishResult.Published(id), repo.publish(owner, id))
            assertNotNull(db.propertyDao().findById(id))
            assertTrue(repo.unpublish(owner.id, id))
            assertNull(db.propertyDao().findById(id))
            assertEquals(ListingStatus.Draft, repo.observeListing(owner.id, id).first()!!.status)
            assertFalse(repo.unpublish(owner.id, id))
            repo.publish(owner, id)
            assertTrue(repo.delete(owner.id, id))
            assertNull(db.propertyDao().findById(id))
            assertNull(repo.observeListing(owner.id, id).first())
        }

    @Test
    fun deleteAllForRemovesEveryListingOfTheOwnerOnly() =
        runTest {
            val mine = repo.createDraft(owner.id).id
            val theirs = repo.createDraft("someone").id
            repo.deleteAllFor(owner.id)
            assertNull(repo.observeListing(owner.id, mine).first())
            assertNotNull(repo.observeListing("someone", theirs).first())
        }

    @Test
    fun signalsAndEnquiriesComeFromOtherUsersActivity() =
        runTest {
            val id = repo.createDraft(owner.id).id
            repo.saveDraft(owner.id, id, completeDraft())
            repo.publish(owner, id)
            val sql = db.openHelper.writableDatabase
            sql.execSQL(
                "INSERT INTO interaction_events (user_id, property_id, type, occurred_at, dwell_millis) VALUES " +
                    "('u2', '$id', 'viewed', 1000, 0), ('owner-1', '$id', 'viewed', 2000, 0), " +
                    "('u2', '$id', 'favorited', 3000, 0), ('u3', 'market-1', 'viewed', 4000, 0)",
            )
            sql.execSQL("INSERT INTO favorites (user_id, property_id, created_at) VALUES ('u2', '$id', 5000)")
            sql.execSQL(
                "INSERT INTO conversations (id, user_id, property_id, property_title, agent_id, agent_name, " +
                    "agent_initials, agent_color, created_at) VALUES " +
                    "('c1', 'u2', '$id', 'Sunny two-bedroom flat', 'owner-owner-1', 'Ada Lovelace', 'AL', 0, 6000)",
            )
            sql.execSQL(
                "INSERT INTO messages (id, conversation_id, property_id, sender_id, recipient_id, body, sent_at, " +
                    "read_at, status) VALUES ('m1', 'c1', '$id', 'u2', 'agent:owner-owner-1', 'Is it free in May?', 7000, NULL, 'sent')",
            )
            sql.execSQL(
                "INSERT INTO viewings (user_id, property_id, start_at, duration_minutes, status, note, created_at) " +
                    "VALUES ('u3', '$id', 9000, 30, 'booked', '', 8000)",
            )
            repo.observeSignals(owner.id, listOf(id)).test {
                val signals = awaitItem()
                assertEquals(listOf("u2"), signals.views.map { it.userId })
                assertEquals(listOf("u2"), signals.saves.map { it.userId })
                assertEquals(listOf("u2"), signals.chats.map { it.userId })
                assertEquals(listOf("u3"), signals.viewingRequests.map { it.userId })
                cancelAndIgnoreRemainingEvents()
            }
            val enquiries = repo.observeEnquiries(owner.id).first()
            assertEquals(1, enquiries.size)
            assertEquals("Is it free in May?", enquiries.single().lastMessage)
            assertEquals("Sunny two-bedroom flat", enquiries.single().listingTitle)
            assertTrue(repo.observeEnquiries("someone").first().isEmpty())
            assertTrue(
                repo
                    .observeSignals(owner.id, emptyList())
                    .first()
                    .views
                    .isEmpty(),
            )
        }

    @Test
    fun deletingTheAccountRemovesItsListingsAndTheirLiveCopies() =
        runTest {
            val id = repo.createDraft(owner.id).id
            repo.saveDraft(owner.id, id, completeDraft())
            repo.publish(owner, id)
            db.accountDao().deleteAccount(owner.id, owner.email)
            assertNull(db.propertyDao().findById(id))
            assertTrue(repo.observeListings(owner.id).first().isEmpty())
        }
}
