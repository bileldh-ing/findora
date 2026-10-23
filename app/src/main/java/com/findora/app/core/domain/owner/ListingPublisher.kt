package com.findora.app.core.domain.owner

import com.findora.app.core.domain.model.Agent
import com.findora.app.core.domain.model.InitialsAvatar
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.PhotoCredit
import com.findora.app.core.domain.model.PricePoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyImage
import com.findora.app.core.domain.model.UserAccount
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Turns a complete [ListingDraft] into the [Property] every user on the device sees. The owner is the contact
 * ("agent") with an initials avatar and no phone or e-mail (contact happens in the app's chat), photos are local
 * files, and the listing is marked with [Property.ownerId] so the owner sees "Listed by you".
 */
object ListingPublisher {
    /** Agent ids of owner listings start with this prefix (no call or e-mail action is offered for them). */
    const val OWNER_AGENT_PREFIX = "owner-"

    /** Bundled image shown if a listing photo file is missing. */
    const val PHOTO_FALLBACK_ASSET = "owner/photo_fallback.webp"
    private const val RESPONSE_MINUTES = 60
    private val AvatarColours = listOf(0xFF3D5AFEL, 0xFF00897BL, 0xFF8E24AAL, 0xFFEF6C00L, 0xFF5D4037L, 0xFF1565C0L)

    /**
     * Returns null when the draft is not publishable (see [ListingValidator.firstInvalidStep]). [firstPublishedAt] is
     * kept across re-publishes so the listing does not jump to the top of "newest" on every edit.
     */
    fun toProperty(
        listingId: String,
        owner: UserAccount,
        draft: ListingDraft,
        firstPublishedAt: Instant,
    ): Property? {
        if (ListingValidator.firstInvalidStep(draft) != null) return null
        // The validator guarantees every field below is set.
        val rent = requireNotNull(draft.rent)
        val currency = requireNotNull(draft.currency)
        val month = YearMonth.from(firstPublishedAt.atZone(ZoneOffset.UTC))
        return Property(
            id = listingId,
            title = draft.title.trim(),
            type = requireNotNull(draft.type),
            rent = Money(rent, currency),
            bedrooms = draft.bedrooms ?: 0,
            bathrooms = draft.bathrooms ?: 1,
            areaSqm = draft.areaSqm ?: 0,
            floor = draft.floor ?: 0,
            furnished = draft.furnished,
            petFriendly = draft.petFriendly,
            parking = draft.parking,
            amenities = draft.amenities.sortedBy { it.ordinal },
            tags = emptyList(),
            rating = 0.0,
            reviewsCount = 0,
            verified = false,
            location = requireNotNull(draft.pin),
            locality = draft.locality.trim(),
            regionId = draft.regionId.orEmpty(),
            description = draft.description.trim(),
            agent = ownerAgent(owner),
            images = draft.photos.mapIndexed { index, photo -> photo.toImage(owner, draft.title.trim(), index) },
            videoUrl = null,
            panoramaUrl = null,
            createdAt = firstPublishedAt,
            priceHistory = listOf(PricePoint(month, rent)),
            isSampleData = false,
            ownerId = owner.id,
            deposit = draft.deposit?.let { Money(it, currency) },
        )
    }

    fun ownerAgent(owner: UserAccount): Agent =
        Agent(
            id = OWNER_AGENT_PREFIX + owner.id,
            name = owner.displayName,
            phone = "",
            avatar =
                InitialsAvatar(
                    owner.initials,
                    AvatarColours[Math.floorMod(owner.id.hashCode(), AvatarColours.size)],
                ),
            responseTimeMinutes = RESPONSE_MINUTES,
        )

    private fun ListingPhoto.toImage(
        owner: UserAccount,
        title: String,
        index: Int,
    ): PropertyImage =
        PropertyImage(
            url = "file://$path",
            width = width,
            height = height,
            dominantColorArgb = dominantColorArgb,
            altText = "$title (${index + 1})",
            credit =
                PhotoCredit(
                    photographer = owner.displayName,
                    photographerUrl = "",
                    sourceUrl = "",
                    licence = "",
                    licenceUrl = "",
                ),
            offlineAsset = null,
            fallbackAsset = PHOTO_FALLBACK_ASSET,
        )
}
