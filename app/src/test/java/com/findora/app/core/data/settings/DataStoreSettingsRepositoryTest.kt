package com.findora.app.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repository(file: File = File(tmp.root, "settings.preferences_pb")) =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            ),
        )

    @Test
    fun defaultsUseKeralaAndTheSystemThemeAndLanguage() =
        runTest(UnconfinedTestDispatcher()) {
            assertEquals(AppSettings(), repository().settings.first())
        }

    @Test
    fun everySettingPersistsAndEmits() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            repo.settings.test {
                assertEquals(AppSettings(), awaitItem())
                repo.setThemeMode(ThemeMode.Dark)
                assertEquals(ThemeMode.Dark, awaitItem().themeMode)
                repo.setLanguage(AppLanguage.Arabic)
                assertEquals(AppLanguage.Arabic, awaitItem().language)
                repo.setRegion("tunis")
                assertEquals("tunis", awaitItem().regionId)
                repo.setCurrencyOverride(Currency.EUR)
                assertEquals(Currency.EUR, awaitItem().currencyOverride)
                repo.setCurrencyOverride(null)
                assertEquals(null, awaitItem().currencyOverride)
                repo.setOnboardingCompleted(true)
                assertEquals(true, awaitItem().onboardingCompleted)
                repo.setBiometricLockEnabled(true)
                assertEquals(true, awaitItem().biometricLockEnabled)
                repo.setAreaUnit(AreaUnit.Imperial)
                assertEquals(AreaUnit.Imperial, awaitItem().areaUnit)
                repo.setReduceMotion(ReduceMotion.On)
                assertEquals(ReduceMotion.On, awaitItem().reduceMotion)
                repo.setNotificationEnabled(NotificationToggle.Messages, false)
                assertEquals(false, awaitItem().messageNotifications)
                repo.setNotificationEnabled(NotificationToggle.ViewingReminders, false)
                assertEquals(false, awaitItem().viewingReminderNotifications)
                repo.setNotificationEnabled(NotificationToggle.InApp, false)
                assertEquals(false, awaitItem().inAppNotifications)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun blankRegionIsRejected() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            val invalid = runCatching { repo.setRegion(" ") }
            assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
        }
}
