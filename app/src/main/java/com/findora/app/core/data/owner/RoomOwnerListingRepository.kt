package com.findora.app.core.data.owner

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.ListingSignalRow
import com.findora.app.core.data.local.dao.OwnerListingDao
import com.findora.app.core.data.local.entity.OwnerListingEntity
import com.findora.app.core.data.local.toEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingPhotoStore
import com.findora.app.core.domain.owner.ListingPublisher
import com.findora.app.core.domain.owner.ListingSignal
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.OwnerEnquiry
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.domain.owner.OwnerListingRepository
import com.findora.app.core.domain.owner.OwnerSignals
import com.findora.app.core.domain.owner.PublishResult
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.domain.repository.RegionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Generates owner listing ids ("own-" + a random UUID); replaced by a counter in tests. */
fun interface OwnerListingIds {
    fun next(): String

    companion object {
        val Random = OwnerListingIds { OwnerListing.ID_PREFIX + UUID.randomUUID() }
    }
}

/**
 * Room-backed [OwnerListingRepository]. The working copy and the published snapshot are versioned JSON documents
 * ([OwnerDraftJson]); publishing writes the listing into the shared `properties` cache with its owner id, which the
 * market refresh never replaces or deletes.
 */
@Suppress("TooManyFunctions") // Implements OwnerListingRepository one-to-one.
@Singleton
class RoomOwnerListingRepository
    @Inject
    constructor(
        private val dao: OwnerListingDao,
        private val regions: RegionRepository,
        private val photos: ListingPhotoStore,
        private val clock: AppClock,
        private val ids: OwnerListingIds,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : OwnerListingRepository {
        override fun observeListings(ownerId: String): Flow<List<OwnerListing>> =
            dao
                .observeForOwner(ownerId)
                .distinctUntilChanged()
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override fun observeListing(
            ownerId: String,
            listingId: String,
        ): Flow<OwnerListing?> =
            dao
                .observe(listingId, ownerId)
                .distinctUntilChanged()
                .map { it?.toDomain() }
                .flowOn(ioDispatcher)

        override suspend fun createDraft(ownerId: String): OwnerListing =
            withContext(ioDispatcher) {
                val now = clock.now().toEpochMilli()
                val entity =
                    OwnerListingEntity(
                        id = ids.next(),
                        ownerId = ownerId,
                        status = ListingStatus.Draft.id,
                        draftJson = OwnerDraftJson.encode(ListingDraft()),
                        publishedJson = null,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        publishedAtMillis = null,
                    )
                dao.insert(entity)
                entity.toDomain()
            }

        override suspend fun saveDraft(
            ownerId: String,
            listingId: String,
            draft: ListingDraft,
        ): Boolean =
            withContext(ioDispatcher) {
                val current = dao.find(listingId, ownerId) ?: return@withContext false
                val json = OwnerDraftJson.encode(draft)
                if (json != current.draftJson) {
                    dao.update(current.copy(draftJson = json, updatedAtMillis = clock.now().toEpochMilli()))
                }
                true
            }

        override suspend fun publish(
            owner: UserAccount,
            listingId: String,
        ): PublishResult =
            withContext(ioDispatcher) {
                val current = dao.find(listingId, owner.id) ?: return@withContext PublishResult.NotFound
                val draft = OwnerDraftJson.decode(current.draftJson)
                val bounds = draft.regionId?.let { regions.region(it)?.bounds }
                ListingValidator.firstInvalidStep(draft, bounds)?.let { return@withContext PublishResult.Invalid(it) }
                val now = clock.now()
                val firstPublished = current.publishedAtMillis?.let(Instant::ofEpochMilli) ?: now
                val property =
                    ListingPublisher.toProperty(listingId, owner, draft, firstPublished)
                        ?: return@withContext PublishResult.Invalid(WizardStep.Basics)
                dao.publish(
                    current.copy(
                        status = ListingStatus.Published.id,
                        publishedJson = current.draftJson,
                        updatedAtMillis = now.toEpochMilli(),
                        publishedAtMillis = firstPublished.toEpochMilli(),
                    ),
                    property.toEntity(now.toEpochMilli()),
                )
                // Photos removed while editing are only deleted once the listing no longer shows them.
                photos.retainOnly(listingId, draft.photos)
                PublishResult.Published(listingId)
            }

        override suspend fun pause(
            ownerId: String,
            listingId: String,
        ): Boolean = takeOffMarket(ownerId, listingId, ListingStatus.Paused, from = setOf(ListingStatus.Published))

        override suspend fun unpublish(
            ownerId: String,
            listingId: String,
        ): Boolean =
            takeOffMarket(
                ownerId,
                listingId,
                ListingStatus.Draft,
                from = setOf(ListingStatus.Published, ListingStatus.Paused),
            )

        private suspend fun takeOffMarket(
            ownerId: String,
            listingId: String,
            to: ListingStatus,
            from: Set<ListingStatus>,
        ): Boolean =
            withContext(ioDispatcher) {
                val current = dao.find(listingId, ownerId) ?: return@withContext false
                if (ListingStatus.fromId(current.status) !in from) return@withContext false
                dao.takeOffMarket(current.copy(status = to.id, updatedAtMillis = clock.now().toEpochMilli()))
                true
            }

        override suspend fun delete(
            ownerId: String,
            listingId: String,
        ): Boolean =
            withContext(ioDispatcher) {
                val deleted = dao.delete(listingId, ownerId)
                if (deleted) photos.deleteAll(listingId)
                deleted
            }

        override suspend fun deleteAllFor(ownerId: String) {
            withContext(ioDispatcher) {
                dao.idsForOwner(ownerId).forEach { id -> delete(ownerId, id) }
            }
        }

        override fun observeSignals(
            ownerId: String,
            listingIds: List<String>,
        ): Flow<OwnerSignals> {
            if (listingIds.isEmpty()) return flowOf(OwnerSignals())
            return combine(
                dao.observeViews(ownerId, listingIds),
                dao.observeSaves(ownerId, listingIds),
                dao.observeChats(ownerId, listingIds),
                dao.observeViewingRequests(ownerId, listingIds),
            ) { views, saves, chats, viewings ->
                OwnerSignals(
                    views = views.map { it.toSignal() },
                    saves = saves.map { it.toSignal() },
                    chats = chats.map { it.toSignal() },
                    viewingRequests = viewings.map { it.toSignal() },
                )
            }.distinctUntilChanged().flowOn(ioDispatcher)
        }

        override fun observeEnquiries(ownerId: String): Flow<List<OwnerEnquiry>> =
            dao
                .observeEnquiries(ownerId)
                .distinctUntilChanged()
                .map { rows ->
                    rows.map {
                        OwnerEnquiry(
                            conversationId = it.conversationId,
                            propertyId = it.propertyId,
                            listingTitle = it.propertyTitle,
                            lastMessage = it.lastMessage,
                            lastMessageAt = it.lastMessageAtMillis?.let(Instant::ofEpochMilli),
                        )
                    }
                }.flowOn(ioDispatcher)

        private fun ListingSignalRow.toSignal() = ListingSignal(propertyId, userId, Instant.ofEpochMilli(atMillis))

        private fun OwnerListingEntity.toDomain(): OwnerListing {
            val listingStatus = ListingStatus.fromId(status)
            return OwnerListing(
                id = id,
                ownerId = ownerId,
                status = listingStatus,
                draft = OwnerDraftJson.decode(draftJson),
                createdAt = Instant.ofEpochMilli(createdAtMillis),
                updatedAt = Instant.ofEpochMilli(updatedAtMillis),
                publishedAt = publishedAtMillis?.let(Instant::ofEpochMilli),
                hasUnpublishedChanges =
                    listingStatus != ListingStatus.Draft && publishedJson != null && publishedJson != draftJson,
            )
        }
    }
