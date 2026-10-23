package com.findora.app.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.insight.AffordabilityCalculator
import com.findora.app.core.domain.insight.AffordabilityReport
import com.findora.app.core.domain.insight.AffordabilityVerdict
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val DONUT_MILLIS = 700
private const val FULL_CIRCLE = 360f
private const val START_DEGREES = -90f
private const val CHIP_ALPHA = 0.18f
private val DonutSize = 148.dp
private val DonutStroke = 18.dp
private val CardIconSize = 24.dp
private val DotSize = 10.dp

@Composable
private fun AffordabilityVerdict.color(): Color =
    when (this) {
        AffordabilityVerdict.Comfortable -> FindoraTheme.colors.success
        AffordabilityVerdict.Stretch -> FindoraTheme.colors.star
        AffordabilityVerdict.Over -> FindoraTheme.colors.error
    }

@Composable
private fun AffordabilityVerdict.label(): String =
    stringResource(
        when (this) {
            AffordabilityVerdict.Comfortable -> R.string.afford_verdict_comfortable
            AffordabilityVerdict.Stretch -> R.string.afford_verdict_stretch
            AffordabilityVerdict.Over -> R.string.afford_verdict_over
        },
    )

@Composable
private fun AffordabilityUiState.format(
    amount: Long,
    locale: Locale,
): String = PriceFormatter.format(money(amount), locale, PriceStyle.Localized)

/** "Can I afford it?" entry card; once an income was entered it also shows the last verdict. */
@Composable
fun AffordabilityCard(
    state: AffordabilityUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md)
                .testTag("affordabilityCard"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                FindoraIcons.Calculator,
                contentDescription = null,
                tint = FindoraTheme.colors.accent,
                modifier = Modifier.size(CardIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.xs))
            SectionTitle(stringResource(R.string.afford_title))
        }
        Text(
            text = stringResource(R.string.afford_card_body),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        if (report != null) VerdictChip(report.verdict, Modifier.testTag("affordabilityCardVerdict"))
        SecondaryPillButton(
            text = stringResource(R.string.afford_open),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().testTag("affordabilityOpen"),
        )
    }
}

@Composable
private fun VerdictChip(
    verdict: AffordabilityVerdict,
    modifier: Modifier = Modifier,
) {
    val color = verdict.color()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(color.copy(alpha = CHIP_ALPHA))
                .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs),
    ) {
        Dot(color)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(verdict.label(), style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
    }
}

@Composable
private fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(DotSize)) { drawCircle(color) }
}

/**
 * The calculator: monthly income (numeric keyboard), deposit months and agency fee sliders, an editable
 * moving estimate, a donut of rent against the rest of the income, the 30% verdict and the upfront total,
 * all in the listing's currency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffordabilitySheet(
    state: AffordabilityUiState,
    onEvent: (AffordabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { onEvent(AffordabilityEvent.Dismiss) },
        sheetState = sheetState,
        shape = FindoraTheme.shapes.bottomSheetTop,
        containerColor = FindoraTheme.colors.background,
        modifier = modifier.testTag("affordabilitySheet"),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .padding(bottom = FindoraTheme.spacing.lg),
        ) {
            AffordabilityForm(state, onEvent)
            PrimaryPillButton(
                text = stringResource(R.string.afford_done),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(AffordabilityEvent.Dismiss) }
                },
                modifier = Modifier.fillMaxWidth().testTag("affordabilityDone"),
            )
        }
    }
}

/** The sheet's content, separate from the sheet so it can be tested and previewed on its own. */
@Composable
fun AffordabilityForm(
    state: AffordabilityUiState,
    onEvent: (AffordabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md), modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.afford_title))
        Text(
            text = stringResource(R.string.afford_rent_line, state.format(state.rent.amount, locale)),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        FindoraTextField(
            value = state.incomeText,
            onValueChange = { onEvent(AffordabilityEvent.IncomeChanged(it)) },
            label = stringResource(R.string.afford_income),
            error = state.incomeError?.message(),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            fieldTag = "affordabilityIncome",
        )
        val report = state.report
        if (report == null) {
            Text(
                text = stringResource(R.string.afford_empty),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag("affordabilityEmpty"),
            )
        } else {
            ResultBlock(state, report, locale)
        }
        CostInputs(state, onEvent)
        if (report != null) UpfrontBreakdown(state, report, locale)
        Text(
            text = stringResource(R.string.afford_disclaimer),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun AffordabilityFieldError.message(): String =
    stringResource(
        when (this) {
            AffordabilityFieldError.IncomeZero -> R.string.afford_income_zero
            AffordabilityFieldError.AmountTooLarge -> R.string.afford_amount_too_large
        },
    )

@Composable
private fun ResultBlock(
    state: AffordabilityUiState,
    report: AffordabilityReport,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val rentColor = report.verdict.color()
    val share = formatShare(report.rentShare, locale)
    val remaining = state.format(report.remainingIncome, locale)
    val donutDescription = stringResource(R.string.afford_donut_cd, share, remaining)
    Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clearAndSetSemantics { contentDescription = donutDescription }.testTag("affordabilityDonut"),
            ) {
                Donut(report.rentShare.toFloat(), rentColor)
                Text(share, style = FindoraTheme.typography.title, color = FindoraTheme.colors.onBackground)
            }
            Spacer(Modifier.width(FindoraTheme.spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                LegendLine(rentColor, stringResource(R.string.afford_rent), state.format(report.rent, locale))
                LegendLine(FindoraTheme.colors.outline, stringResource(R.string.afford_remaining), remaining)
            }
        }
        VerdictChip(report.verdict, Modifier.testTag("affordabilityVerdict"))
        Text(
            text =
                stringResource(
                    R.string.afford_max_rent,
                    AffordabilityCalculator.RULE_PERCENT,
                    state.format(report.maxRent, locale),
                ),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.testTag("affordabilityMaxRent"),
        )
    }
}

@Composable
private fun LegendLine(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Dot(color)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Column {
            Text(label, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            Text(value, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
        }
    }
}

/** Rent's share of income as an arc over the full ring (capped at the full ring when rent exceeds income). */
@Composable
private fun Donut(
    share: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val sweep by animateFloatAsState(
        targetValue = share.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(DONUT_MILLIS),
        label = "donut",
    )
    val track = FindoraTheme.colors.outline
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Canvas(modifier.size(DonutSize)) {
        val stroke = DonutStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, FULL_CIRCLE, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            color,
            START_DEGREES,
            sign * sweep * FULL_CIRCLE,
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun CostInputs(
    state: AffordabilityUiState,
    onEvent: (AffordabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs), modifier = modifier.fillMaxWidth()) {
        val depositLabel = pluralStringResource(R.plurals.afford_deposit_months, state.depositMonths, state.depositMonths)
        LabeledSlider(
            label = depositLabel,
            value = state.depositMonths,
            max = AffordabilityCalculator.MAX_DEPOSIT_MONTHS,
            onChange = { onEvent(AffordabilityEvent.DepositMonthsChanged(it)) },
            tag = "affordabilityDeposit",
        )
        LabeledSlider(
            label = stringResource(R.string.afford_fee_percent, state.agencyFeePercent),
            value = state.agencyFeePercent,
            max = AffordabilityCalculator.MAX_AGENCY_FEE_PERCENT,
            onChange = { onEvent(AffordabilityEvent.AgencyFeeChanged(it)) },
            tag = "affordabilityFee",
        )
        FindoraTextField(
            value = state.movingText,
            onValueChange = { onEvent(AffordabilityEvent.MovingEstimateChanged(it)) },
            label = stringResource(R.string.afford_moving),
            error = state.movingError?.message(),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            fieldTag = "affordabilityMoving",
        )
    }
}

/** Whole-number slider; TalkBack announces the full label ("Deposit: 2 months") as the value. */
@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            colors =
                SliderDefaults.colors(
                    thumbColor = FindoraTheme.colors.accent,
                    activeTrackColor = FindoraTheme.colors.accent,
                    inactiveTrackColor = FindoraTheme.colors.outline,
                ),
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = label }.testTag(tag),
        )
    }
}

@Composable
private fun UpfrontBreakdown(
    state: AffordabilityUiState,
    report: AffordabilityReport,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md)
                .testTag("affordabilityUpfront"),
    ) {
        Text(
            stringResource(R.string.afford_upfront),
            style = FindoraTheme.typography.bodyMedium,
            color = FindoraTheme.colors.onBackground,
        )
        AmountRow(stringResource(R.string.afford_deposit), state.format(report.deposit, locale))
        AmountRow(stringResource(R.string.afford_fee), state.format(report.agencyFee, locale))
        AmountRow(stringResource(R.string.afford_moving), state.format(report.movingEstimate, locale))
        HorizontalDivider(color = FindoraTheme.colors.outline)
        AmountRow(
            stringResource(R.string.afford_upfront_total),
            state.format(report.upfrontTotal, locale),
            emphasised = true,
            modifier = Modifier.testTag("affordabilityUpfrontTotal"),
        )
    }
}

@Composable
private fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val style = if (emphasised) FindoraTheme.typography.bodyMedium else FindoraTheme.typography.body
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = style, color = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasised) style.copy(fontWeight = FontWeight.SemiBold) else style,
            color = FindoraTheme.colors.onBackground,
        )
    }
}
