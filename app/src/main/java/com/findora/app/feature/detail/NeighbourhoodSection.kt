package com.findora.app.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.insight.NeighbourhoodAxis
import com.findora.app.core.domain.insight.NeighbourhoodScore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val GROW_MILLIS = 900
private const val RINGS = 4
private const val FILL_ALPHA = 0.22f
private const val START_ANGLE = -PI / 2
private val RadarHeight = 280.dp
private val LabelMaxWidth = 88.dp
private val LabelGap = 10.dp
private val LabelReserve = 40.dp
private val GridWidth = 1.dp
private val ShapeWidth = 2.dp
private val VertexRadius = 3.5.dp

@Composable
private fun NeighbourhoodAxis.label(): String =
    stringResource(
        when (this) {
            NeighbourhoodAxis.Walkability -> R.string.neighbourhood_walkability
            NeighbourhoodAxis.Transport -> R.string.neighbourhood_transport
            NeighbourhoodAxis.Safety -> R.string.neighbourhood_safety
            NeighbourhoodAxis.Noise -> R.string.neighbourhood_noise
            NeighbourhoodAxis.Amenities -> R.string.neighbourhood_amenities
        },
    )

/**
 * The locality's five scores as a radar chart that grows from the centre (instantly with reduced motion).
 * Axes run clockwise from the top in left-to-right languages and counter-clockwise in right-to-left ones;
 * labels are shaped by the text engine, so Arabic renders correctly. TalkBack reads every score.
 */
@Composable
fun NeighbourhoodSection(
    score: NeighbourhoodScore,
    modifier: Modifier = Modifier,
) {
    val labels = NeighbourhoodAxis.entries.map { it.label() }
    val scoreList =
        NeighbourhoodAxis.entries
            .zip(labels)
            .map { (axis, label) -> stringResource(R.string.detail_neighbourhood_score_cd, label, score.score(axis)) }
            .joinToString(", ")
    val sample = stringResource(R.string.detail_sample_data)
    val summary =
        stringResource(R.string.detail_neighbourhood_cd, score.locality, scoreList, score.overall) +
            if (score.isSampleData) ", $sample" else ""
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .testTag("detailNeighbourhood"),
    ) {
        InsightHeader(stringResource(R.string.detail_neighbourhood), score.isSampleData, tag = "neighbourhoodSample")
        Text(
            text = stringResource(R.string.detail_neighbourhood_caption, score.locality, score.overall),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        RadarChart(
            score = score,
            labels = labels,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FindoraTheme.shapes.chip)
                    .background(FindoraTheme.colors.surface)
                    .padding(FindoraTheme.spacing.sm)
                    .testTag("neighbourhoodRadar")
                    .clearAndSetSemantics { contentDescription = summary },
        )
    }
}

@Composable
private fun RadarChart(
    score: NeighbourhoodScore,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val growth = remember(score) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(score, reduceMotion) {
        if (reduceMotion) growth.snapTo(1f) else growth.animateTo(1f, tween(GROW_MILLIS, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = FindoraTheme.colors
    val labelStyle = FindoraTheme.typography.caption.copy(color = colors.onBackground, textAlign = TextAlign.Center)
    val values = NeighbourhoodAxis.entries.map { score.score(it) / NeighbourhoodScore.MAX_SCORE.toFloat() }
    val texts = NeighbourhoodAxis.entries.mapIndexed { i, axis -> "${labels[i]}\n${score.score(axis)}" }
    Canvas(modifier.height(RadarHeight)) {
        val geometry = RadarGeometry(center, min(size.width, size.height) / 2 - LabelReserve.toPx(), values.size, rtl)
        repeat(RINGS) { ring ->
            drawPolygon(geometry.polygon { (ring + 1f) / RINGS }, colors.outline, Stroke(GridWidth.toPx()))
        }
        values.indices.forEach { i -> drawLine(colors.outline, center, geometry.point(i, 1f), GridWidth.toPx()) }
        val shape = geometry.polygon { i -> values[i] * growth.value }
        drawPolygon(shape, colors.accent.copy(alpha = FILL_ALPHA), null)
        drawPolygon(shape, colors.accent, Stroke(ShapeWidth.toPx()))
        values.indices.forEach { i ->
            drawCircle(colors.accent, VertexRadius.toPx(), geometry.point(i, values[i] * growth.value))
        }
        texts.forEachIndexed { i, text -> drawAxisLabel(measurer, text, labelStyle, geometry, i) }
    }
}

/** Positions on the radar: axis [index] at [fraction] of the radius, mirrored horizontally in RTL. */
private class RadarGeometry(
    val center: Offset,
    val radius: Float,
    val axes: Int,
    val rtl: Boolean,
) {
    fun direction(index: Int): Offset {
        val angle = START_ANGLE + index * 2 * PI / axes
        val x = cos(angle).toFloat()
        return Offset(if (rtl) -x else x, sin(angle).toFloat())
    }

    fun point(
        index: Int,
        fraction: Float,
    ): Offset = center + direction(index) * (radius * fraction)

    fun polygon(fraction: (Int) -> Float): List<Offset> = (0 until axes).map { point(it, fraction(it)) }
}

private fun DrawScope.drawPolygon(
    points: List<Offset>,
    color: Color,
    stroke: Stroke?,
) {
    val path =
        Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
    if (stroke == null) drawPath(path, color) else drawPath(path, color, style = stroke)
}

/** The label sits just outside its axis tip, pushed away from the centre along the axis direction. */
private fun DrawScope.drawAxisLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    geometry: RadarGeometry,
    index: Int,
) {
    val layout =
        measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = LabelMaxWidth.roundToPx()),
            layoutDirection = layoutDirection,
        )
    val direction = geometry.direction(index)
    val anchor = geometry.point(index, 1f) + direction * LabelGap.toPx()
    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    val left = (anchor.x - width * (1 - direction.x) / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    val top = (anchor.y - height * (1 - direction.y) / 2).coerceIn(0f, (size.height - height).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(left, top))
}
