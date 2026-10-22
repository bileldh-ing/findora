package com.findora.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.findora.app.core.designsystem.theme.FindoraTheme

private const val SHIMMER_MILLIS = 1_100
private const val SHIMMER_WIDTH = 600f

/** Placeholder block with a soft highlight sweeping across the surface colour (loading skeletons). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val base = FindoraTheme.colors.surface
    val highlight = FindoraTheme.colors.outline
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -SHIMMER_WIDTH,
        targetValue = SHIMMER_WIDTH * 2,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x, 0f),
                end = Offset(x + SHIMMER_WIDTH, SHIMMER_WIDTH),
            ),
        ),
    )
}
