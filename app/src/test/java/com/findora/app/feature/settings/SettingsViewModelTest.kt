package com.findora.app.feature.settings

import app.cash.turbine.test
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeBiometricAvailability
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val matchProfiles = FakeMatchProfileRepository()

    @Test
    fun showsTheAccountAndChangesTheTheme() =
        runTest(main.dispatcher) {
            auth.signIn(remembered = true)
            val vm = SettingsViewModel(auth, settings, FakeBiometricAvailability(true), matchProfiles)
            vm.state.test {
                var state = awaitItem()
                while (state.account == null) state = awaitItem()
                assertEquals("Ada Lovelace", state.account?.displayName)
                assertTrue(state.canToggleBiometric)
                vm.onEvent(SettingsUiEvent.ThemeSelected(ThemeMode.Dark))
                assertEquals(ThemeMode.Dark, awaitItem().themeMode)
                vm.onEvent(SettingsUiEvent.BiometricToggled(true))
                assertTrue(awaitItem().biometricEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun biometricLockNeedsARememberedSessionAndCapableDevice() =
        runTest(main.dispatcher) {
            auth.signIn(remembered = false)
            val vm = SettingsViewModel(auth, settings, FakeBiometricAvailability(true), matchProfiles)
            vm.state.test {
                var state = awaitItem()
                while (state.account == null) state = awaitItem()
                assertFalse(state.canToggleBiometric)
                cancelAndIgnoreRemainingEvents()
            }
            vm.onEvent(SettingsUiEvent.BiometricToggled(true))
            assertFalse(settings.settings.value.biometricLockEnabled)
        }

    @Test
    fun logoutAndDeleteSignOut() =
        runTest(main.dispatcher) {
            auth.signIn()
            settings.setBiometricLockEnabled(true)
            matchProfiles.save(auth.account.id, PreferenceProfile(minBedrooms = 2))
            val vm = SettingsViewModel(auth, settings, FakeBiometricAvailability(true), matchProfiles)
            vm.effects.test {
                vm.onEvent(SettingsUiEvent.DeleteAccountRequested)
                vm.onEvent(SettingsUiEvent.DeleteAccountConfirmed)
                assertEquals(SettingsUiEffect.SignedOut, awaitItem())
                vm.onEvent(SettingsUiEvent.Logout)
                assertEquals(SettingsUiEffect.SignedOut, awaitItem())
            }
            assertEquals(1, auth.deletions)
            // The deleted account's quiz answers go with it.
            assertEquals(listOf(auth.account.id), matchProfiles.cleared)
            assertTrue(matchProfiles.profiles.value.isEmpty())
            assertEquals(1, auth.logouts)
            assertFalse(settings.settings.value.biometricLockEnabled)
        }
}
