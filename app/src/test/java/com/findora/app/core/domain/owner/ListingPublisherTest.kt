package com.findora.app.core.domain.owner

import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.testing.completeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ListingPublisherTest {
    private val owner = UserAccount("user-9", "ada@findora.app", "Ada Lovelace")
    private val at = Instant.parse("2026-10-23T08:00:00Z")

    @Test
    fun aCompleteDraftBecomesAListingOwnedByTheUser() {
        val property = requireNotNull(ListingPublisher.toProperty("own-1", owner, completeDraft(), at))
        assertEquals("own-1", property.id)
        assertEquals("user-9", property.ownerId)
        assertTrue(property.isListedBy("user-9"))
        assertFalse(property.isListedBy("someone-else"))
        assertEquals(Money(25_000, com.findora.app.core.domain.model.Currency.INR), property.rent)
        assertEquals(Money(50_000, com.findora.app.core.domain.model.Currency.INR), property.deposit)
        assertEquals("owner-user-9", property.agent.id)
        assertEquals("AL", property.agent.avatar.initials)
        assertEquals("", property.agent.phone)
        assertEquals("file:///photos/own-1/0.jpg", property.coverImage.url)
        assertEquals(ListingPublisher.PHOTO_FALLBACK_ASSET, property.coverImage.fallbackAsset)
        assertEquals(at, property.createdAt)
        assertFalse(property.isSampleData)
        assertFalse(property.verified)
        assertEquals(0, property.reviewsCount)
    }

    @Test
    fun anIncompleteDraftIsNotPublished() {
        assertNull(ListingPublisher.toProperty("own-1", owner, completeDraft().copy(photos = emptyList()), at))
    }
}
