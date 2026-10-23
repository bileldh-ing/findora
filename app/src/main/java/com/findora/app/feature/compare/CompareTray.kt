package com.findora.app.feature.compare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.ListingCoverPhoto

/** Height the tray takes above the navigation pill, so Home can pad its grid while the tray shows. */
val CompareTrayHeight = 72.dp
private val Thumb = 36.dp
private val ThumbOverlap = (-10).dp
private val ButtonHeight = 44.dp

/**
 * Floating tray shown on Home while at least one home is picked for comparing: overlapping thumbnails,
 * "2 / 3 selected" (announced politely), Clear and Compare.
 */
@Composable
fun CompareTray(
    state: CompareTrayState,
    onCompare: () -> Unit,
    onClear: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = if (reduceMotion) fadeIn(snap()) else slideInVertically { it } + fadeIn(),
        exit = if (reduceMotion) fadeOut(snap()) else slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CompareTrayHeight - FindoraTheme.spacing.xs)
                    .shadow(FindoraTheme.elevation.high, FindoraTheme.shapes.navPill)
                    .clip(FindoraTheme.shapes.navPill)
                    .background(FindoraTheme.colors.surface)
                    .padding(horizontal = FindoraTheme.spacing.md)
                    .testTag("compareTray"),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ThumbOverlap),
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                state.items.forEach { property ->
                    Box(
                        Modifier
                            .size(Thumb)
                            .clip(CircleShape)
                            .border(2.dp, FindoraTheme.colors.surface, CircleShape),
                    ) { ListingCoverPhoto(property) }
                }
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.compare_tray_count,
                        state.items.size,
                        state.items.size,
                        state.maxItems,
                    ),
                style = FindoraTheme.typography.label,
                color = FindoraTheme.colors.onBackground,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = FindoraTheme.spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("compareTrayCount"),
            )
            TextLink(
                text = stringResource(R.string.compare_clear),
                onClick = onClear,
                modifier = Modifier.testTag("compareTrayClear"),
            )
            Button(
                onClick = onCompare,
                shape = FindoraTheme.shapes.buttonPill,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = FindoraTheme.colors.primaryAction,
                        contentColor = FindoraTheme.colors.onPrimaryAction,
                    ),
                modifier = Modifier.height(ButtonHeight).testTag("compareTrayOpen"),
            ) {
                Text(stringResource(R.string.compare_tray_action), style = FindoraTheme.typography.label)
            }
        }
    }
}
