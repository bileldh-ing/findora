package com.findora.app.feature.match.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val IconBadge = 44.dp
private val IconSize = 22.dp

/** The way into Dream Match from Home and Saved: one tappable row with what it does. */
@Composable
fun DreamMatchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.home_dream_match_action)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.primaryAction)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .padding(FindoraTheme.spacing.md)
                .testTag("dreamMatchEntry"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(IconBadge).clip(CircleShape).background(FindoraTheme.colors.onPrimaryAction),
        ) {
            Icon(
                FindoraIcons.Sparkles,
                contentDescription = null,
                tint = FindoraTheme.colors.primaryAction,
                modifier = Modifier.size(IconSize),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_dream_match_title),
                style = FindoraTheme.typography.section,
                color = FindoraTheme.colors.onPrimaryAction,
            )
            Text(
                text = stringResource(R.string.home_dream_match_body),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onPrimaryAction,
            )
        }
        Icon(
            FindoraIcons.ChevronEnd,
            contentDescription = null,
            tint = FindoraTheme.colors.onPrimaryAction,
            modifier = Modifier.size(IconSize),
        )
    }
}
