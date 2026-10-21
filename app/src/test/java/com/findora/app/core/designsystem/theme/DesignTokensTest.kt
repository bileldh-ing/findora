package com.findora.app.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    private fun argb(color: androidx.compose.ui.graphics.Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun `palette matches the design spec hex values`() {
        assertEquals(0xFFFFFFFFL, argb(FindoraPalette.White))
        assertEquals(0xFFF2F2F4L, argb(FindoraPalette.SurfaceGray))
        assertEquals(0xFF8A8A8EL, argb(FindoraPalette.ChipGrayText))
        assertEquals(0xFF161616L, argb(FindoraPalette.InkBlack))
        assertEquals(0xFF1F6BFFL, argb(FindoraPalette.AccentBlue))
        assertEquals(0xFFFFC531L, argb(FindoraPalette.StarYellow))
        assertEquals(0xFF0E0E10L, argb(FindoraPalette.NightBackground))
        assertEquals(0xFF1A1A1DL, argb(FindoraPalette.NightSurface))
    }

    @Test
    fun `primary action text meets WCAG AA in both themes`() {
        listOf(LightFindoraColors, DarkFindoraColors).forEach { c ->
            val ratio = Contrast.ratio(argb(c.onPrimaryAction), argb(c.primaryAction))
            assertTrue("primary contrast $ratio in dark=${c.isDark}", ratio >= Contrast.AA_NORMAL_TEXT)
        }
    }

    @Test
    fun `body text on background meets WCAG AA in both themes`() {
        listOf(LightFindoraColors, DarkFindoraColors).forEach { c ->
            val ratio = Contrast.ratio(argb(c.onBackground), argb(c.background))
            assertTrue("body contrast $ratio in dark=${c.isDark}", ratio >= Contrast.AA_NORMAL_TEXT)
        }
    }

    @Test
    fun `white text on the photo scrim end meets WCAG AA`() {
        // Scrim end is ~78% black; worst case is a pure white photo underneath.
        val alpha = (argb(FindoraPalette.ScrimEnd) shr 24) / 255.0
        val composited = (255 * (1 - alpha)).toLong()
        val flattened = (0xFFL shl 24) or (composited shl 16) or (composited shl 8) or composited
        assertTrue(Contrast.ratio(0xFFFFFFFFL, flattened) >= Contrast.AA_NORMAL_TEXT)
    }

    @Test
    fun `contrast helper returns known WCAG values`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000L, 0xFFFFFFFFL), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777L, 0xFF777777L), 0.0001)
    }

    @Test
    fun `spacing and radii follow the spec scale`() {
        val s = FindoraSpacing()
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp), listOf(s.xxs, s.xs, s.sm, s.md, s.lg, s.xl))
        val e = FindoraElevation()
        assertTrue(e.high <= 8.dp)
    }

    @Test
    fun `motion durations stay within 200 to 450 ms`() {
        val m = FindoraMotion()
        listOf(m.shortMillis, m.mediumMillis, m.longMillis).forEach { assertTrue(it in 200..450) }
    }
}
