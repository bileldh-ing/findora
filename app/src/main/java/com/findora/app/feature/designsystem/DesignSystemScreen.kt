package com.findora.app.feature.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.component.FindoraLogoBadge
import com.findora.app.core.designsystem.component.GlassChip
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.theme.FindoraTheme

private val Categories = listOf("Rental House", "Apartment", "Houses", "Rooms")

/** Debug-only catalogue of every design-system component, grown phase by phase. */
@Composable
fun DesignSystemScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedChip by rememberSaveable { mutableIntStateOf(0) }
    val colors = FindoraTheme.colors
    val spacing = FindoraTheme.spacing

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .testTag("designSystem"),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { Section(stringResource(R.string.ds_buttons)) }
        item {
            Column(
                modifier = Modifier.padding(horizontal = spacing.buttonHorizontal),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PrimaryPillButton(text = stringResource(R.string.back), onClick = onBack)
                SecondaryPillButton(text = stringResource(R.string.back), onClick = onBack)
            }
        }
        item { Section(stringResource(R.string.ds_typography)) }
        item {
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text("WELCOME", style = FindoraTheme.typography.display, color = colors.onBackground)
                Text("Discover\nYour New House", style = FindoraTheme.typography.headline, color = colors.onBackground)
                Text("Nearby Rental Houses", style = FindoraTheme.typography.section, color = colors.onBackground)
                Text(
                    "Find your next space, feel at home",
                    style = FindoraTheme.typography.body,
                    color = colors.onSurfaceMuted,
                )
                Text("Kochi, Kerala", style = FindoraTheme.typography.caption, color = colors.onSurfaceMuted)
            }
        }
        item { Section(stringResource(R.string.ds_colors)) }
        item {
            Row(
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                listOf(
                    colors.primaryAction,
                    colors.surface,
                    colors.onSurfaceMuted,
                    colors.accent,
                    colors.star,
                    colors.error,
                    colors.success,
                ).forEach { Swatch(it) }
            }
        }
        item { Section(stringResource(R.string.ds_chips)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                itemsIndexed(Categories, key = { _, label -> label }) { index, label ->
                    CategoryChip(text = label, selected = index == selectedChip, onClick = { selectedChip = index })
                }
            }
        }
        item {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = spacing.screenHorizontal)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(FindoraTheme.shapes.card)
                        .background(Brush.verticalGradient(listOf(Color(0xFF6B8FB5), colors.scrimEnd))),
                contentAlignment = Alignment.BottomStart,
            ) {
                GlassChip(modifier = Modifier.padding(spacing.sm)) {
                    Text("10.000/ Month", style = FindoraTheme.typography.caption, color = colors.onPhoto)
                }
            }
        }
        item { Section(stringResource(R.string.ds_brand)) }
        item {
            Box(Modifier.fillMaxWidth().padding(bottom = spacing.xl), contentAlignment = Alignment.Center) {
                FindoraLogoBadge(modifier = Modifier.border(1.dp, colors.outline, CircleShape))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier =
            Modifier
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .semantics { heading() },
    )
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, FindoraTheme.colors.outline, CircleShape),
    )
}
