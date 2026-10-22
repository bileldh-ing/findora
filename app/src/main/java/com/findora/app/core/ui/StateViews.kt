package com.findora.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val StateIconBadge = 72.dp
private val StateIcon = 32.dp

/** Centered empty or error message with an optional action (Clear filters, Retry). */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier =
            modifier.fillMaxWidth().padding(FindoraTheme.spacing.lg).semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(StateIconBadge).clip(CircleShape).background(FindoraTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(StateIcon),
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        Text(
            text = title,
            style = FindoraTheme.typography.section,
            color = FindoraTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(FindoraTheme.spacing.xxs))
        Text(
            text = body,
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(FindoraTheme.spacing.md))
            SecondaryPillButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(horizontal = FindoraTheme.spacing.xl),
            )
        }
    }
}

/** Slim banner (offline cache in use) with an inline retry. */
@Composable
fun InlineBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(start = FindoraTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FindoraIcons.AlertCircle, contentDescription = null, tint = FindoraTheme.colors.onSurfaceMuted)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(
            text = text,
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextLink(text = actionLabel, onClick = onAction, color = FindoraTheme.colors.accent)
    }
}
