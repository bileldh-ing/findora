package com.findora.app.core.domain.format

import com.findora.app.core.domain.model.AreaUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AreaFormatterTest {
    @Test
    fun metricKeepsTheStoredSquareMetres() {
        assertEquals(120L, AreaFormatter.value(120, AreaUnit.Metric))
        assertEquals("120", AreaFormatter.format(120, AreaUnit.Metric, Locale.US))
    }

    @Test
    fun imperialConvertsWithTheExactFootAndRoundsHalfUp() {
        // 1 m² = 10.7639104... ft².
        assertEquals(11L, AreaFormatter.value(1, AreaUnit.Imperial))
        assertEquals(1_076L, AreaFormatter.value(100, AreaUnit.Imperial))
        assertEquals(1_292L, AreaFormatter.value(120, AreaUnit.Imperial))
        assertEquals(0L, AreaFormatter.value(0, AreaUnit.Imperial))
        assertEquals(10.7639, AreaFormatter.SQFT_PER_SQM, 0.0001)
    }

    @Test
    fun numbersAreGroupedForTheLocale() {
        assertEquals("1,292", AreaFormatter.format(120, AreaUnit.Imperial, Locale.US))
        assertEquals("1.292", AreaFormatter.format(120, AreaUnit.Imperial, Locale.GERMANY))
        // French groups with a (narrow) no-break space.
        val french = AreaFormatter.format(120, AreaUnit.Imperial, Locale.FRANCE)
        assertEquals("1292", french.filter(Char::isDigit))
        assertEquals(5, french.length)
    }

    @Test
    fun pricePerAreaFollowsTheUnit() {
        assertEquals(200.0, AreaFormatter.perArea(200.0, AreaUnit.Metric), 0.0)
        assertEquals(18.58, AreaFormatter.perArea(200.0, AreaUnit.Imperial), 0.01)
    }
}
