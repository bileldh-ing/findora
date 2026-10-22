package com.findora.app.feature.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.auth.CredentialValidator
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.NameError
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.domain.repository.AuthRepository
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

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    /** Errors are only shown once the user has tried to submit; the rules checklist is always live. */
    val showErrors: Boolean = false,
    val emailTaken: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val nameError: NameError? get() = if (showErrors) CredentialValidator.validateName(name) else null
    val emailError: EmailError? get() = if (showErrors) CredentialValidator.validateEmail(email) else null
    val unmetRules: Set<PasswordRule> get() = CredentialValidator.unmetPasswordRules(password)
    val passwordError: Boolean get() = showErrors && unmetRules.isNotEmpty()
    val confirmMismatch: Boolean get() = showErrors && confirmPassword != password
    val isValid: Boolean
        get() =
            CredentialValidator.validateName(name) == null &&
                CredentialValidator.validateEmail(email) == null &&
                unmetRules.isEmpty() &&
                confirmPassword == password
}

sealed interface SignUpUiEvent {
    data class NameChanged(
        val value: String,
    ) : SignUpUiEvent

    data class EmailChanged(
        val value: String,
    ) : SignUpUiEvent

    data class PasswordChanged(
        val value: String,
    ) : SignUpUiEvent

    data class ConfirmPasswordChanged(
        val value: String,
    ) : SignUpUiEvent

    data object Submit : SignUpUiEvent
}

sealed interface SignUpUiEffect {
    data object SignedIn : SignUpUiEffect
}

@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SignUpUiState())
        val state: StateFlow<SignUpUiState> = mutableState.asStateFlow()

        private val effectChannel = Channel<SignUpUiEffect>(Channel.BUFFERED)
        val effects: Flow<SignUpUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SignUpUiEvent) {
            when (event) {
                is SignUpUiEvent.NameChanged -> mutableState.update { it.copy(name = event.value) }
                is SignUpUiEvent.EmailChanged ->
                    mutableState.update {
                        it.copy(
                            email = event.value,
                            emailTaken = false,
                        )
                    }
                is SignUpUiEvent.PasswordChanged -> mutableState.update { it.copy(password = event.value) }
                is SignUpUiEvent.ConfirmPasswordChanged ->
                    mutableState.update {
                        it.copy(
                            confirmPassword = event.value,
                        )
                    }
                SignUpUiEvent.Submit -> submit()
            }
        }

        private fun submit() {
            mutableState.update { it.copy(showErrors = true) }
            val current = mutableState.value
            if (!current.isValid || current.isSubmitting) return
            mutableState.update { it.copy(isSubmitting = true) }
            viewModelScope.launch {
                val result = auth.signUp(current.name, current.email, current.password, rememberMe = true)
                mutableState.update { it.copy(isSubmitting = false) }
                when (result) {
                    is AuthResult.Success -> effectChannel.send(SignUpUiEffect.SignedIn)
                    is AuthResult.Failure ->
                        if (result.error == AuthError.EmailTaken) mutableState.update { it.copy(emailTaken = true) }
                }
            }
        }
    }
