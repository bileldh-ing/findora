package com.findora.app.feature.auth.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.feature.auth.common.secondsUntil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val isBusy: Boolean = false,
    /** Seconds left when the demo e-mail is locked out after too many failed sign-ins. */
    val demoLockoutSeconds: Int? = null,
    /** The demo sign-in was refused for another reason (its password was changed through the reset flow). */
    val demoUnavailable: Boolean = false,
)

sealed interface WelcomeUiEvent {
    data object TryDemo : WelcomeUiEvent

    data object ContinueAsGuest : WelcomeUiEvent
}

sealed interface WelcomeUiEffect {
    data object SignedIn : WelcomeUiEffect
}

@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(WelcomeUiState())
        val state: StateFlow<WelcomeUiState> = mutableState.asStateFlow()

        private val effectChannel = Channel<WelcomeUiEffect>(Channel.BUFFERED)
        val effects: Flow<WelcomeUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: WelcomeUiEvent) {
            if (mutableState.value.isBusy) return
            mutableState.update { it.copy(isBusy = true, demoLockoutSeconds = null, demoUnavailable = false) }
            viewModelScope.launch {
                when (event) {
                    WelcomeUiEvent.TryDemo ->
                        when (val result = auth.loginWithDemoAccount()) {
                            is AuthResult.Success -> effectChannel.send(WelcomeUiEffect.SignedIn)
                            is AuthResult.Failure -> showDemoFailure(result.error)
                        }
                    WelcomeUiEvent.ContinueAsGuest -> {
                        auth.continueAsGuest()
                        effectChannel.send(WelcomeUiEffect.SignedIn)
                    }
                }
                mutableState.update { it.copy(isBusy = false) }
            }
        }

        private fun showDemoFailure(error: AuthError) {
            mutableState.update {
                if (error is AuthError.LockedOut) {
                    it.copy(demoLockoutSeconds = secondsUntil(error.until, clock.now()))
                } else {
                    it.copy(demoUnavailable = true)
                }
            }
        }
    }
