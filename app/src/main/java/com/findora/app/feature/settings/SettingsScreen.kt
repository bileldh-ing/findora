package com.findora.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.ActionRow
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SegmentedControl
import com.findora.app.core.designsystem.component.SwitchRow
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.ThemeMode

private val AvatarSize = 56.dp

@Composable
fun SettingsRoute(
    onSignedOut: () -> Unit,
    onCreateAccount: () -> Unit,
    onOpenViewings: () -> Unit,
    onOpenMatchPreferences: () -> Unit,
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsUiEffect.SignedOut -> currentOnSignedOut()
            }
        }
    }
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCreateAccount = onCreateAccount,
        onOpenViewings = onOpenViewings,
        onOpenMatchPreferences = onOpenMatchPreferences,
        onOpenDesignSystem = onOpenDesignSystem,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * Account and app settings. Only working controls are shown: viewings, match preferences (the quiz), theme,
 * biometric lock (when the device supports it), log out and delete account. [onOpenDesignSystem] is null in release builds, so that row disappears.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onCreateAccount: () -> Unit,
    onOpenViewings: () -> Unit,
    onOpenMatchPreferences: () -> Unit,
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        Text(
            text = stringResource(R.string.settings_title),
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        AccountCard(state = state, onCreateAccount = onCreateAccount)
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        ActionRow(
            title = stringResource(R.string.viewings_title),
            icon = FindoraIcons.Calendar,
            onClick = onOpenViewings,
            modifier = Modifier.testTag("settingsViewings"),
        )
        ActionRow(
            title = stringResource(R.string.settings_match_preferences),
            icon = FindoraIcons.Sliders,
            onClick = onOpenMatchPreferences,
            modifier = Modifier.testTag("settingsMatchPreferences"),
        )

        SectionTitle(stringResource(R.string.settings_appearance))
        SegmentedControl(
            options =
                listOf(
                    ThemeMode.System to stringResource(R.string.theme_system),
                    ThemeMode.Light to stringResource(R.string.theme_light),
                    ThemeMode.Dark to stringResource(R.string.theme_dark),
                ),
            selected = state.themeMode,
            onSelect = { onEvent(SettingsUiEvent.ThemeSelected(it)) },
        )

        if (state.biometricAvailable && !state.isGuest) {
            SectionTitle(stringResource(R.string.settings_security))
            SwitchRow(
                title = stringResource(R.string.biometric_lock),
                summary =
                    if (state.sessionRemembered) {
                        stringResource(R.string.biometric_lock_summary)
                    } else {
                        stringResource(R.string.biometric_requires_remember)
                    },
                icon = FindoraIcons.Fingerprint,
                checked = state.biometricEnabled && state.canToggleBiometric,
                enabled = state.canToggleBiometric,
                onCheckedChange = { onEvent(SettingsUiEvent.BiometricToggled(it)) },
            )
        }

        SectionTitle(stringResource(R.string.settings_account))
        ActionRow(
            title = stringResource(R.string.action_logout),
            icon = FindoraIcons.LogOut,
            onClick = { onEvent(SettingsUiEvent.Logout) },
        )
        if (!state.isGuest && state.account != null) {
            ActionRow(
                title = stringResource(R.string.action_delete_account),
                icon = FindoraIcons.Trash,
                tint = FindoraTheme.colors.error,
                onClick = { onEvent(SettingsUiEvent.DeleteAccountRequested) },
            )
        }
        if (onOpenDesignSystem != null) {
            ActionRow(
                title = stringResource(R.string.open_design_system),
                icon = FindoraIcons.ChevronEnd,
                onClick = onOpenDesignSystem,
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsUiEvent.DeleteAccountDismissed) },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsUiEvent.DeleteAccountConfirmed) }) {
                    Text(stringResource(R.string.action_delete), color = FindoraTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SettingsUiEvent.DeleteAccountDismissed) }) {
                    Text(stringResource(R.string.action_cancel), color = FindoraTheme.colors.onBackground)
                }
            },
            containerColor = FindoraTheme.colors.background,
        )
    }
}

@Composable
private fun AccountCard(
    state: SettingsUiState,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(AvatarSize).clip(CircleShape).background(FindoraTheme.colors.primaryAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.account?.initials ?: "?",
                    style = FindoraTheme.typography.section,
                    color = FindoraTheme.colors.onPrimaryAction,
                )
            }
            Spacer(Modifier.width(FindoraTheme.spacing.md))
            Column {
                Text(
                    text =
                        state.account?.let { stringResource(R.string.account_greeting, it.displayName) }
                            ?: stringResource(R.string.account_guest_title),
                    style = FindoraTheme.typography.section,
                    color = FindoraTheme.colors.onBackground,
                )
                Text(
                    text = state.account?.email ?: stringResource(R.string.account_guest_body),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        if (state.isGuest) {
            Spacer(Modifier.height(FindoraTheme.spacing.md))
            PrimaryPillButton(text = stringResource(R.string.action_sign_up), onClick = onCreateAccount)
        }
    }
}

@Composable
private fun SectionTitle(
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
