package com.findora.app.core.designsystem.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Rectangle whose bottom edge is a concave arc: the corners stay at full height and the middle rises by
 * [arcDepth], so the white panel below appears to bulge up into the photo (Welcome reference).
 * A quadratic curve whose control point sits at twice the depth peaks exactly at [arcDepth].
 */
class ConcaveArcShape(
    private val arcDepth: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val depth = with(density) { arcDepth.toPx() }.coerceIn(0f, size.height / 2f)
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                quadraticTo(size.width / 2f, size.height - 2f * depth, 0f, size.height)
                close()
            }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is ConcaveArcShape && other.arcDepth == arcDepth

    override fun hashCode(): Int = arcDepth.hashCode()
}
