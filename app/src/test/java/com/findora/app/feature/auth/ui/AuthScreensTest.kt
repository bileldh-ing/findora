package com.findora.app.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.EmailError
import com.findora.app.feature.auth.login.LoginScreen
import com.findora.app.feature.auth.login.LoginUiEvent
import com.findora.app.feature.auth.login.LoginUiState
import com.findora.app.feature.auth.signup.SignUpScreen
import com.findora.app.feature.auth.signup.SignUpUiEvent
import com.findora.app.feature.auth.signup.SignUpUiState
import com.findora.app.feature.auth.welcome.WelcomeScreen
import com.findora.app.feature.auth.welcome.WelcomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AuthScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeShowsTheReferenceContentAndRoutesEveryButton() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FindoraTheme {
                WelcomeScreen(
                    state = WelcomeUiState(),
                    onLogin = { clicks += "login" },
                    onSignUp = { clicks += "signup" },
                    onTryDemo = { clicks += "demo" },
                    onContinueAsGuest = { clicks += "guest" },
                    reduceMotion = true,
                )
            }
        }
        compose.onNodeWithContentDescription("Findora Dream House logo").assertIsDisplayed()
        compose.onNodeWithText("FINDORA").assertIsDisplayed()
        compose.onNodeWithText("Dream House").assertIsDisplayed()
        compose.onNodeWithText("WELCOME").assertExists()
        compose.onNodeWithText("Find your next space, feel at home\nWhere comfort meets convenience").assertExists()
        compose.onNodeWithText("Login").performScrollTo().performClick()
        compose.onNodeWithText("Sign Up").performScrollTo().performClick()
        compose.onNodeWithText("Try demo account").performScrollTo().performClick()
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        assertEquals(listOf("login", "signup", "demo", "guest"), clicks)
    }

    @Test
    fun welcomeRendersInDarkThemeWithAnimations() {
        compose.setContent {
            FindoraTheme(darkTheme = true) {
                WelcomeScreen(
                    state = WelcomeUiState(demoLockoutSeconds = 12),
                    onLogin = {},
                    onSignUp = {},
                    onTryDemo = {},
                    onContinueAsGuest = {},
                    reduceMotion = false,
                )
            }
        }
        compose.onNodeWithTag("welcome").assertIsDisplayed()
        compose.onNodeWithText("Too many attempts. Try again in 12 s").assertExists()
    }

    @Test
    fun loginShowsInlineErrorsAndTheLockoutCountdown() {
        compose.setContent {
            FindoraTheme {
                LoginScreen(
                    state =
                        LoginUiState(
                            emailError = EmailError.Malformed,
                            passwordMissing = true,
                            lockoutSecondsRemaining = 27,
                        ),
                    onEvent = {},
                    onBack = {},
                    onSignUp = {},
                    onForgotPassword = {},
                )
            }
        }
        compose.onNodeWithText("Enter a valid email address").assertExists()
        compose.onNodeWithText("Enter your password").assertExists()
        compose.onNodeWithText("Too many attempts. Try again in 27 s").assertExists()
        compose.onNodeWithText("Login").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun loginShowsAttemptsLeftAfterAWrongPassword() {
        compose.setContent {
            FindoraTheme {
                LoginScreen(
                    state = LoginUiState(attemptsRemaining = 2),
                    onEvent = {},
                    onBack = {},
                    onSignUp = {},
                    onForgotPassword = {},
                )
            }
        }
        compose.onNodeWithText("Incorrect email or password. Attempts left: 2").assertExists()
    }

    @Test
    fun signUpValidatesInlineAsTheUserTypes() {
        compose.setContent {
            var state by remember { mutableStateOf(SignUpUiState()) }
            FindoraTheme {
                SignUpScreen(
                    state = state,
                    onEvent = { event ->
                        state =
                            when (event) {
                                is SignUpUiEvent.NameChanged -> state.copy(name = event.value)
                                is SignUpUiEvent.EmailChanged -> state.copy(email = event.value)
                                is SignUpUiEvent.PasswordChanged -> state.copy(password = event.value)
                                is SignUpUiEvent.ConfirmPasswordChanged -> state.copy(confirmPassword = event.value)
                                SignUpUiEvent.Submit -> state.copy(showErrors = true)
                            }
                    },
                    onBack = {},
                    onLogin = {},
                )
            }
        }
        // The rules checklist is live: each rule reads as met or not met while typing.
        compose.onNodeWithContentDescription("At least 8 characters, Not met yet").assertExists()
        compose.onNodeWithTag("signUpPassword").performTextInput("Secret123")
        compose.onNodeWithContentDescription("At least 8 characters, Met").assertExists()
        compose.onNodeWithContentDescription("At least one number, Met").assertExists()
        // No field error is shown before the first submit.
        compose.onNodeWithText("Enter your name").assertDoesNotExist()
    }

    @Test
    fun signUpSubmitShowsEveryFieldError() {
        compose.setContent {
            var state by remember { mutableStateOf(SignUpUiState()) }
            FindoraTheme {
                SignUpScreen(
                    state = state,
                    onEvent = { event ->
                        state =
                            when (event) {
                                is SignUpUiEvent.PasswordChanged -> state.copy(password = event.value)
                                is SignUpUiEvent.ConfirmPasswordChanged -> state.copy(confirmPassword = event.value)
                                SignUpUiEvent.Submit -> state.copy(showErrors = true)
                                else -> state
                            }
                    },
                    onBack = {},
                    onLogin = {},
                )
            }
        }
        compose.onNodeWithTag("signUpSubmit").performScrollTo().performClick()
        compose.onNodeWithText("Enter your name").assertExists()
        compose.onNodeWithText("Enter your email").assertExists()
        compose.onNodeWithText("Your password does not meet every rule below").assertExists()
    }

    @Test
    fun typingIntoLoginFieldsSendsEvents() {
        val events = mutableListOf<Any>()
        compose.setContent {
            FindoraTheme {
                LoginScreen(
                    state = LoginUiState(),
                    onEvent = { events += it },
                    onBack = {},
                    onSignUp = {},
                    onForgotPassword = {},
                )
            }
        }
        compose.onNodeWithTag("loginEmail").performTextInput("ada@findora.app")
        compose.onNodeWithTag("loginSubmit").performScrollTo().performClick()
        assertEquals(
            listOf(
                LoginUiEvent.EmailChanged("ada@findora.app"),
                LoginUiEvent.Submit,
            ),
            events,
        )
    }
}
