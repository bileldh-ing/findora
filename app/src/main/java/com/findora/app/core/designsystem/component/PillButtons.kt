package com.findora.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme

private val PillHeight = 56.dp

/** Solid ink pill ("Login" on the Welcome reference). */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(PillHeight),
        shape = FindoraTheme.shapes.buttonPill,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = FindoraTheme.colors.primaryAction,
                contentColor = FindoraTheme.colors.onPrimaryAction,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text = text, style = FindoraTheme.typography.button)
    }
}

/** White pill with a thin light-grey border ("Sign Up" on the Welcome reference). */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(PillHeight),
        shape = FindoraTheme.shapes.buttonPill,
        border = BorderStroke(1.dp, FindoraTheme.colors.outline),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = FindoraTheme.colors.secondaryAction,
                contentColor = FindoraTheme.colors.onSecondaryAction,
            ),
    ) {
        Text(text = text, style = FindoraTheme.typography.button)
    }
}
