package com.findora.app.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.insight.AffordabilityVerdict
import java.util.Locale

private const val CHIP_ALPHA = 0.18f
private val CardIconSize = 24.dp
private val DotSize = 10.dp

@Composable
internal fun AffordabilityVerdict.color(): Color =
    when (this) {
        AffordabilityVerdict.Comfortable -> FindoraTheme.colors.success
        AffordabilityVerdict.Stretch -> FindoraTheme.colors.star
        AffordabilityVerdict.Over -> FindoraTheme.colors.error
    }

@Composable
internal fun AffordabilityVerdict.label(): String =
    stringResource(
        when (this) {
            AffordabilityVerdict.Comfortable -> R.string.afford_verdict_comfortable
            AffordabilityVerdict.Stretch -> R.string.afford_verdict_stretch
            AffordabilityVerdict.Over -> R.string.afford_verdict_over
        },
    )

@Composable
internal fun AffordabilityUiState.format(
    amount: Long,
    locale: Locale,
): String = PriceFormatter.format(money(amount), locale)

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
internal fun VerdictChip(
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
internal fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(DotSize)) { drawCircle(color) }
}
