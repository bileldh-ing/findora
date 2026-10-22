package com.findora.app.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.auth.CredentialValidator
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.feature.auth.common.secondsUntil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = true,
    val emailError: EmailError? = null,
    val passwordMissing: Boolean = false,
    /** Attempts left after a wrong e-mail/password, or null when there is no such error. */
    val attemptsRemaining: Int? = null,
    val lockoutSecondsRemaining: Int = 0,
    val isSubmitting: Boolean = false,
) {
    val isLockedOut: Boolean get() = lockoutSecondsRemaining > 0
    val canSubmit: Boolean get() = !isSubmitting && !isLockedOut
}

sealed interface LoginUiEvent {
    data class EmailChanged(
        val value: String,
    ) : LoginUiEvent

    data class PasswordChanged(
        val value: String,
    ) : LoginUiEvent

    data class RememberMeChanged(
        val value: Boolean,
    ) : LoginUiEvent

    data object Submit : LoginUiEvent

    data object TryDemo : LoginUiEvent
}

sealed interface LoginUiEffect {
    data object SignedIn : LoginUiEffect
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LoginUiState())
        val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

        private val effectChannel = Channel<LoginUiEffect>(Channel.BUFFERED)
        val effects: Flow<LoginUiEffect> = effectChannel.receiveAsFlow()

        private var countdown: Job? = null
        private var submittedOnce = false

        fun onEvent(event: LoginUiEvent) {
            when (event) {
                is LoginUiEvent.EmailChanged ->
                    mutableState.update {
                        it.copy(
                            email = event.value,
                            emailError = if (submittedOnce) CredentialValidator.validateEmail(event.value) else null,
                            attemptsRemaining = null,
                        )
                    }
                is LoginUiEvent.PasswordChanged ->
                    mutableState.update {
                        it.copy(password = event.value, passwordMissing = submittedOnce && event.value.isEmpty())
                    }
                is LoginUiEvent.RememberMeChanged -> mutableState.update { it.copy(rememberMe = event.value) }
                LoginUiEvent.Submit -> submit()
                LoginUiEvent.TryDemo -> signIn { auth.loginWithDemoAccount() }
            }
        }

        private fun submit() {
            submittedOnce = true
            val current = mutableState.value
            val emailError = CredentialValidator.validateEmail(current.email)
            val passwordMissing = current.password.isEmpty()
            mutableState.update { it.copy(emailError = emailError, passwordMissing = passwordMissing) }
            if (emailError != null || passwordMissing) return
            signIn { auth.login(current.email, current.password, current.rememberMe) }
        }

        private fun signIn(block: suspend () -> AuthResult<*>) {
            if (!mutableState.value.canSubmit) return
            mutableState.update { it.copy(isSubmitting = true, attemptsRemaining = null) }
            viewModelScope.launch {
                val result = block()
                mutableState.update { it.copy(isSubmitting = false) }
                when (result) {
                    is AuthResult.Success -> effectChannel.send(LoginUiEffect.SignedIn)
                    is AuthResult.Failure -> showFailure(result.error)
                }
            }
        }

        private fun showFailure(error: AuthError) {
            when (error) {
                is AuthError.InvalidCredentials ->
                    mutableState.update { it.copy(attemptsRemaining = error.attemptsRemaining, password = "") }
                is AuthError.LockedOut -> startCountdown(error.until)
                is AuthError.InvalidInput ->
                    mutableState.update {
                        it.copy(emailError = error.email, passwordMissing = error.password.isNotEmpty())
                    }
                // Sign-in only fails with the errors above; account-management errors cannot occur here.
                else -> Unit
            }
        }

        /** Ticks once per second against the injected clock until the lockout ends. */
        private fun startCountdown(until: Instant) {
            countdown?.cancel()
            countdown =
                viewModelScope.launch {
                    do {
                        val remaining = secondsUntil(until, clock.now())
                        mutableState.update { it.copy(lockoutSecondsRemaining = remaining, password = "") }
                        if (remaining > 0) delay(TICK_MILLIS)
                    } while (remaining > 0)
                }
        }

        private companion object {
            const val TICK_MILLIS = 1_000L
        }
    }
