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
    fun dreamMatchRentShowsTheCurrencyAndASpacedPeriod() {
        // The reported bug: "12.000/ Month" with no currency and a slash glued to the number.
        assertEquals(
            "TND\u00A012,000 / month",
            PriceFormatter.formatPerMonth(Money(12_000, Currency.TND), Locale.US),
        )
        assertEquals("₹10,000 / month", PriceFormatter.formatPerMonth(Money(10_000, Currency.INR), english))
    }

    @Test
    fun letterCurrencyCodesAreSeparatedFromTheDigitsAsOnAndroid() {
        assertEquals("TND\u00A012,000", PriceFormatter.format(Money(12_000, Currency.TND), Locale.US))
        assertEquals("AED\u00A05,500", PriceFormatter.format(Money(5_500, Currency.AED), Locale.US))
        assertEquals("TND\u00A01,50,000", PriceFormatter.format(Money(150_000, Currency.TND), english))
        // Symbols that are not letters stay attached, as ICU does.
        assertEquals("$0", PriceFormatter.format(Money(0, Currency.USD), Locale.US))
        assertEquals("€950", PriceFormatter.format(Money(950, Currency.EUR), Locale.US))
    }

    @Test
    fun perMonthUsesThePeriodLabelFromTheCaller() {
        assertEquals(
            "1\u202F250\u00A0€ / mois",
            PriceFormatter.formatPerMonth(Money(1_250, Currency.EUR), Locale.FRANCE, periodLabel = "mois"),
        )
    }

    @Test
    fun displayPerMonthMarksConvertedRentsAndKeepsListedOnesExact() {
        val listed = DisplayMoney(Money(12_000, Currency.TND), approximate = false)
        val converted = DisplayMoney(Money(4_000, Currency.USD), approximate = true)
        assertEquals("TND\u00A012,000 / month", PriceFormatter.formatDisplayPerMonth(listed, Locale.US))
        assertEquals("≈\u00A0$4,000 / month", PriceFormatter.formatDisplayPerMonth(converted, Locale.US))
        assertEquals("≈\u00A0$4,000", PriceFormatter.formatDisplay(converted, Locale.US))
    }

    @Test
    fun inrUsesLakhGrouping() {
        val money = Money(150_000, Currency.INR)
        assertEquals("₹1,50,000", PriceFormatter.format(money, english))
        assertEquals("₹12,34,567", PriceFormatter.format(Money(1_234_567, Currency.INR), english))
        assertEquals("₹999", PriceFormatter.format(Money(999, Currency.INR), english))
    }

    @Test
    fun usdUsesUsConventions() {
        assertEquals("$1,250", PriceFormatter.format(Money(1_250, Currency.USD), Locale.US))
        assertEquals("$1,234,567 / month", PriceFormatter.formatPerMonth(Money(1_234_567, Currency.USD), Locale.US))
    }

    @Test
    fun eurFollowsFrenchAndGermanConventions() {
        val money = Money(1_250, Currency.EUR)
        assertEquals("1\u202F250\u00A0€", PriceFormatter.format(money, Locale.FRANCE))
        assertEquals("1.250\u00A0€", PriceFormatter.format(money, Locale.GERMANY))
    }

    @Test
    fun tndHidesMillimesForWholeRents() {
        val money = Money(1_250, Currency.TND)
        assertEquals(3, Currency.TND.fractionDigits)
        assertEquals(
            "1\u202F250\u00A0DT",
            PriceFormatter.format(money, Locale.forLanguageTag("fr-TN")),
        )
    }

    @Test
    fun rtlPerMonthIsolatesTheAmountSoTheSlashDoesNotReorder() {
        val arabic = Locale.forLanguageTag("ar-TN")
        val formatted = PriceFormatter.formatPerMonth(Money(10_000, Currency.TND), arabic, periodLabel = "شهر")
        assertEquals("\u2068\u062F.\u062A.\u200F\u00A010.000\u2069 / شهر", formatted)
        assertEquals("\u062F.\u062A.\u200F\u00A010.000 / شهر", PriceFormatter.withoutIsolation(formatted))
        assertTrue(PriceFormatter.isRtl(arabic))
        assertTrue(PriceFormatter.isRtl(Locale.forLanguageTag("he-IL")))
        assertFalse(PriceFormatter.isRtl(Locale.FRANCE))
    }

    @Test
    fun ltrPerMonthHasNoIsolationMarks() {
        val formatted = PriceFormatter.formatPerMonth(Money(10_000, Currency.TND), Locale.US)
        assertEquals(formatted, PriceFormatter.withoutIsolation(formatted))
    }

    @Test
    fun rtlKeepsArabicCurrencyConventions() {
        val formatted =
            PriceFormatter.format(
                Money(1_250, Currency.TND),
                Locale.forLanguageTag("ar-TN"),
            )
        assertEquals("\u062F.\u062A.\u200F\u00A01.250", formatted)
        val aed =
            PriceFormatter.format(
                Money(5_500, Currency.AED),
                Locale.forLanguageTag("ar-AE"),
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
