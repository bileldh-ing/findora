package com.findora.app.core.testing

import com.findora.app.core.domain.format.ExchangeRates
import com.findora.app.core.domain.model.AboutCredits
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.OpenSourceLibrary
import com.findora.app.core.domain.model.PhotoCredit
import com.findora.app.core.domain.repository.LanguageController
import com.findora.app.core.domain.repository.ReferenceDataRepository
import java.math.BigDecimal
import java.time.LocalDate

/** Round numbers so conversions are easy to check by hand: 1 USD = 0.9 EUR = 90 INR = 3 TND = 3.6725 AED. */
val testRates =
    ExchangeRates(
        asOf = LocalDate.of(2026, 10, 23),
        base = Currency.USD,
        perBase =
            mapOf(
                Currency.USD to BigDecimal.ONE,
                Currency.EUR to BigDecimal("0.9"),
                Currency.INR to BigDecimal("90"),
                Currency.TND to BigDecimal("3"),
                Currency.AED to BigDecimal("3.6725"),
            ),
        source = "test",
    )

val testCredits =
    AboutCredits(
        libraries = listOf(OpenSourceLibrary("Coil", "Coil Contributors", "Apache License 2.0", "https://coil-kt.github.io/coil/")),
        photoSource = "Pexels",
        photoLicense = "Pexels License",
        photoLicenseUrl = "https://www.pexels.com/license/",
        photographers = listOf(PhotoCredit("Max Vakhtbovych", "https://www.pexels.com/@artbovich/", 12)),
        mapAttribution = "© OpenStreetMap contributors",
        mapLicense = "Open Database License (ODbL)",
        mapUrl = "https://www.openstreetmap.org/copyright",
    )

class FakeReferenceDataRepository(
    private val rates: ExchangeRates? = testRates,
    private val credits: AboutCredits = testCredits,
) : ReferenceDataRepository {
    override suspend fun exchangeRates(): ExchangeRates? = rates

    override suspend fun aboutCredits(): AboutCredits = credits
}

class FakeLanguageController(
    var language: AppLanguage = AppLanguage.System,
) : LanguageController {
    val applied = mutableListOf<AppLanguage>()

    override fun current(): AppLanguage = language

    override fun apply(language: AppLanguage) {
        applied += language
        this.language = language
    }
}
