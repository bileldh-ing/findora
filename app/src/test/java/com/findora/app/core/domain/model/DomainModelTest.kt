package com.findora.app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {
    @Test
    fun moneyArithmeticRequiresTheSameCurrency() {
        assertEquals(Money(15, Currency.EUR), Money(10, Currency.EUR) + Money(5, Currency.EUR))
        assertEquals(Money(5, Currency.EUR), Money(10, Currency.EUR) - Money(5, Currency.EUR))
        assertTrue(Money(10, Currency.INR) > Money(9, Currency.INR))
        assertThrows(IllegalArgumentException::class.java) { Money(1, Currency.EUR) + Money(1, Currency.USD) }
        assertThrows(IllegalArgumentException::class.java) { Money(1, Currency.EUR).compareTo(Money(1, Currency.TND)) }
    }

    @Test
    fun currencyCodesParseCaseInsensitively() {
        assertEquals(Currency.TND, Currency.fromCode(" tnd "))
        assertThrows(IllegalArgumentException::class.java) { Currency.fromCode("XYZ") }
    }

    @Test
    fun stableIdsRoundTrip() {
        PropertyType.entries.forEach { assertEquals(it, PropertyType.fromId(it.id)) }
        Amenity.entries.forEach { assertEquals(it, Amenity.fromId(it.id)) }
        InteractionType.entries.forEach { assertEquals(it, InteractionType.fromId(it.id)) }
        assertNull(Amenity.fromIdOrNull("helipad"))
        assertThrows(IllegalArgumentException::class.java) { PropertyType.fromId("castle") }
        assertThrows(IllegalArgumentException::class.java) { Amenity.fromId("helipad") }
        assertThrows(IllegalArgumentException::class.java) { InteractionType.fromId("teleported") }
    }

    @Test
    fun languageTagsFallBackToSystem() {
        assertEquals(AppLanguage.Arabic, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.System, AppLanguage.fromTag("de"))
    }

    @Test
    fun boundsContainmentIsInclusive() {
        val bounds = GeoBounds(south = 8.0, west = 74.0, north = 13.0, east = 78.0)
        assertTrue(GeoPoint(9.98, 76.29) in bounds)
        assertTrue(GeoPoint(8.0, 74.0) in bounds)
        assertFalse(GeoPoint(36.8, 10.18) in bounds)
        assertThrows(IllegalArgumentException::class.java) { GeoBounds(10.0, 0.0, 9.0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { GeoBounds(0.0, 2.0, 1.0, 1.0) }
    }

    @Test
    fun filterRejectsInvertedRentRange() {
        assertThrows(IllegalArgumentException::class.java) { PropertyFilter(minRent = 10, maxRent = 5) }
        assertEquals(PropertyFilter(), PropertyFilter(type = null))
    }
}
