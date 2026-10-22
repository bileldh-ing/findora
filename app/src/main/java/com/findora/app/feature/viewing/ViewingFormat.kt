package com.findora.app.feature.viewing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware date and time labels for viewings (the device's 12/24 h convention comes with the locale). */
internal object ViewingFormat {
    fun time(
        instant: Instant,
        zone: ZoneId,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun weekday(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofPattern("EEE", locale).format(date)

    fun dayOfMonth(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofPattern("d", locale).format(date)

    fun month(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofPattern("MMM", locale).format(date)

    /** "Monday 26 October" style, in the locale's word order. */
    fun longDate(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

    fun mediumDate(
        instant: Instant,
        zone: ZoneId,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone))
}

@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
