package com.findora.app.feature.auth.unlock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.security.AndroidBiometricAvailability
import com.findora.app.feature.auth.common.FormErrorText

private val LockBadgeSize = 96.dp
private val LockIconSize = 40.dp

@Composable
fun UnlockRoute(
    onUnlock: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current.findFragmentActivity()
    val currentOnUnlock by rememberUpdatedState(onUnlock)
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    var error by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.unlock_prompt_title)
    val subtitle = stringResource(R.string.unlock_prompt_subtitle)
    val showPrompt: () -> Unit =
        remember(activity, title, subtitle) {
            {
                if (activity != null) {
                    error = null
                    authenticate(
                        activity,
                        title,
                        subtitle,
                        onSuccess = { currentOnUnlock() },
                        onError = { error = it },
                    )
                }
            }
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                UnlockUiEffect.SignedOut -> currentOnSignedOut()
            }
        }
    }
    LaunchedEffect(showPrompt) { showPrompt() }
    UnlockScreen(error = error, onUnlockClick = showPrompt, onSignOut = viewModel::signOut, modifier = modifier)
}

@Composable
fun UnlockScreen(
    error: String?,
    onUnlockClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .safeDrawingPadding()
                .padding(horizontal = FindoraTheme.spacing.buttonHorizontal)
                .testTag("unlock"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(LockBadgeSize).clip(CircleShape).background(FindoraTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                FindoraIcons.Fingerprint,
                contentDescription = null,
                tint = FindoraTheme.colors.onBackground,
                modifier = Modifier.size(LockIconSize),
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
        Text(
            text = stringResource(R.string.unlock_title),
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        Text(
            text = stringResource(R.string.unlock_subtitle),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        if (error != null) FormErrorText(text = error)
        Spacer(Modifier.height(FindoraTheme.spacing.xl))
        PrimaryPillButton(text = stringResource(R.string.unlock_action), onClick = onUnlockClick)
        Spacer(Modifier.height(FindoraTheme.spacing.sm))
        TextLink(text = stringResource(R.string.unlock_sign_out), onClick = onSignOut)
    }
}

private fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    // Cancelling the sheet is not an error worth shouting about; the Unlock button stays available.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )
    val info =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AndroidBiometricAvailability.AUTHENTICATORS)
            .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
