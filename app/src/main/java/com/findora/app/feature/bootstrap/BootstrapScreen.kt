package com.findora.app.feature.bootstrap

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraLogoBadge
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.theme.FindoraTheme

private const val BADGE_START_SCALE = 0.6f

/**
 * Phase 0 themed landing. Proves fonts, tokens, motion and navigation end to end.
 * [onOpenDesignSystem] is null in release builds, in which case no button is rendered (no dead controls).
 */
@Composable
fun BootstrapScreen(
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val badgeScale = remember { Animatable(BADGE_START_SCALE) }
    val motion = FindoraTheme.motion
    LaunchedEffect(Unit) { badgeScale.animateTo(1f, motion.bouncy()) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .safeDrawingPadding()
                .padding(horizontal = FindoraTheme.spacing.buttonHorizontal)
                .testTag("bootstrap"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FindoraLogoBadge(
            modifier =
                Modifier.graphicsLayer {
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
        Text(
            text = stringResource(R.string.welcome_title),
            style = FindoraTheme.typography.display,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        if (onOpenDesignSystem != null) {
            Spacer(Modifier.height(FindoraTheme.spacing.xl))
            PrimaryPillButton(
                text = stringResource(R.string.open_design_system),
                onClick = onOpenDesignSystem,
            )
        }
    }
}
