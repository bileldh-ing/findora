package com.findora.app.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.PasswordTextField
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.auth.common.AuthFormScaffold
import com.findora.app.feature.auth.common.FieldGap
import com.findora.app.feature.auth.common.PasswordRulesChecklist
import com.findora.app.feature.auth.common.messageRes

@Composable
fun SignUpRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SignUpUiEffect.SignedIn -> currentOnSignedIn()
            }
        }
    }
    SignUpScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onLogin = onLogin, modifier = modifier)
}

@Composable
fun SignUpScreen(
    state: SignUpUiState,
    onEvent: (SignUpUiEvent) -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormScaffold(
        title = stringResource(R.string.signup_title),
        subtitle = stringResource(R.string.signup_subtitle),
        onBack = onBack,
        modifier = modifier.testTag("signUp"),
    ) {
        FindoraTextField(
            value = state.name,
            onValueChange = { onEvent(SignUpUiEvent.NameChanged(it)) },
            label = stringResource(R.string.field_name),
            leadingIcon = FindoraIcons.User,
            error = state.nameError?.let { stringResource(it.messageRes()) },
        )
        Spacer(Modifier.height(FieldGap))
        FindoraTextField(
            value = state.email,
            onValueChange = { onEvent(SignUpUiEvent.EmailChanged(it)) },
            label = stringResource(R.string.field_email),
            leadingIcon = FindoraIcons.Mail,
            keyboardType = KeyboardType.Email,
            error =
                when {
                    state.emailTaken -> stringResource(R.string.error_email_taken)
                    else -> state.emailError?.let { stringResource(it.messageRes()) }
                },
        )
        Spacer(Modifier.height(FieldGap))
        PasswordTextField(
            value = state.password,
            onValueChange = { onEvent(SignUpUiEvent.PasswordChanged(it)) },
            label = stringResource(R.string.field_password),
            error = if (state.passwordError) stringResource(R.string.error_password_rules) else null,
            imeAction = ImeAction.Next,
            fieldTag = "signUpPassword",
        )
        PasswordRulesChecklist(
            unmet = state.unmetRules,
            modifier = Modifier.padding(top = FindoraTheme.spacing.xs, start = FindoraTheme.spacing.xs),
        )
        Spacer(Modifier.height(FieldGap))
        PasswordTextField(
            value = state.confirmPassword,
            onValueChange = { onEvent(SignUpUiEvent.ConfirmPasswordChanged(it)) },
            label = stringResource(R.string.field_confirm_password),
            error = if (state.confirmMismatch) stringResource(R.string.error_passwords_mismatch) else null,
            onImeAction = { onEvent(SignUpUiEvent.Submit) },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
        PrimaryPillButton(
            text = stringResource(R.string.signup_title),
            onClick = { onEvent(SignUpUiEvent.Submit) },
            enabled = !state.isSubmitting,
            modifier = Modifier.testTag("signUpSubmit"),
        )
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.signup_have_account),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
            TextLink(
                text = stringResource(R.string.action_login),
                onClick = onLogin,
                color = FindoraTheme.colors.accent,
            )
        }
    }
}
