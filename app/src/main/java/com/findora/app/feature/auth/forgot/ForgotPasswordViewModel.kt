package com.findora.app.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.auth.CredentialValidator
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ForgotStep { EnterEmail, EnterCode, Done }

/** Errors the reset flow can show; mapped to strings by the screen. */
enum class ForgotError { AccountNotFound, CodeBlank, InvalidCode, CodeExpired, WeakPassword }

data class ForgotPasswordUiState(
    val step: ForgotStep = ForgotStep.EnterEmail,
    val email: String = "",
    val emailError: EmailError? = null,
    /** The demo code, displayed in-app because the local flow sends no e-mail. */
    val issuedCode: String? = null,
    val code: String = "",
    val newPassword: String = "",
    val error: ForgotError? = null,
    val isSubmitting: Boolean = false,
) {
    val unmetRules: Set<PasswordRule> get() = CredentialValidator.unmetPasswordRules(newPassword)
}

sealed interface ForgotPasswordUiEvent {
    data class EmailChanged(
        val value: String,
    ) : ForgotPasswordUiEvent

    data class CodeChanged(
        val value: String,
    ) : ForgotPasswordUiEvent

    data class NewPasswordChanged(
        val value: String,
    ) : ForgotPasswordUiEvent

    data object RequestCode : ForgotPasswordUiEvent

    data object ResetPassword : ForgotPasswordUiEvent
}

@HiltViewModel
class ForgotPasswordViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ForgotPasswordUiState())
        val state: StateFlow<ForgotPasswordUiState> = mutableState.asStateFlow()

        fun onEvent(event: ForgotPasswordUiEvent) {
            when (event) {
                is ForgotPasswordUiEvent.EmailChanged ->
                    mutableState.update { it.copy(email = event.value, emailError = null, error = null) }
                is ForgotPasswordUiEvent.CodeChanged ->
                    mutableState.update {
                        it.copy(
                            code = event.value.filter(Char::isDigit).take(CODE_LENGTH),
                            error = null,
                        )
                    }
                is ForgotPasswordUiEvent.NewPasswordChanged ->
                    mutableState.update { it.copy(newPassword = event.value, error = null) }
                ForgotPasswordUiEvent.RequestCode -> requestCode()
                ForgotPasswordUiEvent.ResetPassword -> resetPassword()
            }
        }

        private fun requestCode() {
            val current = mutableState.value
            val emailError = CredentialValidator.validateEmail(current.email)
            if (emailError != null) {
                mutableState.update { it.copy(emailError = emailError) }
                return
            }
            launchSubmitting {
                when (val result = auth.requestPasswordReset(current.email)) {
                    is AuthResult.Success ->
                        mutableState.update {
                            it.copy(
                                step = ForgotStep.EnterCode,
                                issuedCode = result.value.code,
                                code = "",
                                error = null,
                            )
                        }
                    is AuthResult.Failure -> mutableState.update { it.copy(error = result.error.toForgotError()) }
                }
            }
        }

        private fun resetPassword() {
            val current = mutableState.value
            val localError =
                when {
                    current.code.length != CODE_LENGTH -> ForgotError.CodeBlank
                    current.unmetRules.isNotEmpty() -> ForgotError.WeakPassword
                    else -> null
                }
            if (localError != null) {
                mutableState.update { it.copy(error = localError) }
                return
            }
            launchSubmitting {
                when (val result = auth.resetPassword(current.email, current.code, current.newPassword)) {
                    is AuthResult.Success -> mutableState.update { it.copy(step = ForgotStep.Done, error = null) }
                    is AuthResult.Failure -> mutableState.update { it.copy(error = result.error.toForgotError()) }
                }
            }
        }

        private fun launchSubmitting(block: suspend () -> Unit) {
            if (mutableState.value.isSubmitting) return
            mutableState.update { it.copy(isSubmitting = true) }
            viewModelScope.launch {
                block()
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }

        private fun AuthError.toForgotError(): ForgotError =
            when (this) {
                AuthError.ResetCodeExpired -> ForgotError.CodeExpired
                AuthError.InvalidResetCode -> ForgotError.InvalidCode
                is AuthError.InvalidInput -> ForgotError.WeakPassword
                else -> ForgotError.AccountNotFound
            }

        private companion object {
            const val CODE_LENGTH = 6
        }
    }
