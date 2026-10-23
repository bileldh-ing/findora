package com.findora.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.security.BiometricAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LaunchState {
    /** Restoring the session; the splash screen stays up meanwhile. */
    data object Loading : LaunchState

    data class Ready(
        val startDestination: FindoraRoute,
    ) : LaunchState
}

/**
 * Route guard: decides the first screen. A valid remembered session skips Welcome (straight to Main, or to
 * Unlock when the biometric lock is on and the device can still authenticate).
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
        private val biometric: BiometricAvailability,
        displayPreferences: ObserveDisplayPreferences,
    ) : ViewModel() {
        private val mutableLaunchState = MutableStateFlow<LaunchState>(LaunchState.Loading)
        val launchState: StateFlow<LaunchState> = mutableLaunchState.asStateFlow()

        val themeMode: StateFlow<ThemeMode> =
            settings.settings
                .map { it.themeMode }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

        /** Display currency and area unit, provided to the whole UI through LocalDisplayPreferences. */
        val displayPreferences: StateFlow<DisplayPreferences> =
            displayPreferences().stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPreferences())

        /** The reduce-motion override: null follows the system. */
        val reducedMotionOverride: StateFlow<Boolean?> =
            settings.settings
                .map {
                    when (it.reduceMotion) {
                        ReduceMotion.System -> null
                        ReduceMotion.On -> true
                        ReduceMotion.Off -> false
                    }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** The signed-in (or guest) user's id, provided to the UI as LocalViewerId. */
        val viewerId: StateFlow<String?> =
            auth.session
                .map { it?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, auth.session.value?.userId)

        init {
            viewModelScope.launch { mutableLaunchState.value = LaunchState.Ready(resolveStart()) }
        }

        private suspend fun resolveStart(): FindoraRoute {
            val session = auth.restoreSession()
            val lockOn = settings.settings.first().biometricLockEnabled
            return when {
                session == null -> FindoraRoute.Welcome
                !session.isGuest && lockOn && biometric.canAuthenticate() -> FindoraRoute.Unlock
                else -> FindoraRoute.Main
            }
        }
    }
