package com.findora.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import com.findora.app.core.designsystem.component.ActionRow
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SwitchRow
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

/** One scrolling column of settings rows. */
@Composable
internal fun SettingsColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        content = content,
    )
}

/**
 * Owner mode: a switch that opens the owner dashboard when turned on, and a shortcut to it while on. Guests get an
 * inline explanation with "Create account" instead, because listings need an account to belong to.
 */
@Composable
internal fun OwnerSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onOpenOwnerDashboard: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        SectionTitle(stringResource(R.string.owner_section))
        SwitchRow(
            title = stringResource(R.string.owner_mode),
            summary = stringResource(R.string.owner_mode_summary),
            icon = FindoraIcons.Building,
            checked = state.ownerModeActive,
            onCheckedChange = { onEvent(AccountEvent.OwnerModeToggled(it)) },
            modifier = Modifier.testTag("settingsOwnerMode"),
        )
        if (state.ownerModeActive) {
            ActionRow(
                title = stringResource(R.string.owner_dashboard_title),
                icon = FindoraIcons.Layers,
                onClick = onOpenOwnerDashboard,
                modifier = Modifier.testTag("settingsOwnerDashboard"),
            )
        }
        if (state.ownerNeedsAccount) {
            Column(
                verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(FindoraTheme.shapes.card)
                        .background(FindoraTheme.colors.surface)
                        .padding(FindoraTheme.spacing.md)
                        .testTag("ownerNeedsAccount"),
            ) {
                Text(
                    text = stringResource(R.string.owner_guest_message),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                    TextButton(onClick = { onEvent(AccountEvent.OwnerPromptDismissed) }) {
                        Text(stringResource(R.string.action_not_now), color = FindoraTheme.colors.onBackground)
                    }
                    PrimaryPillButton(
                        text = stringResource(R.string.action_create_account),
                        onClick = onCreateAccount,
                        modifier = Modifier.testTag("ownerCreateAccount"),
                    )
                }
            }
        }
    }
}
