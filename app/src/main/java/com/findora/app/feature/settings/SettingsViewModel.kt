package com.findora.app.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.CacheCleaner
import com.findora.app.core.domain.repository.DocumentWriter
import com.findora.app.core.domain.repository.LanguageController
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.ReferenceDataRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.UserDataExporter
import com.findora.app.core.security.BiometricAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Display-currency choices: null is "each listing in its own currency". */
val DisplayCurrencyOptions: List<Currency?> = listOf(null, Currency.INR, Currency.USD, Currency.EUR, Currency.TND)

data class SettingsUiState(
    val account: UserAccount? = null,
    val isGuest: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    /** Null shows every listing in its own currency. */
    val displayCurrency: Currency? = null,
    /** Date of the bundled indicative exchange-rate table, null when it could not be read. */
    val ratesAsOf: LocalDate? = null,
    val areaUnit: AreaUnit = AreaUnit.Metric,
    val reduceMotion: ReduceMotion = ReduceMotion.System,
    val messageNotifications: Boolean = true,
    val viewingReminderNotifications: Boolean = true,
    val inAppNotifications: Boolean = true,
    val searchAlertNotifications: Boolean = true,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    /** Biometric lock only makes sense for a remembered session (one that survives an app restart). */
    val sessionRemembered: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** An export or cache clear is running; their buttons are disabled meanwhile. */
    val working: Boolean = false,
) {
    val canToggleBiometric: Boolean get() = biometricAvailable && sessionRemembered && !isGuest

    fun isEnabled(toggle: NotificationToggle): Boolean =
        when (toggle) {
            NotificationToggle.Messages -> messageNotifications
            NotificationToggle.ViewingReminders -> viewingReminderNotifications
            NotificationToggle.InApp -> inAppNotifications
            NotificationToggle.SearchAlerts -> searchAlertNotifications
        }
}

sealed interface SettingsUiEvent

/** Preferences: each one is persisted and applied at once. */
sealed interface PreferenceEvent : SettingsUiEvent {
    data class ThemeSelected(
        val mode: ThemeMode,
    ) : PreferenceEvent

    data class LanguageSelected(
        val language: AppLanguage,
    ) : PreferenceEvent

    data class CurrencySelected(
        val currency: Currency?,
    ) : PreferenceEvent

    data class AreaUnitSelected(
        val unit: AreaUnit,
    ) : PreferenceEvent

    data class ReduceMotionSelected(
        val mode: ReduceMotion,
    ) : PreferenceEvent

    data class NotificationToggled(
        val toggle: NotificationToggle,
        val enabled: Boolean,
    ) : PreferenceEvent

    data class BiometricToggled(
        val enabled: Boolean,
    ) : PreferenceEvent
}

/** Privacy & data controls. */
sealed interface DataEvent : SettingsUiEvent {
    data object ClearSearchHistory : DataEvent

    data object ClearCache : DataEvent

    /** Asks the screen to open the system "Save as" picker. */
    data object ExportRequested : DataEvent

    /** The picked document, or null when the picker was cancelled. */
    data class ExportDestinationChosen(
        val uri: String?,
    ) : DataEvent
}

sealed interface AccountEvent : SettingsUiEvent {
    data object Logout : AccountEvent

    data object DeleteAccountRequested : AccountEvent

    data object DeleteAccountConfirmed : AccountEvent

    data object DeleteAccountDismissed : AccountEvent
}

/** One-off feedback shown in a snackbar. */
enum class SettingsMessage { SearchHistoryCleared, CacheCleared, ExportSaved, ExportFailed }

sealed interface SettingsUiEffect {
    data object SignedOut : SettingsUiEffect

    data class Show(
        val message: SettingsMessage,
    ) : SettingsUiEffect

    /** Open ACTION_CREATE_DOCUMENT with this suggested file name (application/json). */
    data class CreateExportDocument(
        val fileName: String,
    ) : SettingsUiEffect
}

private data class LocalState(
    val language: AppLanguage,
    val confirmingDelete: Boolean = false,
    val working: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
        biometric: BiometricAvailability,
        private val matchProfiles: MatchProfileRepository,
        private val languages: LanguageController,
        referenceData: ReferenceDataRepository,
        private val searchHistory: SearchHistoryRepository,
        private val cacheCleaner: CacheCleaner,
        private val exporter: UserDataExporter,
        private val documents: DocumentWriter,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState(language = languages.current()))
        private val biometricAvailable = biometric.canAuthenticate()
        private val ratesAsOf: Flow<LocalDate?> = flow { emit(referenceData.exchangeRates()?.asOf) }

        val state: StateFlow<SettingsUiState> =
            combine(auth.observeCurrentUser(), auth.session, settings.settings, local, ratesAsOf) {
                account,
                session,
                appSettings,
                localState,
                asOf,
                ->
                appSettings.toUiState(localState).copy(
                    account = account,
                    isGuest = session?.isGuest == true,
                    sessionRemembered = session?.isPersistent == true,
                    ratesAsOf = asOf,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

        private val effectChannel = Channel<SettingsUiEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SettingsUiEvent) {
            when (event) {
                is PreferenceEvent -> onPreference(event)
                is DataEvent -> onData(event)
                is AccountEvent -> onAccount(event)
            }
        }

        private fun AppSettings.toUiState(localState: LocalState) =
            SettingsUiState(
                themeMode = themeMode,
                language = localState.language,
                displayCurrency = currencyOverride,
                areaUnit = areaUnit,
                reduceMotion = reduceMotion,
                messageNotifications = messageNotifications,
                viewingReminderNotifications = viewingReminderNotifications,
                inAppNotifications = inAppNotifications,
                searchAlertNotifications = searchAlertNotifications,
                biometricEnabled = biometricLockEnabled,
                biometricAvailable = biometricAvailable,
                confirmingDelete = localState.confirmingDelete,
                working = localState.working,
            )

        private fun onPreference(event: PreferenceEvent) {
            when (event) {
                is PreferenceEvent.ThemeSelected -> viewModelScope.launch { settings.setThemeMode(event.mode) }
                is PreferenceEvent.LanguageSelected -> selectLanguage(event.language)
                is PreferenceEvent.CurrencySelected ->
                    viewModelScope.launch { settings.setCurrencyOverride(event.currency) }
                is PreferenceEvent.AreaUnitSelected -> viewModelScope.launch { settings.setAreaUnit(event.unit) }
                is PreferenceEvent.ReduceMotionSelected ->
                    viewModelScope.launch {
                        settings.setReduceMotion(
                            event.mode,
                        )
                    }
                is PreferenceEvent.NotificationToggled ->
                    viewModelScope.launch { settings.setNotificationEnabled(event.toggle, event.enabled) }
                is PreferenceEvent.BiometricToggled ->
                    if (state.value.canToggleBiometric || !event.enabled) {
                        viewModelScope.launch { settings.setBiometricLockEnabled(event.enabled) }
                    }
            }
        }

        /**
         * Persisted first (background work reads the Settings copy), then applied: AppCompat recreates the
         * activity in the new language and direction, and this ViewModel survives the recreation.
         */
        private fun selectLanguage(language: AppLanguage) {
            if (language == local.value.language) return
            local.update { it.copy(language = language) }
            viewModelScope.launch {
                settings.setLanguage(language)
                languages.apply(language)
            }
        }

        private fun onData(event: DataEvent) {
            when (event) {
                DataEvent.ClearSearchHistory ->
                    viewModelScope.launch {
                        searchHistory.clear(currentUserId())
                        effectChannel.send(SettingsUiEffect.Show(SettingsMessage.SearchHistoryCleared))
                    }
                DataEvent.ClearCache ->
                    working {
                        cacheCleaner.clear()
                        effectChannel.send(SettingsUiEffect.Show(SettingsMessage.CacheCleared))
                    }
                DataEvent.ExportRequested ->
                    viewModelScope.launch {
                        val date = clock.now().atZone(zone).toLocalDate()
                        effectChannel.send(SettingsUiEffect.CreateExportDocument("findora-data-$date.json"))
                    }
                is DataEvent.ExportDestinationChosen -> event.uri?.let(::export)
            }
        }

        private fun export(uri: String) {
            working {
                val message =
                    try {
                        documents.write(uri, exporter.export(currentUserId()))
                        SettingsMessage.ExportSaved
                    } catch (error: IOException) {
                        Log.w(TAG, "Export failed", error)
                        SettingsMessage.ExportFailed
                    } catch (error: SecurityException) {
                        Log.w(TAG, "Export not permitted", error)
                        SettingsMessage.ExportFailed
                    }
                effectChannel.send(SettingsUiEffect.Show(message))
            }
        }

        /** Runs [block] with [SettingsUiState.working] set; a second request while one runs is ignored. */
        private fun working(block: suspend () -> Unit) {
            if (local.value.working) return
            local.update { it.copy(working = true) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    local.update { it.copy(working = false) }
                }
            }
        }

        private fun onAccount(event: AccountEvent) {
            when (event) {
                AccountEvent.Logout ->
                    viewModelScope.launch {
                        auth.logout()
                        effectChannel.send(SettingsUiEffect.SignedOut)
                    }
                AccountEvent.DeleteAccountRequested -> local.update { it.copy(confirmingDelete = true) }
                AccountEvent.DeleteAccountDismissed -> local.update { it.copy(confirmingDelete = false) }
                AccountEvent.DeleteAccountConfirmed -> deleteAccount()
            }
        }

        private fun deleteAccount() {
            viewModelScope.launch {
                local.update { it.copy(confirmingDelete = false) }
                // The quiz answers belong to the account being deleted.
                auth.session.value
                    ?.userId
                    ?.let { matchProfiles.clear(it) }
                auth.deleteAccount()
                // The biometric lock belonged to the deleted account.
                settings.setBiometricLockEnabled(false)
                effectChannel.send(SettingsUiEffect.SignedOut)
            }
        }

        private fun currentUserId(): String = auth.session.value?.userId ?: AuthRepository.GUEST_USER_ID

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TAG = "Settings"
        }
    }
