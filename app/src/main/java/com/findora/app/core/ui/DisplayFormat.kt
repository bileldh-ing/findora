package com.findora.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.domain.format.AreaFormatter
import com.findora.app.core.domain.format.DisplayMoney
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Money
import com.findora.app.core.locale.areaUnitFormat
import java.util.Locale

/**
 * The display currency and area unit chosen in Settings, provided once at the root (MainActivity) so every
 * card, sheet and table formats amounts the same way without threading the preference through each screen.
 */
val LocalDisplayPreferences = staticCompositionLocalOf { DisplayPreferences() }

/** The first locale of the current configuration (the per-app language once one is chosen). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/** [money] in the display currency (converted and marked approximate when Settings asks for another currency). */
@Composable
@ReadOnlyComposable
fun displayMoney(money: Money): DisplayMoney = LocalDisplayPreferences.current.display(money)

/** A rent in the display currency ("TND 12,000", "₹10,000"), with "≈ " when converted. */
@Composable
@ReadOnlyComposable
fun rentText(
    money: Money,
    locale: Locale = currentLocale(),
): String = PriceFormatter.formatDisplay(displayMoney(money), locale)

/** "TND 12,000 / month", or "≈ €3,600 / month" when Settings converts to another currency. */
@Composable
@ReadOnlyComposable
fun rentPerMonthText(
    money: Money,
    locale: Locale = currentLocale(),
): String = PriceFormatter.formatDisplayPerMonth(displayMoney(money), locale, stringResource(R.string.per_month))

/** "120 m²" or "1,292 sq ft" depending on the unit chosen in Settings. */
@Composable
@ReadOnlyComposable
fun areaText(
    sqm: Int,
    unit: AreaUnit = LocalDisplayPreferences.current.areaUnit,
    locale: Locale = currentLocale(),
): String = stringResource(areaUnitFormat(unit), AreaFormatter.format(sqm, unit, locale))

/** The spoken form of [areaText] ("120 square metres"), for TalkBack. */
@Composable
@ReadOnlyComposable
fun spokenAreaText(
    sqm: Int,
    unit: AreaUnit = LocalDisplayPreferences.current.areaUnit,
    locale: Locale = currentLocale(),
): String {
    val value = AreaFormatter.value(sqm, unit)
    val plural =
        when (unit) {
            AreaUnit.Metric -> R.plurals.area_spoken_sqm
            AreaUnit.Imperial -> R.plurals.area_spoken_sqft
        }
    return pluralStringResource(
        plural,
        value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        AreaFormatter.format(sqm, unit, locale),
    )
}
