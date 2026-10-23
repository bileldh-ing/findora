package com.findora.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.insight.AffordabilityCalculator
import com.findora.app.core.domain.insight.AffordabilityReport
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CostInputs(
    state: AffordabilityUiState,
    onEvent: (AffordabilityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs), modifier = modifier.fillMaxWidth()) {
        val months = state.depositMonths
        val depositLabel = pluralStringResource(R.plurals.afford_deposit_months, months, months)
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
internal fun UpfrontBreakdown(
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
