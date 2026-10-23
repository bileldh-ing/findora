package com.findora.app.core.locale

import android.content.Context
import com.findora.app.R
import com.findora.app.core.domain.format.AreaFormatter
import com.findora.app.core.domain.model.AreaUnit
import java.util.Locale

/** "120 m²" or "1,292 sq ft" (localized unit label) for an area stored in square metres. */
fun areaText(
    context: Context,
    sqm: Int,
    unit: AreaUnit,
    locale: Locale,
): String = context.getString(areaUnitFormat(unit), AreaFormatter.format(sqm, unit, locale))

/** The "%1$s m²" / "%1$s sq ft" pattern for [unit]. */
fun areaUnitFormat(unit: AreaUnit): Int =
    when (unit) {
        AreaUnit.Metric -> R.string.area_value_sqm
        AreaUnit.Imperial -> R.string.area_value_sqft
    }
