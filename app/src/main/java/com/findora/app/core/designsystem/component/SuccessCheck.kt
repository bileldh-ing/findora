package com.findora.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val CIRCLE_SHARE = 0.55f
private const val FULL_SWEEP = 360f
private const val START_ANGLE = -90f
private const val ANIMATION_MILLIS = 900
private const val FILL_ALPHA = 0.12f

/**
 * A Canvas-drawn success mark: a ring sweeps closed, then the check stroke draws itself. Decorative (the
 * "Viewing booked" heading next to it carries the meaning); with reduced motion it appears fully drawn.
 */
@Composable
fun SuccessCheck(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    val reduceMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) progress.animateTo(1f, tween(ANIMATION_MILLIS, easing = FastOutSlowInEasing))
    }
    val check = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.07f
        val inset = strokeWidth / 2
        val value = progress.value
        drawCircle(color = color.copy(alpha = FILL_ALPHA * value), radius = this.size.minDimension / 2)
        drawArc(
            color = color,
            startAngle = START_ANGLE,
            sweepAngle = FULL_SWEEP * (value / CIRCLE_SHARE).coerceAtMost(1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - strokeWidth, this.size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val checkProgress = ((value - CIRCLE_SHARE) / (1f - CIRCLE_SHARE)).coerceIn(0f, 1f)
        if (checkProgress > 0f) {
            val w = this.size.width
            val h = this.size.height
            check.reset()
            check.moveTo(w * 0.29f, h * 0.52f)
            check.lineTo(w * 0.44f, h * 0.66f)
            check.lineTo(w * 0.72f, h * 0.38f)
            measure.setPath(check, false)
            segment.reset()
            measure.getSegment(0f, measure.length * checkProgress, segment, true)
            drawPath(
                path = segment,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
