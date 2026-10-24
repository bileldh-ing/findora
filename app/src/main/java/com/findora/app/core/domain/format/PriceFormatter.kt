package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.Money
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency as JavaCurrency

/**
 * The one place rents become text, so every screen, the map pins, the widget, chat replies and notifications
 * show the same amount the same way. Pure Kotlin/JVM so it is unit-tested without Android.
 *
 * - Amounts are locale-aware currency strings ("TND 12,000", "$1,250", "₹1,50,000", "1 250 €", "د.ت.‏ 12.000"),
 *   with no fraction digits (rents are whole amounts).
 * - Indian locales use lakh grouping (1,50,000) explicitly, so the JVM and Android agree.
 * - A letter currency code is kept apart from the digits by a no-break space ("TND 12,000"), as Android's ICU
 *   does and the JVM does not, so tests and devices print the same text.
 * - Per-month rents read "TND 12,000 / month"; in right-to-left locales the amount is wrapped in Unicode
 *   FIRST STRONG ISOLATE marks so it never reorders around the slash.
 */
object PriceFormatter {
    /** The period label used when the UI does not pass its translated one (string resource `per_month`). */
    const val DEFAULT_PERIOD_LABEL = "month"

    /** "≈" and a no-break space. */
    const val APPROX = "\u2248\u00A0"

    private const val FSI = '\u2068'
    private const val PDI = '\u2069'
    private const val NBSP = "\u00A0"
    private const val GROUP = 3
    private const val INDIAN_GROUP = 2
    private val rtlLanguages = setOf("ar", "fa", "he", "iw", "ur", "ps", "dv", "yi", "ckb")
    private val letterDigitBoundary = Regex("(?<=\\p{L})(?=\\p{Nd})|(?<=\\p{Nd})(?=\\p{L})")

    /** A rent in its own currency: "TND 12,000", "$1,250", "₹1,50,000". */
    fun format(
        money: Money,
        locale: Locale,
    ): String {
        require(money.amount >= 0) { "Prices cannot be negative" }
        return localized(money, locale).replace(letterDigitBoundary, NBSP)
    }

    /** "TND 12,000 / month"; [periodLabel] comes from string resources in the UI. */
    fun formatPerMonth(
        money: Money,
        locale: Locale,
        periodLabel: String = DEFAULT_PERIOD_LABEL,
    ): String = perPeriod(format(money, locale), locale, periodLabel)

    /** A displayed rent; a converted amount is prefixed with "≈" so it never reads as the listed price. */
    fun formatDisplay(
        display: DisplayMoney,
        locale: Locale,
    ): String {
        val text = format(display.money, locale)
        return if (display.approximate) "$APPROX$text" else text
    }

    /** "≈ €120 / month" for a converted rent, "TND 12,000 / month" otherwise. */
    fun formatDisplayPerMonth(
        display: DisplayMoney,
        locale: Locale,
        periodLabel: String = DEFAULT_PERIOD_LABEL,
    ): String = perPeriod(formatDisplay(display, locale), locale, periodLabel)

    fun isRtl(locale: Locale): Boolean = locale.language in rtlLanguages

    /** [text] without the bidi isolation marks, for surfaces that draw one left-to-right run (map pins). */
    fun withoutIsolation(text: String): String = text.filterNot { it == FSI || it == PDI }

    private fun perPeriod(
        amount: String,
        locale: Locale,
        periodLabel: String,
    ): String = "${isolateIfRtl(amount, locale)} / $periodLabel"

    private fun localized(
        money: Money,
        locale: Locale,
    ): String {
        val javaCurrency = JavaCurrency.getInstance(money.currency.code)
        if (locale.country == "IN") {
            val separator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
            return javaCurrency.getSymbol(locale) + groupDigits(money.amount, separator)
        }
        val format =
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = javaCurrency
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }
        return format.format(money.amount)
    }

    /** Lakh grouping: the last three digits, then groups of two ("12,34,567"). */
    private fun groupDigits(
        amount: Long,
        separator: Char,
    ): String {
        val digits = amount.toString()
        if (digits.length <= GROUP) return digits
        val head = digits.dropLast(GROUP)
        val tail = digits.takeLast(GROUP)
        val groups =
            head
                .reversed()
                .chunked(INDIAN_GROUP)
                .map(String::reversed)
                .reversed()
        return (groups + tail).joinToString(separator.toString())
    }

    private fun isolateIfRtl(
        text: String,
        locale: Locale,
    ): String = if (isRtl(locale)) "$FSI$text$PDI" else text
}
