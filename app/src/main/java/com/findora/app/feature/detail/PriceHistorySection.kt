package com.findora.app.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.insight.PriceTrend
import com.findora.app.core.domain.insight.TrendDirection
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import java.util.Locale

private const val DRAW_MILLIS = 1_100
private const val AREA_ALPHA = 0.18f
private val SparkHeight = 112.dp
private val LineWidth = 3.dp
private val MarkerRadius = 5.dp
private val MarkerRing = 2.dp
private val ChipIconSize = 14.dp

@Composable
internal fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/** Small muted pill marking fictional data, as required for seed listings and sample scorecards. */
@Composable
internal fun SampleDataChip(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.detail_sample_data),
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.onSurfaceMuted,
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(horizontal = FindoraTheme.spacing.xs, vertical = FindoraTheme.spacing.xxs),
    )
}

/** Section title with an optional "Sample data" chip at the end. */
@Composable
internal fun InsightHeader(
    title: String,
    sample: Boolean,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        SectionTitle(title)
        Spacer(Modifier.weight(1f))
        if (sample) SampleDataChip(if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

/**
 * Twelve months of rent as a sparkline that draws itself in (instantly with reduced motion), with the
 * lowest and highest months marked, a change chip and a TalkBack summary of the whole series. Time runs
 * from the reading start, so the chart mirrors in right-to-left languages.
 */
@Composable
fun PriceHistorySection(
    trend: PriceTrend,
    currency: Currency,
    isSampleData: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()

    fun price(amount: Long) = PriceFormatter.format(Money(amount, currency), locale, PriceStyle.Localized)
    val change = formatSignedPercent(trend.changePercent, locale)
    val months = trend.points.size
    val changeText = pluralStringResource(R.plurals.detail_price_change, months, change, months)
    val summary =
        stringResource(
            R.string.detail_price_history_cd,
            price(trend.first.amount),
            formatMonth(trend.first.month, locale),
            price(trend.last.amount),
            formatMonth(trend.last.month, locale),
            changeText,
            price(trend.min.amount),
            price(trend.max.amount),
        ) + if (isSampleData) ", " + stringResource(R.string.detail_sample_data) else ""
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .testTag("detailPriceHistory"),
    ) {
        InsightHeader(stringResource(R.string.detail_price_history), isSampleData, tag = "priceHistorySample")
        ChangeChip(trend.direction, changeText)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FindoraTheme.shapes.chip)
                    .background(FindoraTheme.colors.surface)
                    .padding(FindoraTheme.spacing.sm)
                    .testTag("priceSparkline")
                    .clearAndSetSemantics { contentDescription = summary },
        ) {
            Sparkline(trend)
            Spacer(Modifier.height(FindoraTheme.spacing.xs))
            Row(Modifier.fillMaxWidth()) {
                MutedCaption(formatMonth(trend.first.month, locale))
                Spacer(Modifier.weight(1f))
                MutedCaption(formatMonth(trend.last.month, locale))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
            Legend(FindoraTheme.colors.success, stringResource(R.string.detail_price_low, price(trend.min.amount)))
            Legend(FindoraTheme.colors.error, stringResource(R.string.detail_price_high, price(trend.max.amount)))
        }
    }
}

@Composable
private fun MutedCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted, modifier = modifier)
}

@Composable
private fun Legend(
    color: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Canvas(Modifier.size(MarkerRadius * 2)) { drawCircle(color) }
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(text, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onBackground)
    }
}

/** Rising rent is shown in the error colour and falling rent in the success colour: the renter's view. */
@Composable
private fun ChangeChip(
    direction: TrendDirection,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = FindoraTheme.colors
    val (tint, icon) =
        when (direction) {
            TrendDirection.Up -> colors.error to FindoraIcons.TrendUp
            TrendDirection.Down -> colors.success to FindoraIcons.TrendDown
            TrendDirection.Flat -> colors.onSurfaceMuted to FindoraIcons.TrendFlat
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(tint.copy(alpha = AREA_ALPHA))
                .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs)
                .testTag("priceChangeChip"),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ChipIconSize))
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(text, style = FindoraTheme.typography.caption, color = colors.onBackground)
    }
}

@Composable
private fun Sparkline(
    trend: PriceTrend,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val progress = remember(trend) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(trend, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(DRAW_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line = FindoraTheme.colors.accent
    val low = FindoraTheme.colors.success
    val high = FindoraTheme.colors.error
    val ring = FindoraTheme.colors.surface
    Canvas(modifier.fillMaxWidth().height(SparkHeight)) {
        val points = sparkPoints(trend, rtl)
        val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        val area =
            Path().apply {
                addPath(path)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
        drawPath(
            area,
            Brush.verticalGradient(listOf(line.copy(alpha = AREA_ALPHA * progress.value), Color.Transparent)),
        )
        drawPath(drawn, line, style = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = (points.size - 1).toFloat()
        val minIndex = trend.points.indexOf(trend.min)
        val maxIndex = trend.points.indexOf(trend.max)
        // A marker appears once the line has reached it.
        if (progress.value >= minIndex / last) marker(points[minIndex], low, ring)
        if (progress.value >= maxIndex / last) marker(points[maxIndex], high, ring)
    }
}

private fun DrawScope.sparkPoints(
    trend: PriceTrend,
    rtl: Boolean,
): List<Offset> {
    val inset = (MarkerRadius + MarkerRing).toPx()
    val width = size.width - inset * 2
    val height = size.height - inset * 2
    val min = trend.min.amount
    val range = (trend.max.amount - min).toFloat()
    val last = (trend.points.size - 1).toFloat()
    return trend.points.mapIndexed { index, point ->
        val fx = index / last
        val fy = if (range == 0f) HALF else (point.amount - min) / range
        Offset(inset + (if (rtl) 1f - fx else fx) * width, inset + (1f - fy) * height)
    }
}

private fun DrawScope.marker(
    center: Offset,
    color: Color,
    ring: Color,
) {
    drawCircle(ring, radius = (MarkerRadius + MarkerRing).toPx(), center = center)
    drawCircle(color, radius = MarkerRadius.toPx(), center = center)
}

private const val HALF = 0.5f
