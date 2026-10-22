package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.Money
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency as JavaCurrency

/** How a price is rendered. */
enum class PriceStyle {
    /** Design fidelity: digits grouped by dots, no currency symbol ("10.000"). */
    Design,

    /** Locale-aware currency format ("₹1,50,000", "1 250 €", "$1,250"). */
    Localized,
}

/**
 * Formats rents. Pure Kotlin/JVM so it is unit-tested without Android.
 *
 * Indian locales use lakh grouping (1,50,000) explicitly, so the JVM and Android agree.
 * In right-to-left locales the design-style number is wrapped in Unicode LEFT-TO-RIGHT ISOLATE
 * marks so "10.000/ شهر" never reorders around the slash.
 */
object PriceFormatter {
    const val DEFAULT_PERIOD_LABEL = "Month"

    private const val LRI = '\u2066'
    private const val PDI = '\u2069'
    private const val GROUP = 3
    private const val INDIAN_GROUP = 2
    private val rtlLanguages = setOf("ar", "fa", "he", "iw", "ur", "ps", "dv", "yi", "ckb")

    fun format(
        money: Money,
        locale: Locale,
        style: PriceStyle = PriceStyle.Design,
    ): String {
        require(money.amount >= 0) { "Prices cannot be negative" }
        return when (style) {
            PriceStyle.Design -> isolateIfRtl(groupDigits(money.amount, '.', indian = false), locale)
            PriceStyle.Localized -> localized(money, locale)
        }
    }

    /** "10.000/ Month" in the design style; [periodLabel] comes from string resources in the UI. */
    fun formatPerMonth(
        money: Money,
        locale: Locale,
        periodLabel: String = DEFAULT_PERIOD_LABEL,
        style: PriceStyle = PriceStyle.Design,
    ): String = "${format(money, locale, style)}/ $periodLabel"

    fun isRtl(locale: Locale): Boolean = locale.language in rtlLanguages

    private fun localized(
        money: Money,
        locale: Locale,
    ): String {
        val javaCurrency = JavaCurrency.getInstance(money.currency.code)
        if (locale.country == "IN") {
            val separator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
            return javaCurrency.getSymbol(locale) + groupDigits(money.amount, separator, indian = true)
        }
        val format =
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = javaCurrency
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }
        return format.format(money.amount)
    }

    private fun groupDigits(
        amount: Long,
        separator: Char,
        indian: Boolean,
    ): String {
        val digits = amount.toString()
        if (digits.length <= GROUP) return digits
        val head = digits.dropLast(GROUP)
        val tail = digits.takeLast(GROUP)
        val size = if (indian) INDIAN_GROUP else GROUP
        val groups =
            head
                .reversed()
                .chunked(size)
                .map(String::reversed)
                .reversed()
        return (groups + tail).joinToString(separator.toString())
    }

    private fun isolateIfRtl(
        text: String,
        locale: Locale,
    ): String = if (isRtl(locale)) "$LRI$text$PDI" else text
}
