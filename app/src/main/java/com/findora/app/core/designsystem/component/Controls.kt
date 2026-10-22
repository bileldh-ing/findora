package com.findora.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme

private val MinTouchTarget = 48.dp

/** Inline text action ("Forgot password?", "Sign up"). Keeps a 48dp touch target. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FindoraTheme.colors.onBackground,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
    ) {
        Text(text = text, style = FindoraTheme.typography.bodyMedium, color = color)
    }
}

/** Checkbox with its label as one toggleable target. */
@Composable
fun LabeledCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = MinTouchTarget)
                .clip(FindoraTheme.shapes.chip)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(end = FindoraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = FindoraTheme.colors.primaryAction,
                    checkmarkColor = FindoraTheme.colors.onPrimaryAction,
                    uncheckedColor = FindoraTheme.colors.onSurfaceMuted,
                ),
            modifier = Modifier.padding(FindoraTheme.spacing.sm),
        )
        Text(text = text, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onBackground)
    }
}

/** A settings row with a title, an optional summary and a trailing switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(FindoraTheme.shapes.chip)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = FindoraTheme.colors.onBackground)
            Spacer(Modifier.width(FindoraTheme.spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
            if (summary != null) {
                Text(
                    text = summary,
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = FindoraTheme.colors.accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = FindoraTheme.colors.surface,
                    uncheckedThumbColor = FindoraTheme.colors.onSurfaceMuted,
                    uncheckedBorderColor = FindoraTheme.colors.outline,
                ),
        )
    }
}

/** A tappable settings row (log out, delete account). */
@Composable
fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FindoraTheme.colors.onBackground,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(FindoraTheme.shapes.chip)
                .selectable(selected = false, role = Role.Button, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(FindoraTheme.spacing.md))
        Text(text = title, style = FindoraTheme.typography.bodyMedium, color = tint)
    }
}

/** Single-choice segmented control (theme: System / Light / Dark). */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.xxs)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .clip(FindoraTheme.shapes.chip)
                        .background(if (isSelected) FindoraTheme.colors.primaryAction else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = FindoraTheme.typography.bodyMedium,
                    color = if (isSelected) FindoraTheme.colors.onPrimaryAction else FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

/** Small filled dot (unread indicator, rule bullets). */
@Composable
fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}
