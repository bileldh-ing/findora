package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PriceFormatterTest {
    private val english = Locale.forLanguageTag("en-IN")

    @Test
    fun designStyleMatchesTheReference() {
        assertEquals("10.000/ Month", PriceFormatter.formatPerMonth(Money(10_000, Currency.INR), english))
        assertEquals("12.000/ Month", PriceFormatter.formatPerMonth(Money(12_000, Currency.INR), english))
        assertEquals("30.000/ Month", PriceFormatter.formatPerMonth(Money(30_000, Currency.INR), english))
    }

    @Test
    fun designStyleGroupsEveryThreeDigitsWithDots() {
        val locale = Locale.US
        assertEquals("0", PriceFormatter.format(Money(0, Currency.USD), locale))
        assertEquals("950", PriceFormatter.format(Money(950, Currency.USD), locale))
        assertEquals("1.000", PriceFormatter.format(Money(1_000, Currency.USD), locale))
        assertEquals("1.234.567", PriceFormatter.format(Money(1_234_567, Currency.USD), locale))
    }

    @Test
    fun designStyleUsesThePeriodLabelFromTheCaller() {
        val french = Locale.FRANCE
        assertEquals(
            "1.250/ mois",
            PriceFormatter.formatPerMonth(Money(1_250, Currency.EUR), french, periodLabel = "mois"),
        )
    }

    @Test
    fun inrUsesLakhGrouping() {
        val money = Money(150_000, Currency.INR)
        assertEquals("₹1,50,000", PriceFormatter.format(money, english, PriceStyle.Localized))
        assertEquals("₹12,34,567", PriceFormatter.format(Money(1_234_567, Currency.INR), english, PriceStyle.Localized))
        assertEquals("₹999", PriceFormatter.format(Money(999, Currency.INR), english, PriceStyle.Localized))
    }

    @Test
    fun usdUsesUsConventions() {
        assertEquals("$1,250", PriceFormatter.format(Money(1_250, Currency.USD), Locale.US, PriceStyle.Localized))
        assertEquals(
            "$1,250/ Month",
            PriceFormatter.formatPerMonth(Money(1_250, Currency.USD), Locale.US, style = PriceStyle.Localized),
        )
    }

    @Test
    fun eurFollowsFrenchAndGermanConventions() {
        val money = Money(1_250, Currency.EUR)
        assertEquals("1\u202F250\u00A0€", PriceFormatter.format(money, Locale.FRANCE, PriceStyle.Localized))
        assertEquals("1.250\u00A0€", PriceFormatter.format(money, Locale.GERMANY, PriceStyle.Localized))
    }

    @Test
    fun tndHidesMillimesForWholeRents() {
        val money = Money(1_250, Currency.TND)
        assertEquals(3, Currency.TND.fractionDigits)
        assertEquals(
            "1\u202F250\u00A0DT",
            PriceFormatter.format(money, Locale.forLanguageTag("fr-TN"), PriceStyle.Localized),
        )
    }

    @Test
    fun rtlDesignStyleIsolatesTheNumberSoTheSlashDoesNotReorder() {
        val arabic = Locale.forLanguageTag("ar-TN")
        val formatted = PriceFormatter.formatPerMonth(Money(10_000, Currency.TND), arabic, periodLabel = "شهر")
        assertEquals("\u206610.000\u2069/ شهر", formatted)
        assertTrue(PriceFormatter.isRtl(arabic))
        assertTrue(PriceFormatter.isRtl(Locale.forLanguageTag("he-IL")))
        assertFalse(PriceFormatter.isRtl(Locale.FRANCE))
    }

    @Test
    fun rtlLocalizedStyleKeepsArabicCurrencyConventions() {
        val formatted =
            PriceFormatter.format(
                Money(1_250, Currency.TND),
                Locale.forLanguageTag("ar-TN"),
                PriceStyle.Localized,
            )
        assertEquals("\u062F.\u062A.\u200F\u00A01.250", formatted)
        val aed =
            PriceFormatter.format(
                Money(5_500, Currency.AED),
                Locale.forLanguageTag("ar-AE"),
                PriceStyle.Localized,
            )
        assertTrue(aed, aed.endsWith("\u062F.\u0625.\u200F"))
    }

    @Test
    fun negativeAmountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PriceFormatter.format(Money(-1, Currency.EUR), Locale.FRANCE)
        }
    }
}
