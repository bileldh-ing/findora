package com.findora.app.core.domain.owner

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import java.time.Instant

/**
 * A photo imported into app-private storage: [path] is an absolute file path, already downscaled and compressed.
 * [dominantColorArgb] is painted while the file loads.
 */
@Immutable
data class ListingPhoto(
    val path: String,
    val width: Int,
    val height: Int,
    val dominantColorArgb: Long,
)

/**
 * The owner's working copy of a listing, filled in by the wizard step by step. Every field may still be empty: a
 * draft is saved as it is typed and only checked by [ListingValidator] when a step is left or the listing published.
 * The first photo is the cover.
 */
@Immutable
data class ListingDraft(
    val type: PropertyType? = null,
    val title: String = "",
    val description: String = "",
    val regionId: String? = null,
    val locality: String = "",
    val pin: GeoPoint? = null,
    val bedrooms: Int? = null,
    val bathrooms: Int? = null,
    val areaSqm: Int? = null,
    val floor: Int? = null,
    val furnished: Boolean = false,
    val petFriendly: Boolean = false,
    val parking: Boolean = false,
    val amenities: Set<Amenity> = emptySet(),
    val photos: List<ListingPhoto> = emptyList(),
    val rent: Long? = null,
    val currency: Currency? = null,
    val deposit: Long? = null,
) {
    val cover: ListingPhoto? get() = photos.firstOrNull()
}

/** Where a listing stands. Only [Published] listings are visible to other users. */
enum class ListingStatus(
    val id: String,
) {
    /** Never published, or taken back to draft (unpublished). */
    Draft("draft"),

    /** Live in Home, search, the map and detail pages. */
    Published("published"),

    /** Temporarily hidden; Resume publishes it again. */
    Paused("paused"),
    ;

    companion object {
        fun fromId(id: String): ListingStatus = entries.firstOrNull { it.id == id } ?: Draft
    }
}

/**
 * A listing created by a signed-in owner. [draft] is what the wizard edits; a published listing is the snapshot taken
 * at the last publish, so edits to a live listing stay private until "Publish changes".
 */
@Immutable
data class OwnerListing(
    val id: String,
    val ownerId: String,
    val status: ListingStatus,
    val draft: ListingDraft,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
    /** True while a published (or paused) listing has edits that are not live yet. */
    val hasUnpublishedChanges: Boolean = false,
) {
    val isLive: Boolean get() = status == ListingStatus.Published

    companion object {
        /** Ids of owner listings start with this prefix, so they never collide with market listings. */
        const val ID_PREFIX = "own-"
    }
}

/** The wizard's steps, in order. */
enum class WizardStep {
    Basics,
    Location,
    Details,
    Amenities,
    Photos,
    Price,
    Preview,
    ;

    val number: Int get() = ordinal + 1

    fun next(): WizardStep? = entries.getOrNull(ordinal + 1)

    fun previous(): WizardStep? = entries.getOrNull(ordinal - 1)
}
