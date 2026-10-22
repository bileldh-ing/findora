package com.findora.app.core.data.local

import com.findora.app.core.data.local.entity.AgentColumns
import com.findora.app.core.data.local.entity.ImageColumns
import com.findora.app.core.data.local.entity.InteractionEventEntity
import com.findora.app.core.data.local.entity.PricePointColumns
import com.findora.app.core.data.local.entity.PropertyEntity
import com.findora.app.core.data.local.entity.RecentSearchEntity
import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.data.seed.parseHexColour
import com.findora.app.core.domain.model.Agent
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.InitialsAvatar
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.PhotoCredit
import com.findora.app.core.domain.model.PricePoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyImage
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RecentSearch
import java.time.Instant
import java.time.YearMonth

fun ListingDto.toEntity(fetchedAtMillis: Long): PropertyEntity =
    PropertyEntity(
        id = id,
        title = title,
        type = PropertyType.fromId(type).id,
        rentAmount = rentPerMonth,
        currency = Currency.fromCode(currency).code,
        bedrooms = bedrooms,
        bathrooms = bathrooms,
        areaSqm = areaSqm,
        floor = floor,
        furnished = furnished,
        petFriendly = petFriendly,
        parking = parking,
        amenities = amenities,
        tags = tags,
        rating = rating,
        reviewsCount = reviewsCount,
        verified = verified,
        latitude = latitude,
        longitude = longitude,
        locality = locality,
        regionId = region,
        description = description,
        agent =
            AgentColumns(
                id = agent.id,
                name = agent.name,
                phone = agent.phone,
                initials = agent.avatar.initials,
                avatarArgb = parseHexColour(agent.avatar.background),
                responseTimeMinutes = agent.responseTimeMinutes,
            ),
        images =
            images.map {
                ImageColumns(
                    url = it.url,
                    width = it.width,
                    height = it.height,
                    dominantColorArgb = parseHexColour(it.dominantColor),
                    alt = it.alt,
                    photographer = it.photographer,
                    photographerUrl = it.photographerUrl,
                    sourceUrl = it.sourceUrl,
                    licence = it.licence,
                    licenceUrl = it.licenceUrl,
                    offlineAsset = it.offlineAsset,
                    fallbackAsset = it.fallbackAsset,
                )
            },
        videoUrl = videoUrl,
        panoramaUrl = panoramaUrl,
        createdAtMillis = Instant.parse(createdAt).toEpochMilli(),
        priceHistory = priceHistory.map { PricePointColumns(it.month, it.amount) },
        sampleData = sampleData,
        fetchedAtMillis = fetchedAtMillis,
    )

fun PropertyEntity.toDomain(): Property =
    Property(
        id = id,
        title = title,
        type = PropertyType.fromId(type),
        rent = Money(rentAmount, Currency.fromCode(currency)),
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
        regionId = regionId,
        description = description,
        agent =
            Agent(
                id = agent.id,
                name = agent.name,
                phone = agent.phone,
                avatar = InitialsAvatar(agent.initials, agent.avatarArgb),
                responseTimeMinutes = agent.responseTimeMinutes,
            ),
        images =
            images.map {
                PropertyImage(
                    url = it.url,
                    width = it.width,
                    height = it.height,
                    dominantColorArgb = it.dominantColorArgb,
                    altText = it.alt,
                    credit = PhotoCredit(it.photographer, it.photographerUrl, it.sourceUrl, it.licence, it.licenceUrl),
                    offlineAsset = it.offlineAsset,
                    fallbackAsset = it.fallbackAsset,
                )
            },
        videoUrl = videoUrl,
        panoramaUrl = panoramaUrl,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        priceHistory = priceHistory.map { PricePoint(YearMonth.parse(it.month), it.amount) },
        isSampleData = sampleData,
    )

fun InteractionEvent.toEntity(): InteractionEventEntity =
    InteractionEventEntity(
        userId = userId,
        propertyId = propertyId,
        type = type.id,
        occurredAtMillis = occurredAt.toEpochMilli(),
        dwellMillis = dwellMillis,
    )

fun InteractionEventEntity.toDomain(): InteractionEvent =
    InteractionEvent(
        userId = userId,
        propertyId = propertyId,
        type = InteractionType.fromId(type),
        occurredAt = Instant.ofEpochMilli(occurredAtMillis),
        dwellMillis = dwellMillis,
    )

fun RecentSearchEntity.toDomain(): RecentSearch = RecentSearch(query, Instant.ofEpochMilli(searchedAtMillis))
