package com.findora.app.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.LabeledCheckbox
import com.findora.app.core.designsystem.component.PasswordTextField
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.auth.common.AuthFormScaffold
import com.findora.app.feature.auth.common.FieldGap
import com.findora.app.feature.auth.common.FormErrorText
import com.findora.app.feature.auth.common.messageRes

@Composable
fun LoginRoute(
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginUiEffect.SignedIn -> currentOnSignedIn()
            }
        }
    }
    LoginScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onSignUp = onSignUp,
        onForgotPassword = onForgotPassword,
        modifier = modifier,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEvent: (LoginUiEvent) -> Unit,
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormScaffold(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_subtitle),
        onBack = onBack,
        modifier = modifier.testTag("login"),
    ) {
        FindoraTextField(
            value = state.email,
            onValueChange = { onEvent(LoginUiEvent.EmailChanged(it)) },
            label = stringResource(R.string.field_email),
            leadingIcon = FindoraIcons.Mail,
            error = state.emailError?.let { stringResource(it.messageRes()) },
            keyboardType = KeyboardType.Email,
            fieldTag = "loginEmail",
        )
        Spacer(Modifier.height(FieldGap))
        PasswordTextField(
            value = state.password,
            onValueChange = { onEvent(LoginUiEvent.PasswordChanged(it)) },
            label = stringResource(R.string.field_password),
            error = if (state.passwordMissing) stringResource(R.string.error_password_blank) else null,
            imeAction = ImeAction.Done,
            onImeAction = { onEvent(LoginUiEvent.Submit) },
            fieldTag = "loginPassword",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledCheckbox(
                text = stringResource(R.string.remember_me),
                checked = state.rememberMe,
                onCheckedChange = { onEvent(LoginUiEvent.RememberMeChanged(it)) },
            )
            TextLink(text = stringResource(R.string.forgot_password_link), onClick = onForgotPassword)
        }
        val formError =
            when {
                state.isLockedOut -> stringResource(R.string.error_locked_out, state.lockoutSecondsRemaining)
                state.attemptsRemaining != null ->
                    stringResource(R.string.error_invalid_credentials, state.attemptsRemaining)
                else -> null
            }
        if (formError != null) {
            FormErrorText(
                text = formError,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("loginError"),
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        PrimaryPillButton(
            text = stringResource(R.string.action_login),
            onClick = { onEvent(LoginUiEvent.Submit) },
            enabled = state.canSubmit,
            modifier = Modifier.testTag("loginSubmit"),
        )
        Spacer(Modifier.height(FindoraTheme.spacing.sm))
        SecondaryPillButton(
            text = stringResource(R.string.action_try_demo),
            onClick = { onEvent(LoginUiEvent.TryDemo) },
            enabled = state.canSubmit,
        )
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.login_no_account),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
            TextLink(
                text = stringResource(R.string.action_sign_up),
                onClick = onSignUp,
                color = FindoraTheme.colors.accent,
            )
        }
    }
}
