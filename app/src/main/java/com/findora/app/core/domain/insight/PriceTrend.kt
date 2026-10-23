package com.findora.app.core.domain.insight

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.PricePoint

enum class TrendDirection { Up, Down, Flat }

/**
 * A listing's rent over its most recent [MONTHS] months: ordered points, lowest and highest month and the
 * change from the first to the last month.
 */
@Immutable
data class PriceTrend(
    /** Oldest first, at most [MONTHS] points, each with a positive amount. */
    val points: List<PricePoint>,
    val min: PricePoint,
    val max: PricePoint,
    /** (last - first) / first, in percent. */
    val changePercent: Double,
) {
    val first: PricePoint get() = points.first()
    val last: PricePoint get() = points.last()

    val direction: TrendDirection
        get() =
            when {
                last.amount > first.amount -> TrendDirection.Up
                last.amount < first.amount -> TrendDirection.Down
                else -> TrendDirection.Flat
            }

    companion object {
        const val MONTHS = 12
        private const val PERCENT = 100.0

        /**
         * Builds a trend from any history: sorts by month, keeps one point per month (the last given),
         * drops non-positive amounts and keeps the latest [MONTHS]. Null when fewer than two months remain.
         * When several months share the lowest (or highest) amount, the most recent one is reported.
         */
        fun from(history: List<PricePoint>): PriceTrend? {
            val points =
                history
                    .filter { it.amount > 0 }
                    .associateBy { it.month }
                    .values
                    .sortedBy { it.month }
                    .takeLast(MONTHS)
            if (points.size < 2) return null
            val first = points.first().amount
            return PriceTrend(
                points = points,
                min = points.last { it.amount == points.minOf(PricePoint::amount) },
                max = points.last { it.amount == points.maxOf(PricePoint::amount) },
                changePercent = (points.last().amount - first) * PERCENT / first,
            )
        }
    }
}
