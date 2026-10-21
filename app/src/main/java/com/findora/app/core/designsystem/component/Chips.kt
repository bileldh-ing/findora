package com.findora.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme

/** Category chip: ink fill + white text when selected, surface grey + grey text otherwise. */
@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FindoraTheme.colors
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryAction else colors.surface,
        animationSpec = FindoraTheme.motion.standard(),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryAction else colors.onSurfaceMuted,
        animationSpec = FindoraTheme.motion.standard(),
        label = "chipContent",
    )
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(FindoraTheme.shapes.chip)
                .background(container)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = FindoraTheme.typography.bodyMedium, color = content)
    }
}

/** Translucent glass chip that floats over photos (price pill, rating chip). */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.glassFill)
                .border(1.dp, FindoraTheme.colors.glassBorder, FindoraTheme.shapes.chip)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
