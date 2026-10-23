package com.findora.app.core.data.reference

import com.findora.app.core.domain.model.Currency
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** Validates the files actually bundled in `src/main/assets`, so a bad edit fails here and not on a phone. */
class ReferenceDataParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(path: String): String = File("src/main/assets", path).readText()

    @Test
    fun theBundledRateTableIsDatedAndCoversEveryDisplayCurrency() {
        val rates = ReferenceDataParser.exchangeRates(json, asset(AssetReferenceDataRepository.RATES))
        assertEquals(Currency.USD, rates.base)
        assertEquals(LocalDate.of(2026, 10, 23), rates.asOf)
        assertTrue(rates.source.isNotBlank())
        Currency.entries.forEach { currency ->
            assertTrue("${currency.code} missing", (rates.rate(currency) ?: BigDecimal.ZERO).signum() > 0)
        }
    }

    @Test
    fun unknownCurrenciesInTheTableAreSkipped() {
        val text = """{"asOf":"2026-10-23","base":"USD","rates":{"USD":"1","EUR":"0.9","XYZ":"5"}}"""
        val rates = ReferenceDataParser.exchangeRates(json, text)
        assertEquals(setOf(Currency.USD, Currency.EUR), rates.perBase.keys)
    }

    @Test
    fun theBundledCreditsListLibrariesPhotographersAndOpenStreetMap() {
        val credits = ReferenceDataParser.aboutCredits(json, asset(AssetReferenceDataRepository.LICENSES))
        assertTrue(credits.libraries.any { it.name.startsWith("Coil") && it.license == "Apache License 2.0" })
        assertTrue(credits.libraries.any { it.name == "osmdroid" })
        assertEquals("Pexels", credits.photoSource)
        assertTrue(credits.photographers.size > 10)
        assertTrue(credits.photographers.all { it.url.startsWith("https://www.pexels.com/@") && it.photos > 0 })
        assertEquals("© OpenStreetMap contributors", credits.mapAttribution)
        assertEquals("https://www.openstreetmap.org/copyright", credits.mapUrl)
    }
}
