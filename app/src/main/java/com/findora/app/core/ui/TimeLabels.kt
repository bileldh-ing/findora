package com.findora.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Short, locale-aware time labels for message lists: the time today, the weekday this week, else the date. */
object TimeLabels {
    private const val WEEK_DAYS = 6L

    fun time(
        instant: Instant,
        zone: ZoneId,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun listLabel(
        instant: Instant,
        zone: ZoneId,
        today: LocalDate,
        locale: Locale,
    ): String {
        val date = instant.atZone(zone).toLocalDate()
        return when {
            date == today -> time(instant, zone, locale)
            date.isAfter(today.minusDays(WEEK_DAYS)) && !date.isAfter(today) ->
                DateTimeFormatter.ofPattern("EEEE", locale).format(date)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
        }
    }

    fun longDate(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

    fun dateTime(
        instant: Instant,
        zone: ZoneId,
        locale: Locale,
    ): String =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(instant.atZone(zone))
}

@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** "Today", "Yesterday" or the full date, for the day headers of a thread. */
@Composable
@ReadOnlyComposable
fun dayLabel(
    date: LocalDate,
    today: LocalDate,
): String =
    when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> TimeLabels.longDate(date, appLocale())
    }
