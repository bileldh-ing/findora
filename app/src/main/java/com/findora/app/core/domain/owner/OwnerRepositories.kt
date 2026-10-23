package com.findora.app.core.domain.owner

import com.findora.app.core.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Result of publishing (or resuming) a listing. */
sealed interface PublishResult {
    data class Published(
        val listingId: String,
    ) : PublishResult

    /** The draft is incomplete; [step] is the first step with an error. */
    data class Invalid(
        val step: WizardStep,
    ) : PublishResult

    /** No such listing for this owner. */
    data object NotFound : PublishResult
}

/** A chat thread another user opened about one of the owner's listings. */
data class OwnerEnquiry(
    val conversationId: String,
    val propertyId: String,
    val listingTitle: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
)

/**
 * The owner's listings, stored on the device. Every read and write is scoped to [ownerId]: a listing that belongs to
 * someone else reads as absent and is never changed. Published listings are written into the shared listings cache,
 * so Home, search, the map and detail pages show them to every user of the device.
 */
@Suppress("TooManyFunctions") // The listing lifecycle (create, save, publish, pause, unpublish, delete) and its stats.
interface OwnerListingRepository {
    fun observeListings(ownerId: String): Flow<List<OwnerListing>>

    fun observeListing(
        ownerId: String,
        listingId: String,
    ): Flow<OwnerListing?>

    /** Creates an empty draft and returns it. */
    suspend fun createDraft(ownerId: String): OwnerListing

    /** Autosave: replaces the working copy (never the live listing). Returns false when the listing is not theirs. */
    suspend fun saveDraft(
        ownerId: String,
        listingId: String,
        draft: ListingDraft,
    ): Boolean

    /** Validates the working copy and makes it live (first publish, "Publish changes" or Resume). */
    suspend fun publish(
        owner: UserAccount,
        listingId: String,
    ): PublishResult

    /** Hides a published listing; it keeps its stats and can be resumed. */
    suspend fun pause(
        ownerId: String,
        listingId: String,
    ): Boolean

    /** Takes a listing off the market and back to draft. */
    suspend fun unpublish(
        ownerId: String,
        listingId: String,
    ): Boolean

    /** Deletes the listing, its live copy and its photo files. */
    suspend fun delete(
        ownerId: String,
        listingId: String,
    ): Boolean

    /** Deletes every listing of [ownerId] and their photos (account deletion). */
    suspend fun deleteAllFor(ownerId: String)

    /** Views, saves, chats and viewing requests by other users on [listingIds]. */
    fun observeSignals(
        ownerId: String,
        listingIds: List<String>,
    ): Flow<OwnerSignals>

    /** Chat threads other users opened about the owner's listings, newest first. */
    fun observeEnquiries(ownerId: String): Flow<List<OwnerEnquiry>>
}

/**
 * Imports photos picked with the system Photo Picker into app-private storage: decoded, downscaled so the long edge is
 * at most [PhotoSizing.MAX_EDGE] pixels, re-encoded as JPEG and stored under the listing. Nothing leaves the device.
 */
interface ListingPhotoStore {
    /** Returns null when the picked item cannot be read or decoded. */
    suspend fun import(
        listingId: String,
        sourceUri: String,
    ): ListingPhoto?

    suspend fun delete(photo: ListingPhoto)

    /** Removes the listing's photo files that are not in [keep] (photos removed from a published listing). */
    suspend fun retainOnly(
        listingId: String,
        keep: List<ListingPhoto>,
    )

    suspend fun deleteAll(listingId: String)
}

/** Where owner mode is switched on (a per-device preference that only applies to signed-in accounts). */
interface OwnerModeRepository {
    val ownerMode: Flow<Boolean>

    suspend fun setOwnerMode(enabled: Boolean)
}
