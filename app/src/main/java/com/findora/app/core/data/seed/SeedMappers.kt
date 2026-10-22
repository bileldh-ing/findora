package com.findora.app.core.data.seed

import com.findora.app.core.domain.model.Agent
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.InitialsAvatar
import com.findora.app.core.domain.model.Locality
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.PhotoCredit
import com.findora.app.core.domain.model.PricePoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyImage
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import java.time.Instant
import java.time.YearMonth

private const val OPAQUE_ALPHA = 0xFF000000L
private const val HEX_RADIX = 16
private val hexColour = Regex("^#[0-9A-Fa-f]{6}$")

/** "#RRGGBB" to an opaque ARGB long, the format Compose's Color(Long) accepts. */
fun parseHexColour(hex: String): Long {
    require(hexColour.matches(hex)) { "Expected #RRGGBB, got $hex" }
    return OPAQUE_ALPHA or hex.substring(1).toLong(HEX_RADIX)
}

fun ListingDto.toDomain(): Property =
    Property(
        id = id,
        title = title,
        type = PropertyType.fromId(type),
        rent = Money(rentPerMonth, Currency.fromCode(currency)),
        bedrooms = bedrooms,
        bathrooms = bathrooms,
        areaSqm = areaSqm,
        floor = floor,
        furnished = furnished,
        petFriendly = petFriendly,
        parking = parking,
        amenities = amenities.mapNotNull(Amenity::fromIdOrNull),
        tags = tags,
        rating = rating,
        reviewsCount = reviewsCount,
        verified = verified,
        location = GeoPoint(latitude, longitude),
        locality = locality,
        regionId = region,
        description = description,
        agent = agent.toDomain(),
        images = images.map(ImageDto::toDomain),
        videoUrl = videoUrl,
        panoramaUrl = panoramaUrl,
        createdAt = Instant.parse(createdAt),
        priceHistory = priceHistory.map { PricePoint(YearMonth.parse(it.month), it.amount) },
        isSampleData = sampleData,
    )

fun AgentDto.toDomain(): Agent =
    Agent(
        id = id,
        name = name,
        phone = phone,
        avatar = InitialsAvatar(avatar.initials, parseHexColour(avatar.background)),
        responseTimeMinutes = responseTimeMinutes,
    )

fun ImageDto.toDomain(): PropertyImage =
    PropertyImage(
        url = url,
        width = width,
        height = height,
        dominantColorArgb = parseHexColour(dominantColor),
        altText = alt,
        credit = PhotoCredit(photographer, photographerUrl, sourceUrl, licence, licenceUrl),
        offlineAsset = offlineAsset,
        fallbackAsset = fallbackAsset,
    )

fun RegionDto.toDomain(): RegionPack =
    RegionPack(
        id = id,
        name = name,
        countryCode = country,
        currency = Currency.fromCode(currency),
        localeTag = locale,
        isDefault = isDefault,
        center = GeoPoint(center.latitude, center.longitude),
        defaultZoom = defaultZoom,
        bounds = GeoBounds(bounds.south, bounds.west, bounds.north, bounds.east),
        localities = localities.map { Locality(it.name, GeoPoint(it.latitude, it.longitude)) },
    )
