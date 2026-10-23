package com.findora.app.navigation

import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.Session
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeBiometricAvailability
import com.findora.app.core.testing.FakeReferenceDataRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class AppViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val remembered = Session("user-1", isGuest = false, issuedAt = Instant.EPOCH, expiresAt = Instant.MAX)

    private fun start(
        session: Session?,
        biometricLock: Boolean = false,
        canAuthenticate: Boolean = true,
    ): LaunchState {
        val auth = FakeAuthRepository().apply { restoredSession = session }
        val settings = FakeSettingsRepository(AppSettings(biometricLockEnabled = biometricLock))
        return AppViewModel(
            auth,
            settings,
            FakeBiometricAvailability(canAuthenticate),
            ObserveDisplayPreferences(settings, FakeReferenceDataRepository()),
        ).launchState.value
    }

    @Test
    fun noSessionStartsOnWelcome() =
        runTest(main.dispatcher) {
            assertEquals(LaunchState.Ready(FindoraRoute.Welcome), start(session = null))
        }

    @Test
    fun validSessionSkipsWelcome() =
        runTest(main.dispatcher) {
            assertEquals(LaunchState.Ready(FindoraRoute.Main), start(session = remembered))
        }

    @Test
    fun biometricLockGuardsTheRestoredSessionWhenTheDeviceSupportsIt() =
        runTest(main.dispatcher) {
            assertEquals(LaunchState.Ready(FindoraRoute.Unlock), start(remembered, biometricLock = true))
            assertEquals(
                LaunchState.Ready(FindoraRoute.Main),
                start(remembered, biometricLock = true, canAuthenticate = false),
            )
        }
}
