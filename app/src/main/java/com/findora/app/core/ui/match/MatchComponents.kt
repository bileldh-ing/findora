package com.findora.app.core.ui.match

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.GlassChip
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.match.MatchResult
import com.findora.app.core.domain.match.Reason
import kotlin.math.roundToInt

private const val RING_MILLIS = 900
private const val BAR_MILLIS = 650
private const val BAR_STAGGER_MILLIS = 70
private const val FULL_SWEEP = 360f
private const val START_ANGLE = -90f
private val RingStroke = 10.dp
private val RingSize = 88.dp
private val SheetRingSize = 72.dp
private val BarHeight = 8.dp
private val ReasonIconSize = 14.dp

/** "92% match" on a listing card, in the same glass style as the rating chip. */
@Composable
fun MatchBadge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    GlassChip(modifier = modifier.testTag("matchBadge")) {
        Text(
            text = stringResource(R.string.match_badge, percent),
            style = FindoraTheme.typography.label,
            color = FindoraTheme.colors.onPhoto,
            maxLines = 1,
        )
    }
}

/**
 * A ring that fills to [score] percent while the number counts up (both skip straight to the end when
 * motion is reduced). TalkBack reads it as one "92% match" phrase.
 */
@Composable
fun MatchRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = RingSize,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    var target by remember { mutableFloatStateOf(if (reduceMotion) score.toFloat() else 0f) }
    LaunchedEffect(score) { target = score.toFloat() }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap() else tween(RING_MILLIS),
        label = "matchRing",
    )
    val description = stringResource(R.string.match_badge, score)
    val track = FindoraTheme.colors.outline
    val arc = FindoraTheme.colors.primaryAction
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .clearAndSetSemantics { contentDescription = description }
                .testTag("matchRing"),
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round)
            val inset = RingStroke.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, FULL_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            drawArc(
                arc,
                START_ANGLE,
                FULL_SWEEP * animated / MatchResult.MAX_SCORE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = "${animated.roundToInt()}%",
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
        )
    }
}

/** A small pill with a tick (positive) or an alert (negative) and the reason's sentence. */
@Composable
fun ReasonChip(
    reason: Reason,
    modifier: Modifier = Modifier,
) {
    val tint = if (reason.positive) FindoraTheme.colors.success else FindoraTheme.colors.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs)
                .testTag("reasonChip"),
    ) {
        Icon(
            imageVector = if (reason.positive) FindoraIcons.Check else FindoraIcons.AlertCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ReasonIconSize),
        )
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(text = reason.text(), style = FindoraTheme.typography.label, color = FindoraTheme.colors.onBackground)
    }
}

/**
 * The detail page's match card: the animated ring, the top three reasons as chips (or the missing must-have)
 * and "Why this match?", which opens the full breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchSummaryCard(
    result: MatchResult,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md)
                .testTag("matchCard"),
    ) {
        MatchRing(score = result.score, reduceMotion = reduceMotion)
        Spacer(Modifier.width(FindoraTheme.spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.match_title),
                style = FindoraTheme.typography.section,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (result.excluded) {
                Text(
                    text = stringResource(R.string.match_excluded, missingMustHaves(result)),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.error,
                    modifier = Modifier.testTag("matchExcluded"),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
                ) {
                    result.topReasons().forEach { ReasonChip(it) }
                }
            }
            TextLink(
                text = stringResource(R.string.match_why),
                onClick = onWhy,
                modifier = Modifier.testTag("matchWhy"),
            )
        }
    }
}

@Composable
private fun missingMustHaves(result: MatchResult): String =
    result.failedMustHaves.map { stringResource(it.labelRes()) }.joinToString(", ")

/** "Why this match?": the full breakdown in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyThisMatchSheet(
    result: MatchResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FindoraTheme.shapes.bottomSheetTop,
        containerColor = FindoraTheme.colors.background,
        modifier = modifier,
    ) {
        WhyThisMatchContent(
            result = result,
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .padding(bottom = FindoraTheme.spacing.lg),
        )
    }
}

/**
 * The sheet's content, separate so it can be tested on its own: the ring, then one row per factor with its
 * reason and a bar that grows to the share of the factor's weight it earned, staggered top to bottom.
 */
@Composable
fun WhyThisMatchContent(
    result: MatchResult,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
        modifier = modifier.fillMaxWidth().testTag("whyThisMatch"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchRing(score = result.score, size = SheetRingSize, reduceMotion = reduceMotion)
            Spacer(Modifier.width(FindoraTheme.spacing.md))
            Column {
                Text(
                    text = stringResource(R.string.match_why),
                    style = FindoraTheme.typography.title,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.match_sheet_body),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        if (result.excluded) {
            Text(
                text = stringResource(R.string.match_excluded, missingMustHaves(result)),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.error,
            )
        }
        result.reasons.forEachIndexed { index, reason ->
            FactorRow(reason = reason, index = index, reduceMotion = reduceMotion)
        }
    }
}

@Composable
private fun FactorRow(
    reason: Reason,
    index: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableFloatStateOf(if (reduceMotion) reason.fraction else 0f) }
    LaunchedEffect(reason.fraction) { target = reason.fraction }
    val fill by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap() else tween(BAR_MILLIS, delayMillis = index * BAR_STAGGER_MILLIS),
        label = "factorBar",
    )
    val factor = stringResource(reason.factor.labelRes())
    val sentence = reason.text()
    val points = stringResource(R.string.match_points, reason.points.roundToInt(), reason.maxPoints)
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "$factor. $sentence. $points" }
                .testTag("factor_${reason.factor.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = factor,
                style = FindoraTheme.typography.bodyMedium,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(text = points, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(BarHeight)
                    .clip(FindoraTheme.shapes.chip)
                    .background(if (reason.positive) FindoraTheme.colors.success else FindoraTheme.colors.error),
            )
        }
        Text(text = sentence, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
    }
}
