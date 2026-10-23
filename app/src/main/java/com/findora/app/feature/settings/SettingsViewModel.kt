package com.findora.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.security.BiometricAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val account: UserAccount? = null,
    val isGuest: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    /** Biometric lock only makes sense for a remembered session (one that survives an app restart). */
    val sessionRemembered: Boolean = false,
    val confirmingDelete: Boolean = false,
) {
    val canToggleBiometric: Boolean get() = biometricAvailable && sessionRemembered && !isGuest
}

sealed interface SettingsUiEvent {
    data class ThemeSelected(
        val mode: ThemeMode,
    ) : SettingsUiEvent

    data class BiometricToggled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data object Logout : SettingsUiEvent

    data object DeleteAccountRequested : SettingsUiEvent

    data object DeleteAccountConfirmed : SettingsUiEvent

    data object DeleteAccountDismissed : SettingsUiEvent
}

sealed interface SettingsUiEffect {
    data object SignedOut : SettingsUiEffect
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
        biometric: BiometricAvailability,
        private val matchProfiles: MatchProfileRepository,
    ) : ViewModel() {
        private val confirmingDelete = MutableStateFlow(false)
        private val biometricAvailable = biometric.canAuthenticate()

        val state: StateFlow<SettingsUiState> =
            combine(auth.observeCurrentUser(), auth.session, settings.settings, confirmingDelete) {
                account,
                session,
                appSettings,
                confirming,
                ->
                SettingsUiState(
                    account = account,
                    isGuest = session?.isGuest == true,
                    themeMode = appSettings.themeMode,
                    biometricEnabled = appSettings.biometricLockEnabled,
                    biometricAvailable = biometricAvailable,
                    sessionRemembered = session?.isPersistent == true,
                    confirmingDelete = confirming,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

        private val effectChannel = Channel<SettingsUiEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SettingsUiEvent) {
            when (event) {
                is SettingsUiEvent.ThemeSelected -> viewModelScope.launch { settings.setThemeMode(event.mode) }
                is SettingsUiEvent.BiometricToggled ->
                    if (state.value.canToggleBiometric || !event.enabled) {
                        viewModelScope.launch { settings.setBiometricLockEnabled(event.enabled) }
                    }
                SettingsUiEvent.Logout ->
                    viewModelScope.launch {
                        auth.logout()
                        effectChannel.send(SettingsUiEffect.SignedOut)
                    }
                SettingsUiEvent.DeleteAccountRequested -> confirmingDelete.update { true }
                SettingsUiEvent.DeleteAccountDismissed -> confirmingDelete.update { false }
                SettingsUiEvent.DeleteAccountConfirmed ->
                    viewModelScope.launch {
                        confirmingDelete.update { false }
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
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
