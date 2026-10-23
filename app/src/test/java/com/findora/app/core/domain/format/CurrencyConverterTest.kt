package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.testing.testRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class CurrencyConverterTest {
    private val converter = CurrencyConverter(testRates)

    @Test
    fun convertsThroughTheBaseCurrency() {
        // 90 INR = 1 USD = 0.9 EUR = 3 TND.
        assertEquals(Money(100, Currency.USD), converter.convert(Money(9_000, Currency.INR), Currency.USD))
        assertEquals(Money(90, Currency.EUR), converter.convert(Money(9_000, Currency.INR), Currency.EUR))
        assertEquals(Money(300, Currency.TND), converter.convert(Money(9_000, Currency.INR), Currency.TND))
        assertEquals(Money(30_000, Currency.INR), converter.convert(Money(1_000, Currency.TND), Currency.INR))
    }

    @Test
    fun sameCurrencyIsReturnedUnchanged() {
        val rent = Money(1_234, Currency.EUR)
        assertSame(rent, converter.convert(rent, Currency.EUR))
    }

    @Test
    fun resultsAreRoundedHalfUpToWholeUnits() {
        // 1 TND = 0.3 EUR exactly: 5 TND = 1.5 EUR -> 2; 1 TND = 0.3 -> 0; 85 INR = 0.85 EUR -> 1.
        assertEquals(2L, converter.convert(Money(5, Currency.TND), Currency.EUR)?.amount)
        assertEquals(0L, converter.convert(Money(1, Currency.TND), Currency.EUR)?.amount)
        assertEquals(1L, converter.convert(Money(85, Currency.INR), Currency.EUR)?.amount)
        // 1 USD = 3.6725 AED: 1000 USD = 3672.5 AED rounds up to 3673.
        assertEquals(3_673L, converter.convert(Money(1_000, Currency.USD), Currency.AED)?.amount)
    }

    @Test
    fun exactConversionKeepsTheFractionForDerivedFigures() {
        val exact = converter.convertExact(BigDecimal("12.5"), Currency.TND, Currency.EUR)
        assertEquals(0, BigDecimal("3.75").compareTo(exact))
    }

    @Test
    fun largeAmountsDoNotLosePrecision() {
        // Floating point would drift here; BigDecimal keeps it exact.
        assertEquals(
            Money(9_000_000_000_000, Currency.INR),
            converter.convert(Money(100_000_000_000, Currency.USD), Currency.INR),
        )
    }

    @Test
    fun aCurrencyMissingFromTheTableCannotBeConverted() {
        val partial = ExchangeRates(LocalDate.of(2026, 10, 23), Currency.USD, mapOf(Currency.USD to BigDecimal.ONE))
        assertNull(CurrencyConverter(partial).convert(Money(100, Currency.USD), Currency.EUR))
    }

    @Test
    fun theTableRejectsAnInvalidBaseOrNonPositiveRates() {
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeRates(LocalDate.MIN, Currency.USD, mapOf(Currency.USD to BigDecimal("2")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeRates(
                LocalDate.MIN,
                Currency.USD,
                mapOf(
                    Currency.USD to BigDecimal.ONE,
                    Currency.EUR to BigDecimal.ZERO,
                ),
            )
        }
    }

    @Test
    fun displayPreferencesConvertOnlyWhenAnotherCurrencyIsChosen() {
        val rent = Money(9_000, Currency.INR)
        assertEquals(DisplayMoney(rent, approximate = false), DisplayPreferences().display(rent))
        assertEquals(
            DisplayMoney(rent, approximate = false),
            DisplayPreferences(currency = Currency.INR, rates = testRates).display(rent),
        )
        assertEquals(
            DisplayMoney(Money(100, Currency.USD), approximate = true),
            DisplayPreferences(currency = Currency.USD, rates = testRates).display(rent),
        )
        // Without a rate table the listing currency is kept: never wrong, only unconverted.
        assertEquals(DisplayMoney(rent, approximate = false), DisplayPreferences(currency = Currency.USD).display(rent))
        assertTrue(DisplayPreferences(currency = Currency.USD, rates = testRates).converts)
        assertFalse(DisplayPreferences(currency = Currency.USD).converts)
    }

    @Test
    fun convertedPricesAreMarkedApproximate() {
        val converted = DisplayPreferences(Currency.EUR, AreaUnit.Metric, testRates).display(Money(9_000, Currency.INR))
        assertEquals("≈\u00A090/ Month", PriceFormatter.formatDisplayPerMonth(converted, Locale.US))
        assertEquals("≈\u00A0€90", PriceFormatter.formatDisplay(converted, Locale.US, PriceStyle.Localized))
        val listed = DisplayMoney(Money(10_000, Currency.INR), approximate = false)
        assertEquals("10.000/ Month", PriceFormatter.formatDisplayPerMonth(listed, Locale.US))
    }
}
