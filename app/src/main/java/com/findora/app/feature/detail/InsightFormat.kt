package com.findora.app.feature.detail

import com.findora.app.core.domain.format.PriceFormatter
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LRI = '\u2066'
private const val PDI = '\u2069'

/** "+5.3%" / "−2.0%" / "0.0%", isolated left-to-right in RTL locales so the sign never jumps sides. */
internal fun formatSignedPercent(
    value: Double,
    locale: Locale,
): String {
    val text = String.format(locale, "%+.1f%%", value).replace("+0.0", "0.0").replace("-0.0", "0.0")
    return isolate(text, locale)
}

/** "32%" for a share between 0 and any positive ratio. */
internal fun formatShare(
    ratio: Double,
    locale: Locale,
): String = isolate(String.format(locale, "%.0f%%", ratio * PERCENT), locale)

/** "Oct 2026" in the user's language (stand-alone month form, correct in Arabic and French). */
internal fun formatMonth(
    month: YearMonth,
    locale: Locale,
): String = month.format(DateTimeFormatter.ofPattern("LLL yyyy", locale))

private fun isolate(
    text: String,
    locale: Locale,
): String = if (PriceFormatter.isRtl(locale)) "$LRI$text$PDI" else text

private const val PERCENT = 100.0
