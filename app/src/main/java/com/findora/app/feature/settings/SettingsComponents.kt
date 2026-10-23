package com.findora.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val RowMinHeight = 56.dp

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.padding(top = FindoraTheme.spacing.md).semantics { heading() },
    )
}

/** A setting with its current value; tapping opens [ChoiceDialog]. Read by TalkBack as "Title, value". */
@Composable
internal fun ValueRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RowMinHeight)
                .clip(FindoraTheme.shapes.chip)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = FindoraTheme.colors.onBackground)
        Spacer(Modifier.width(FindoraTheme.spacing.md))
        Column(Modifier.weight(1f).padding(vertical = FindoraTheme.spacing.xs)) {
            Text(text = title, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
            Text(text = value, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
        }
        Icon(FindoraIcons.ChevronEnd, contentDescription = null, tint = FindoraTheme.colors.onSurfaceMuted)
    }
}

/** A short explanatory line under a setting. */
@Composable
internal fun SettingNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(horizontal = FindoraTheme.spacing.xs),
    )
}

/** Single-choice dialog with radio rows (one selectable group, so TalkBack announces "1 of 4"). */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "choiceDialog",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs)) {
                options.forEachIndexed { index, (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = RowMinHeight)
                                .clip(FindoraTheme.shapes.chip)
                                .selectable(
                                    selected = value == selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(value) },
                                ).testTag("$tag-$index"),
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = FindoraTheme.colors.primaryAction),
                        )
                        Spacer(Modifier.width(FindoraTheme.spacing.sm))
                        Text(label, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onBackground)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = FindoraTheme.colors.onBackground)
            }
        },
        containerColor = FindoraTheme.colors.background,
        modifier = modifier.testTag(tag),
    )
}

/** A labelled card-like container (surface background) for small groups such as the text-size preview. */
@Composable
internal fun SurfaceBlock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        content()
    }
}
