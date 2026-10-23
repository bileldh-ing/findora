package com.findora.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.ui.currentLocale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import java.util.Currency as JavaCurrency

private val AvatarSize = 56.dp
private const val PERCENT = 100

/** The privacy policy published with the source code. */
const val PRIVACY_POLICY_URL = "https://github.com/bileldh-ing/findora/blob/main/docs/PRIVACY.md"

/** Which choice dialog is open. */
private enum class OpenChoice { Language, Currency }

@Composable
fun SettingsRoute(
    onSignedOut: () -> Unit,
    onCreateAccount: () -> Unit,
    onOpenViewings: () -> Unit,
    onOpenMatchPreferences: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)) { uri ->
            viewModel.onEvent(DataEvent.ExportDestinationChosen(uri?.toString()))
        }
    val messages =
        SettingsMessage.entries.associateWith { message ->
            stringResource(
                when (message) {
                    SettingsMessage.SearchHistoryCleared -> R.string.settings_msg_history_cleared
                    SettingsMessage.CacheCleared -> R.string.settings_msg_cache_cleared
                    SettingsMessage.ExportSaved -> R.string.settings_msg_export_saved
                    SettingsMessage.ExportFailed -> R.string.settings_msg_export_failed
                },
            )
        }
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsUiEffect.SignedOut -> currentOnSignedOut()
                is SettingsUiEffect.Show -> snackbarHostState.showSnackbar(currentMessages.getValue(effect.message))
                is SettingsUiEffect.CreateExportDocument ->
                    try {
                        exportLauncher.launch(effect.fileName)
                    } catch (ignored: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(currentMessages.getValue(SettingsMessage.ExportFailed))
                    }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onCreateAccount = onCreateAccount,
            onOpenViewings = onOpenViewings,
            onOpenMatchPreferences = onOpenMatchPreferences,
            onOpenAbout = onOpenAbout,
            onOpenSystemNotificationSettings = {
                val intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (ignored: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onOpenDesignSystem = onOpenDesignSystem,
            contentPadding = contentPadding,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding),
        )
    }
}

/**
 * Account and app settings. Only working controls are shown: viewings, match preferences, theme, language,
 * display currency, area unit, notifications, reduce motion, biometric lock (when the device supports it),
 * privacy & data (clear history, clear cache, export, privacy policy, about), log out and delete account.
 * [onOpenDesignSystem] is null in release builds, so that row disappears.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onCreateAccount: () -> Unit,
    onOpenViewings: () -> Unit,
    onOpenMatchPreferences: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var openChoice by rememberSaveable { mutableStateOf<OpenChoice?>(null) }
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
            onSelect = { onEvent(PreferenceEvent.ThemeSelected(it)) },
        )

        LanguageAndUnitsSection(state, onEvent, onOpen = { openChoice = it })
        NotificationsSection(state, onEvent, onOpenSystemNotificationSettings)
        AccessibilitySection(state, onEvent)

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
                onCheckedChange = { onEvent(PreferenceEvent.BiometricToggled(it)) },
            )
        }

        PrivacySection(state, onEvent, onOpenAbout)

        SectionTitle(stringResource(R.string.settings_account))
        ActionRow(
            title = stringResource(R.string.action_logout),
            icon = FindoraIcons.LogOut,
            onClick = { onEvent(AccountEvent.Logout) },
        )
        if (!state.isGuest && state.account != null) {
            ActionRow(
                title = stringResource(R.string.action_delete_account),
                icon = FindoraIcons.Trash,
                tint = FindoraTheme.colors.error,
                onClick = { onEvent(AccountEvent.DeleteAccountRequested) },
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
    when (openChoice) {
        OpenChoice.Language ->
            ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = languageOptions(),
                selected = state.language,
                onSelect = {
                    openChoice = null
                    onEvent(PreferenceEvent.LanguageSelected(it))
                },
                onDismiss = { openChoice = null },
                tag = "languageDialog",
            )
        OpenChoice.Currency ->
            ChoiceDialog(
                title = stringResource(R.string.settings_currency),
                options = DisplayCurrencyOptions.map { it to currencyLabel(it) },
                selected = state.displayCurrency,
                onSelect = {
                    openChoice = null
                    onEvent(PreferenceEvent.CurrencySelected(it))
                },
                onDismiss = { openChoice = null },
                tag = "currencyDialog",
            )
        null -> Unit
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(AccountEvent.DeleteAccountDismissed) },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(AccountEvent.DeleteAccountConfirmed) }) {
                    Text(stringResource(R.string.action_delete), color = FindoraTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(AccountEvent.DeleteAccountDismissed) }) {
                    Text(stringResource(R.string.action_cancel), color = FindoraTheme.colors.onBackground)
                }
            },
            containerColor = FindoraTheme.colors.background,
        )
    }
}

@Composable
private fun LanguageAndUnitsSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onOpen: (OpenChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        SectionTitle(stringResource(R.string.settings_language_units))
        ValueRow(
            title = stringResource(R.string.settings_language),
            value = languageOptions().first { it.first == state.language }.second,
            icon = FindoraIcons.Globe,
            onClick = { onOpen(OpenChoice.Language) },
            modifier = Modifier.testTag("settingsLanguage"),
        )
        ValueRow(
            title = stringResource(R.string.settings_currency),
            value = currencyLabel(state.displayCurrency),
            icon = FindoraIcons.Coins,
            onClick = { onOpen(OpenChoice.Currency) },
            modifier = Modifier.testTag("settingsCurrency"),
        )
        SettingNote(currencyNote(state), Modifier.testTag("settingsRatesNote"))
        Text(
            text = stringResource(R.string.settings_area_unit),
            style = FindoraTheme.typography.bodyMedium,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.padding(top = FindoraTheme.spacing.xs),
        )
        SegmentedControl(
            options =
                listOf(
                    AreaUnit.Metric to stringResource(R.string.area_unit_metric),
                    AreaUnit.Imperial to stringResource(R.string.area_unit_imperial),
                ),
            selected = state.areaUnit,
            onSelect = { onEvent(PreferenceEvent.AreaUnitSelected(it)) },
            modifier = Modifier.testTag("settingsAreaUnit"),
        )
    }
}

@Composable
private fun NotificationsSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        SectionTitle(stringResource(R.string.settings_notifications))
        NotificationToggle.entries.forEach { toggle ->
            val (title, summary) =
                when (toggle) {
                    NotificationToggle.Messages -> R.string.notify_messages to R.string.notify_messages_summary
                    NotificationToggle.ViewingReminders -> R.string.notify_reminders to R.string.notify_reminders_summary
                    NotificationToggle.InApp -> R.string.notify_in_app to R.string.notify_in_app_summary
                }
            SwitchRow(
                title = stringResource(title),
                summary = stringResource(summary),
                icon = FindoraIcons.Bell,
                checked = state.isEnabled(toggle),
                onCheckedChange = { onEvent(PreferenceEvent.NotificationToggled(toggle, it)) },
                modifier = Modifier.testTag("notify${toggle.name}"),
            )
        }
        ActionRow(
            title = stringResource(R.string.notify_system_settings),
            icon = FindoraIcons.ExternalLink,
            onClick = onOpenSystemNotificationSettings,
            modifier = Modifier.testTag("settingsSystemNotifications"),
        )
    }
}

@Composable
private fun AccessibilitySection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        SectionTitle(stringResource(R.string.settings_accessibility))
        Text(
            text = stringResource(R.string.reduce_motion),
            style = FindoraTheme.typography.bodyMedium,
            color = FindoraTheme.colors.onBackground,
        )
        SegmentedControl(
            options =
                listOf(
                    ReduceMotion.System to stringResource(R.string.reduce_motion_system),
                    ReduceMotion.On to stringResource(R.string.reduce_motion_on),
                    ReduceMotion.Off to stringResource(R.string.reduce_motion_off),
                ),
            selected = state.reduceMotion,
            onSelect = { onEvent(PreferenceEvent.ReduceMotionSelected(it)) },
            modifier = Modifier.testTag("settingsReduceMotion"),
        )
        SettingNote(stringResource(R.string.reduce_motion_summary))
        val percent = (LocalConfiguration.current.fontScale * PERCENT).roundToInt()
        SurfaceBlock(Modifier.testTag("settingsTextSize")) {
            Text(
                text = stringResource(R.string.text_size_title),
                style = FindoraTheme.typography.bodyMedium,
                color = FindoraTheme.colors.onBackground,
            )
            Text(
                text = stringResource(R.string.text_size_preview),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onBackground,
            )
            Text(
                text = stringResource(R.string.text_size_note, percent),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun PrivacySection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        SectionTitle(stringResource(R.string.settings_privacy))
        ActionRow(
            title = stringResource(R.string.clear_search_history),
            icon = FindoraIcons.History,
            onClick = { onEvent(DataEvent.ClearSearchHistory) },
            modifier = Modifier.testTag("settingsClearHistory"),
        )
        ValueRow(
            title = stringResource(R.string.clear_cache),
            value = stringResource(R.string.clear_cache_summary),
            icon = FindoraIcons.Trash,
            onClick = { onEvent(DataEvent.ClearCache) },
            enabled = !state.working,
            modifier = Modifier.testTag("settingsClearCache"),
        )
        ValueRow(
            title = stringResource(R.string.export_data),
            value = stringResource(R.string.export_data_summary),
            icon = FindoraIcons.Download,
            onClick = { onEvent(DataEvent.ExportRequested) },
            enabled = !state.working,
            modifier = Modifier.testTag("settingsExport"),
        )
        ActionRow(
            title = stringResource(R.string.privacy_policy),
            icon = FindoraIcons.ShieldCheck,
            onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
            modifier = Modifier.testTag("settingsPrivacyPolicy"),
        )
        ActionRow(
            title = stringResource(R.string.about_title),
            icon = FindoraIcons.Info,
            onClick = onOpenAbout,
            modifier = Modifier.testTag("settingsAbout"),
        )
    }
}

/** Language names are shown in their own language, as Android does, so anyone can find theirs. */
@Composable
private fun languageOptions(): List<Pair<AppLanguage, String>> =
    listOf(
        AppLanguage.System to stringResource(R.string.language_system),
        AppLanguage.English to stringResource(R.string.language_english),
        AppLanguage.French to stringResource(R.string.language_french),
        AppLanguage.Arabic to stringResource(R.string.language_arabic),
    )

/** "Listing currency", or the code and localized name ("EUR · euro"). */
@Composable
private fun currencyLabel(currency: Currency?): String {
    if (currency == null) return stringResource(R.string.currency_listing)
    val name = JavaCurrency.getInstance(currency.code).getDisplayName(currentLocale())
    return stringResource(R.string.currency_option, currency.code, name)
}

@Composable
private fun currencyNote(state: SettingsUiState): String {
    val asOf = state.ratesAsOf
    return when {
        state.displayCurrency == null -> stringResource(R.string.settings_currency_note_listing)
        asOf == null -> stringResource(R.string.settings_rates_unavailable)
        else ->
            stringResource(
                R.string.settings_rates_note,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(asOf),
            )
    }
}

private const val EXPORT_MIME_TYPE = "application/json"

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
