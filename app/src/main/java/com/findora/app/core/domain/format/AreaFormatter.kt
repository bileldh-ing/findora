package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.AreaUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Area display in square metres or square feet. Listings always store square metres; imperial is a display
 * conversion (1 ft² = 0.09290304 m² exactly), rounded HALF_UP to whole square feet. Pure Kotlin, unit-tested.
 */
object AreaFormatter {
    /** Exact by definition of the international foot (0.3048 m). */
    private val SQM_PER_SQFT = BigDecimal("0.09290304")
    private const val SCALE = 10

    /** Square feet in one square metre (about 10.7639). */
    val SQFT_PER_SQM: Double = BigDecimal.ONE.divide(SQM_PER_SQFT, SCALE, RoundingMode.HALF_EVEN).toDouble()

    /** [sqm] in [unit], rounded to a whole number. */
    fun value(
        sqm: Int,
        unit: AreaUnit,
    ): Long =
        when (unit) {
            AreaUnit.Metric -> sqm.toLong()
            AreaUnit.Imperial ->
                BigDecimal(
                    sqm,
                ).divide(SQM_PER_SQFT, SCALE, RoundingMode.HALF_EVEN).setScale(0, RoundingMode.HALF_UP).toLong()
        }

    /** The number part of an area in [unit], grouped for [locale] ("1,076"); the unit label comes from resources. */
    fun format(
        sqm: Int,
        unit: AreaUnit,
        locale: Locale,
    ): String = NumberFormat.getIntegerInstance(locale).format(value(sqm, unit))

    /** A price per square metre expressed per [unit] (per square foot divides by about 10.76). */
    fun perArea(
        pricePerSqm: Double,
        unit: AreaUnit,
    ): Double =
        when (unit) {
            AreaUnit.Metric -> pricePerSqm
            AreaUnit.Imperial -> pricePerSqm / SQFT_PER_SQM
        }
}
