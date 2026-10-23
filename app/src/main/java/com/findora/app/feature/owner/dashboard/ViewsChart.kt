package com.findora.app.feature.owner.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.owner.DailyCount
import com.findora.app.core.ui.currentLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val ChartHeight = 120.dp
private const val BAR_FILL = 0.62f
private const val MIN_BAR_FRACTION = 0.02f

/**
 * Views per day as a simple bar chart drawn on a Canvas (today at the end, mirrored in RTL), with the first and last
 * dates under it. TalkBack reads one sentence: the total and the busiest day.
 */
@Composable
fun ViewsChart(
    days: List<DailyCount>,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val dayFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val total = days.sumOf { it.count }
    val busiest = days.maxByOrNull { it.count }?.takeIf { it.count > 0 }
    val description =
        if (busiest == null) {
            pluralStringResource(R.plurals.owner_chart_cd_empty, days.size, days.size)
        } else {
            pluralStringResource(
                R.plurals.owner_chart_cd,
                total,
                total,
                days.size,
                busiest.date.format(formatter),
                busiest.count,
            )
        }
    val bar = FindoraTheme.colors.accent
    val empty = FindoraTheme.colors.outline
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth().testTag("viewsChart").clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
            if (days.isEmpty()) return@Canvas
            val max = days.maxOf { it.count }.coerceAtLeast(1)
            val slot = size.width / days.size
            val barWidth = slot * BAR_FILL
            days.forEachIndexed { index, day ->
                val position = if (rtl) days.size - 1 - index else index
                val fraction = (day.count.toFloat() / max).coerceAtLeast(MIN_BAR_FRACTION)
                val height = size.height * fraction
                drawRoundRect(
                    color = if (day.count == 0) empty else bar,
                    topLeft = Offset(position * slot + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
        }
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = days.first().date.format(dayFormatter),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = days.last().date.format(dayFormatter),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}
