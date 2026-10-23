package com.findora.app.core.domain.insight

import com.findora.app.core.domain.model.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class PriceTrendTest {
    private val start = YearMonth.of(2025, 11)

    private fun history(vararg amounts: Long) =
        amounts.mapIndexed { i, amount -> PricePoint(start.plusMonths(i.toLong()), amount) }

    @Test
    fun minMaxAndChangeOverTwelveMonths() {
        val amounts = longArrayOf(1000, 1000, 950, 980, 1010, 1040, 1020, 1030, 1050, 1060, 1080, 1100)
        val trend = requireNotNull(PriceTrend.from(history(*amounts)))
        assertEquals(12, trend.points.size)
        assertEquals(PricePoint(start.plusMonths(2), 950), trend.min)
        assertEquals(PricePoint(start.plusMonths(11), 1100), trend.max)
        assertEquals(10.0, trend.changePercent, 1e-9)
        assertEquals(TrendDirection.Up, trend.direction)
        assertEquals(start, trend.first.month)
        assertEquals(start.plusMonths(11), trend.last.month)
    }

    @Test
    fun fallingAndFlatTrends() {
        val falling = requireNotNull(PriceTrend.from(history(2000, 1500)))
        assertEquals(-25.0, falling.changePercent, 1e-9)
        assertEquals(TrendDirection.Down, falling.direction)
        val flat = requireNotNull(PriceTrend.from(history(900, 950, 900)))
        assertEquals(0.0, flat.changePercent, 1e-9)
        assertEquals(TrendDirection.Flat, flat.direction)
    }

    @Test
    fun keepsOnlyTheLatestTwelveMonthsInOrder() {
        val fifteen = history(*LongArray(15) { 1000L + it * 10 })
        val trend = requireNotNull(PriceTrend.from(fifteen.shuffled(java.util.Random(7))))
        assertEquals(PriceTrend.MONTHS, trend.points.size)
        assertEquals(start.plusMonths(3), trend.first.month)
        assertEquals(1030L, trend.min.amount)
        assertEquals(1140L, trend.max.amount)
        assertEquals(fifteen.takeLast(12), trend.points)
    }

    @Test
    fun ignoresNonPositiveAmountsAndDuplicateMonths() {
        val points =
            history(0, 1000, -5, 1200) + PricePoint(start.plusMonths(3), 1100)
        val trend = requireNotNull(PriceTrend.from(points))
        assertEquals(listOf(1000L, 1100L), trend.points.map { it.amount })
        assertEquals(10.0, trend.changePercent, 1e-9)
    }

    @Test
    fun tiesReportTheMostRecentMonth() {
        val trend = requireNotNull(PriceTrend.from(history(900, 1000, 900, 1000)))
        assertEquals(start.plusMonths(2), trend.min.month)
        assertEquals(start.plusMonths(3), trend.max.month)
    }

    @Test
    fun tooLittleHistoryHasNoTrend() {
        assertNull(PriceTrend.from(emptyList()))
        assertNull(PriceTrend.from(history(1000)))
        assertNull(PriceTrend.from(history(0, 1000)))
    }
}
