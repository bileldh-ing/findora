package com.findora.app.feature.auth.forgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.PasswordTextField
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.auth.common.AuthFormScaffold
import com.findora.app.feature.auth.common.FieldGap
import com.findora.app.feature.auth.common.FormErrorText
import com.findora.app.feature.auth.common.PasswordRulesChecklist
import com.findora.app.feature.auth.common.messageRes

@Composable
fun ForgotPasswordRoute(
    onBack: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPasswordScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onBackToLogin = onBackToLogin,
        modifier = modifier,
    )
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEvent: (ForgotPasswordUiEvent) -> Unit,
    onBack: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormScaffold(
        title = stringResource(R.string.forgot_title),
        subtitle = stringResource(R.string.forgot_subtitle),
        onBack = onBack,
        modifier = modifier.testTag("forgotPassword"),
    ) {
        FindoraTextField(
            value = state.email,
            onValueChange = { onEvent(ForgotPasswordUiEvent.EmailChanged(it)) },
            label = stringResource(R.string.field_email),
            leadingIcon = FindoraIcons.Mail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = { onEvent(ForgotPasswordUiEvent.RequestCode) },
            enabled = state.step == ForgotStep.EnterEmail,
            error = state.emailError?.let { stringResource(it.messageRes()) },
        )
        when (state.step) {
            ForgotStep.EnterEmail -> {
                state.error?.let { FormErrorText(text = stringResource(it.messageRes())) }
                Spacer(Modifier.height(FindoraTheme.spacing.lg))
                PrimaryPillButton(
                    text = stringResource(R.string.forgot_send_code),
                    onClick = { onEvent(ForgotPasswordUiEvent.RequestCode) },
                    enabled = !state.isSubmitting,
                )
            }
            ForgotStep.EnterCode -> CodeStep(state = state, onEvent = onEvent)
            ForgotStep.Done -> {
                Spacer(Modifier.height(FindoraTheme.spacing.lg))
                Text(
                    text = stringResource(R.string.forgot_success),
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.success,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(FindoraTheme.spacing.lg))
                PrimaryPillButton(text = stringResource(R.string.forgot_back_to_login), onClick = onBackToLogin)
            }
        }
    }
}

@Composable
private fun CodeStep(
    state: ForgotPasswordUiState,
    onEvent: (ForgotPasswordUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(FieldGap))
        if (state.issuedCode != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.surface)
                        .padding(FindoraTheme.spacing.md)
                        .testTag("demoCode"),
            ) {
                Text(
                    text = stringResource(R.string.forgot_demo_notice),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
                Text(
                    text = state.issuedCode,
                    style = FindoraTheme.typography.title.copy(letterSpacing = 0.3.em),
                    color = FindoraTheme.colors.onBackground,
                )
            }
            Spacer(Modifier.height(FieldGap))
        }
        FindoraTextField(
            value = state.code,
            onValueChange = { onEvent(ForgotPasswordUiEvent.CodeChanged(it)) },
            label = stringResource(R.string.forgot_code_label),
            keyboardType = KeyboardType.NumberPassword,
        )
        Spacer(Modifier.height(FieldGap))
        PasswordTextField(
            value = state.newPassword,
            onValueChange = { onEvent(ForgotPasswordUiEvent.NewPasswordChanged(it)) },
            label = stringResource(R.string.forgot_new_password),
            onImeAction = { onEvent(ForgotPasswordUiEvent.ResetPassword) },
        )
        PasswordRulesChecklist(
            unmet = state.unmetRules,
            modifier = Modifier.padding(top = FindoraTheme.spacing.xs, start = FindoraTheme.spacing.xs),
        )
        state.error?.let { FormErrorText(text = stringResource(it.messageRes())) }
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
        PrimaryPillButton(
            text = stringResource(R.string.forgot_title),
            onClick = { onEvent(ForgotPasswordUiEvent.ResetPassword) },
            enabled = !state.isSubmitting,
        )
    }
}

private fun ForgotError.messageRes(): Int =
    when (this) {
        ForgotError.AccountNotFound -> R.string.error_account_not_found
        ForgotError.CodeBlank -> R.string.error_code_blank
        ForgotError.InvalidCode -> R.string.error_invalid_code
        ForgotError.CodeExpired -> R.string.error_code_expired
        ForgotError.WeakPassword -> R.string.error_password_rules
    }
