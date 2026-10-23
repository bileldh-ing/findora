package com.findora.app.core.testing

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingPhoto
import com.findora.app.core.domain.owner.ListingPhotoStore
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.OwnerEnquiry
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.domain.owner.OwnerListingRepository
import com.findora.app.core.domain.owner.OwnerModeRepository
import com.findora.app.core.domain.owner.OwnerSignals
import com.findora.app.core.domain.owner.PublishResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** A listing photo as the fake store returns it. */
fun testPhoto(
    listingId: String = "own-1",
    index: Int = 0,
): ListingPhoto = ListingPhoto("/photos/$listingId/$index.jpg", 1600, 1200, 0xFF808080L)

/** A draft that passes every step of [ListingValidator] in Kerala (Kochi). */
fun completeDraft(): ListingDraft =
    ListingDraft(
        type = PropertyType.Apartment,
        title = "Sunny two-bedroom flat",
        description = "Bright flat near the metro with a balcony, a modular kitchen and covered parking.",
        regionId = "kerala",
        locality = "Kakkanad",
        pin = GeoPoint(10.0159, 76.3419),
        bedrooms = 2,
        bathrooms = 2,
        areaSqm = 95,
        floor = 3,
        furnished = true,
        parking = true,
        amenities = setOf(Amenity.Balcony, Amenity.Wifi),
        photos = listOf(testPhoto()),
        rent = 25_000,
        currency = Currency.INR,
        deposit = 50_000,
    )

/** In-memory [OwnerListingRepository]: ids "own-1", "own-2"…, publish validates like the real one. */
class FakeOwnerListingRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : OwnerListingRepository {
    val listings = MutableStateFlow<Map<String, OwnerListing>>(emptyMap())
    val signals = MutableStateFlow(OwnerSignals())
    val enquiries = MutableStateFlow<List<OwnerEnquiry>>(emptyList())
    val saved = mutableListOf<ListingDraft>()
    val deletedFor = mutableListOf<String>()
    private var nextId = 1

    override fun observeListings(ownerId: String): Flow<List<OwnerListing>> =
        listings.map { all -> all.values.filter { it.ownerId == ownerId }.sortedByDescending { it.updatedAt } }

    override fun observeListing(
        ownerId: String,
        listingId: String,
    ): Flow<OwnerListing?> = listings.map { it[listingId]?.takeIf { listing -> listing.ownerId == ownerId } }

    override suspend fun createDraft(ownerId: String): OwnerListing {
        val listing =
            OwnerListing("own-${nextId++}", ownerId, ListingStatus.Draft, ListingDraft(), now(), now(), null)
        listings.update { it + (listing.id to listing) }
        return listing
    }

    fun put(listing: OwnerListing) = listings.update { it + (listing.id to listing) }

    private fun owned(
        ownerId: String,
        id: String,
    ) = listings.value[id]?.takeIf { it.ownerId == ownerId }

    override suspend fun saveDraft(
        ownerId: String,
        listingId: String,
        draft: ListingDraft,
    ): Boolean {
        val current = owned(ownerId, listingId) ?: return false
        saved += draft
        put(current.copy(draft = draft, updatedAt = now()))
        return true
    }

    override suspend fun publish(
        owner: UserAccount,
        listingId: String,
    ): PublishResult {
        val current = owned(owner.id, listingId) ?: return PublishResult.NotFound
        val invalidStep = ListingValidator.firstInvalidStep(current.draft)
        if (invalidStep == null) {
            put(current.copy(status = ListingStatus.Published, publishedAt = current.publishedAt ?: now()))
        }
        return invalidStep?.let { PublishResult.Invalid(it) } ?: PublishResult.Published(listingId)
    }

    override suspend fun pause(
        ownerId: String,
        listingId: String,
    ): Boolean = setStatus(ownerId, listingId, ListingStatus.Paused)

    override suspend fun unpublish(
        ownerId: String,
        listingId: String,
    ): Boolean = setStatus(ownerId, listingId, ListingStatus.Draft)

    private fun setStatus(
        ownerId: String,
        listingId: String,
        status: ListingStatus,
    ): Boolean {
        val current = owned(ownerId, listingId) ?: return false
        put(current.copy(status = status))
        return true
    }

    override suspend fun delete(
        ownerId: String,
        listingId: String,
    ): Boolean {
        owned(ownerId, listingId) ?: return false
        listings.update { it - listingId }
        return true
    }

    override suspend fun deleteAllFor(ownerId: String) {
        deletedFor += ownerId
        listings.update { all -> all.filterValues { it.ownerId != ownerId } }
    }

    override fun observeSignals(
        ownerId: String,
        listingIds: List<String>,
    ): Flow<OwnerSignals> = signals

    override fun observeEnquiries(ownerId: String): Flow<List<OwnerEnquiry>> = enquiries
}

/** Imports every URI except those starting with "bad"; records deletions. */
class FakeListingPhotoStore : ListingPhotoStore {
    val imported = mutableListOf<String>()
    val deleted = mutableListOf<ListingPhoto>()
    private var count = 0

    override suspend fun import(
        listingId: String,
        sourceUri: String,
    ): ListingPhoto? {
        if (sourceUri.startsWith("bad")) return null
        imported += sourceUri
        return testPhoto(listingId, count++)
    }

    override suspend fun delete(photo: ListingPhoto) {
        deleted += photo
    }

    override suspend fun retainOnly(
        listingId: String,
        keep: List<ListingPhoto>,
    ) = Unit

    override suspend fun deleteAll(listingId: String) = Unit
}

class FakeOwnerModeRepository : OwnerModeRepository {
    override val ownerMode = MutableStateFlow(false)

    override suspend fun setOwnerMode(enabled: Boolean) {
        ownerMode.value = enabled
    }
}
