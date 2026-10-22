package com.findora.app.feature.auth

import app.cash.turbine.test
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.NameError
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.feature.auth.forgot.ForgotError
import com.findora.app.feature.auth.forgot.ForgotPasswordUiEvent
import com.findora.app.feature.auth.forgot.ForgotPasswordViewModel
import com.findora.app.feature.auth.forgot.ForgotStep
import com.findora.app.feature.auth.login.LoginUiEffect
import com.findora.app.feature.auth.login.LoginUiEvent
import com.findora.app.feature.auth.login.LoginViewModel
import com.findora.app.feature.auth.signup.SignUpUiEffect
import com.findora.app.feature.auth.signup.SignUpUiEvent
import com.findora.app.feature.auth.signup.SignUpViewModel
import com.findora.app.feature.auth.welcome.WelcomeUiEffect
import com.findora.app.feature.auth.welcome.WelcomeUiEvent
import com.findora.app.feature.auth.welcome.WelcomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Wall clock driven by the coroutine test scheduler, so delay() and the clock advance together. */
    private val clock = AppClock { Instant.ofEpochMilli(main.dispatcher.scheduler.currentTime) }
    private val auth = FakeAuthRepository(now = clock::now)

    @Test
    fun loginValidatesInlineBeforeCallingTheRepository() =
        runTest(main.dispatcher) {
            val vm = LoginViewModel(auth, clock)
            vm.state.test {
                assertNull(awaitItem().emailError)
                vm.onEvent(LoginUiEvent.Submit)
                val invalid = awaitItem()
                assertEquals(EmailError.Blank, invalid.emailError)
                assertTrue(invalid.passwordMissing)
                vm.onEvent(LoginUiEvent.EmailChanged("ada@"))
                assertEquals(EmailError.Malformed, awaitItem().emailError)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(auth.loginCalls.isEmpty())
        }

    @Test
    fun successfulLoginEmitsSignedInWithRememberMe() =
        runTest(main.dispatcher) {
            val vm = LoginViewModel(auth, clock)
            vm.onEvent(LoginUiEvent.EmailChanged("ada@findora.app"))
            vm.onEvent(LoginUiEvent.PasswordChanged("Secret123"))
            vm.onEvent(LoginUiEvent.RememberMeChanged(false))
            vm.effects.test {
                vm.onEvent(LoginUiEvent.Submit)
                assertEquals(LoginUiEffect.SignedIn, awaitItem())
            }
            assertEquals(listOf(Triple("ada@findora.app", "Secret123", false)), auth.loginCalls)
        }

    @Test
    fun wrongPasswordShowsAttemptsLeftAndClearsThePassword() =
        runTest(main.dispatcher) {
            auth.loginResult = AuthResult.Failure(AuthError.InvalidCredentials(attemptsRemaining = 3))
            val vm = LoginViewModel(auth, clock)
            vm.onEvent(LoginUiEvent.EmailChanged("ada@findora.app"))
            vm.onEvent(LoginUiEvent.PasswordChanged("nope"))
            vm.onEvent(LoginUiEvent.Submit)
            assertEquals(3, vm.state.value.attemptsRemaining)
            assertEquals("", vm.state.value.password)
        }

    @Test
    fun lockoutCountsDownEverySecondWithTheInjectedClock() =
        runTest(main.dispatcher) {
            auth.loginResult = FakeAuthRepository.lockedOut(clock.now().plusSeconds(30))
            val vm = LoginViewModel(auth, clock)
            vm.onEvent(LoginUiEvent.EmailChanged("ada@findora.app"))
            vm.onEvent(LoginUiEvent.PasswordChanged("nope"))
            vm.onEvent(LoginUiEvent.Submit)
            runCurrent()
            assertEquals(30, vm.state.value.lockoutSecondsRemaining)
            assertFalse(vm.state.value.canSubmit)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(20, vm.state.value.lockoutSecondsRemaining)
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, vm.state.value.lockoutSecondsRemaining)
            assertTrue(vm.state.value.canSubmit)
        }

    @Test
    fun demoButtonSignsInThroughTheDemoAccount() =
        runTest(main.dispatcher) {
            val vm = LoginViewModel(auth, clock)
            vm.effects.test {
                vm.onEvent(LoginUiEvent.TryDemo)
                assertEquals(LoginUiEffect.SignedIn, awaitItem())
            }
            assertEquals(1, auth.demoLogins)
        }

    @Test
    fun signUpShowsErrorsOnlyAfterSubmitAndKeepsTheRulesLive() =
        runTest(main.dispatcher) {
            val vm = SignUpViewModel(auth)
            vm.onEvent(SignUpUiEvent.PasswordChanged("abc"))
            val typing = vm.state.value
            assertNull(typing.nameError)
            assertFalse(typing.passwordError)
            assertEquals(setOf(PasswordRule.MinLength, PasswordRule.Digit), typing.unmetRules)
            vm.onEvent(SignUpUiEvent.ConfirmPasswordChanged("abd"))
            vm.onEvent(SignUpUiEvent.Submit)
            val submitted = vm.state.value
            assertEquals(NameError.Blank, submitted.nameError)
            assertEquals(EmailError.Blank, submitted.emailError)
            assertTrue(submitted.passwordError)
            assertTrue(submitted.confirmMismatch)
        }

    @Test
    fun signUpSucceedsOrReportsATakenEmail() =
        runTest(main.dispatcher) {
            val vm = SignUpViewModel(auth)
            vm.onEvent(SignUpUiEvent.NameChanged("Ada"))
            vm.onEvent(SignUpUiEvent.EmailChanged("ada@findora.app"))
            vm.onEvent(SignUpUiEvent.PasswordChanged("Secret123"))
            vm.onEvent(SignUpUiEvent.ConfirmPasswordChanged("Secret123"))
            auth.signUpResult = AuthResult.Failure(AuthError.EmailTaken)
            vm.onEvent(SignUpUiEvent.Submit)
            assertTrue(vm.state.value.emailTaken)
            vm.onEvent(SignUpUiEvent.EmailChanged("ada2@findora.app"))
            assertFalse(vm.state.value.emailTaken)
            auth.signUpResult = AuthResult.Success(auth.account)
            vm.effects.test {
                vm.onEvent(SignUpUiEvent.Submit)
                assertEquals(SignUpUiEffect.SignedIn, awaitItem())
            }
        }

    @Test
    fun forgotPasswordWalksThroughCodeAndNewPassword() =
        runTest(main.dispatcher) {
            val vm = ForgotPasswordViewModel(auth)
            vm.onEvent(ForgotPasswordUiEvent.RequestCode)
            assertEquals(EmailError.Blank, vm.state.value.emailError)
            vm.onEvent(ForgotPasswordUiEvent.EmailChanged("ada@findora.app"))
            vm.onEvent(ForgotPasswordUiEvent.RequestCode)
            assertEquals(ForgotStep.EnterCode, vm.state.value.step)
            assertEquals("654321", vm.state.value.issuedCode)
            vm.onEvent(ForgotPasswordUiEvent.CodeChanged("65a43"))
            assertEquals("6543", vm.state.value.code)
            vm.onEvent(ForgotPasswordUiEvent.ResetPassword)
            assertEquals(ForgotError.CodeBlank, vm.state.value.error)
            vm.onEvent(ForgotPasswordUiEvent.CodeChanged("654321"))
            vm.onEvent(ForgotPasswordUiEvent.NewPasswordChanged("weak"))
            vm.onEvent(ForgotPasswordUiEvent.ResetPassword)
            assertEquals(ForgotError.WeakPassword, vm.state.value.error)
            auth.resetResult = AuthResult.Failure(AuthError.ResetCodeExpired)
            vm.onEvent(ForgotPasswordUiEvent.NewPasswordChanged("Brand123"))
            vm.onEvent(ForgotPasswordUiEvent.ResetPassword)
            assertEquals(ForgotError.CodeExpired, vm.state.value.error)
            auth.resetResult = AuthResult.Success(Unit)
            vm.onEvent(ForgotPasswordUiEvent.ResetPassword)
            assertEquals(ForgotStep.Done, vm.state.value.step)
        }

    @Test
    fun forgotPasswordReportsUnknownAccounts() =
        runTest(main.dispatcher) {
            auth.resetRequestResult = AuthResult.Failure(AuthError.AccountNotFound)
            val vm = ForgotPasswordViewModel(auth)
            vm.onEvent(ForgotPasswordUiEvent.EmailChanged("ghost@findora.app"))
            vm.onEvent(ForgotPasswordUiEvent.RequestCode)
            assertEquals(ForgotStep.EnterEmail, vm.state.value.step)
            assertEquals(ForgotError.AccountNotFound, vm.state.value.error)
        }

    @Test
    fun welcomeStartsGuestAndDemoSessions() =
        runTest(main.dispatcher) {
            val vm = WelcomeViewModel(auth, clock)
            vm.effects.test {
                vm.onEvent(WelcomeUiEvent.ContinueAsGuest)
                assertEquals(WelcomeUiEffect.SignedIn, awaitItem())
                assertTrue(auth.session.value!!.isGuest)
                vm.onEvent(WelcomeUiEvent.TryDemo)
                assertEquals(WelcomeUiEffect.SignedIn, awaitItem())
            }
        }

    @Test
    fun welcomeExplainsAFailedDemoSignIn() =
        runTest(main.dispatcher) {
            auth.loginResult = FakeAuthRepository.lockedOut(clock.now().plusSeconds(12))
            val vm = WelcomeViewModel(auth, clock)
            vm.onEvent(WelcomeUiEvent.TryDemo)
            assertEquals(12, vm.state.value.demoLockoutSeconds)
            auth.loginResult = AuthResult.Failure(AuthError.InvalidCredentials(4))
            vm.onEvent(WelcomeUiEvent.TryDemo)
            assertNull(vm.state.value.demoLockoutSeconds)
            assertTrue(vm.state.value.demoUnavailable)
            assertFalse(vm.state.value.isBusy)
        }
}
