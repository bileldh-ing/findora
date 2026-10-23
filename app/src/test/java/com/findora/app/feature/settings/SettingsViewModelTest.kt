package com.findora.app.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.CacheCleaner
import com.findora.app.core.domain.repository.DocumentWriter
import com.findora.app.core.domain.repository.UserDataExporter
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeBiometricAvailability
import com.findora.app.core.testing.FakeLanguageController
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakeOwnerListingRepository
import com.findora.app.core.testing.FakeOwnerModeRepository
import com.findora.app.core.testing.FakeReferenceDataRepository
import com.findora.app.core.testing.FakeSearchHistoryRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.testRates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val matchProfiles = FakeMatchProfileRepository()
    private val languages = FakeLanguageController()
    private val history = FakeSearchHistoryRepository()
    private val cache = RecordingCacheCleaner()
    private val exporter = RecordingExporter()
    private val documents = RecordingDocumentWriter()
    private val ownerModes = FakeOwnerModeRepository()
    private val ownerListings = FakeOwnerListingRepository()
    private val now = Instant.parse("2026-10-23T08:00:00Z")

    private fun viewModel(canAuthenticate: Boolean = true) =
        SettingsViewModel(
            auth = auth,
            settings = settings,
            biometric = FakeBiometricAvailability(canAuthenticate),
            matchProfiles = matchProfiles,
            languages = languages,
            referenceData = FakeReferenceDataRepository(),
            searchHistory = history,
            cacheCleaner = cache,
            exporter = exporter,
            documents = documents,
            clock = { now },
            zone = ZoneId.of("Africa/Tunis"),
            ownerModes = ownerModes,
            ownerListings = ownerListings,
        )

    @Test
    fun ownerModeOpensTheDashboardForASignedInUser() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(AccountEvent.OwnerModeToggled(true))
                assertEquals(SettingsUiEffect.OpenOwnerDashboard, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(ownerModes.ownerMode.value)
            vm.state.test {
                var state = awaitItem()
                while (!state.ownerModeActive) state = awaitItem()
                vm.onEvent(AccountEvent.OwnerModeToggled(false))
                assertFalse(awaitItem().ownerModeActive)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun guestsAreAskedToCreateAnAccountInsteadOfOwnerMode() =
        runTest(main.dispatcher) {
            auth.continueAsGuest()
            val vm = viewModel()
            vm.state.test {
                awaitItem()
                vm.onEvent(AccountEvent.OwnerModeToggled(true))
                var state = awaitItem()
                while (!state.ownerNeedsAccount) state = awaitItem()
                assertFalse(state.ownerModeActive)
                vm.onEvent(AccountEvent.OwnerPromptDismissed)
                assertFalse(awaitItem().ownerNeedsAccount)
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(ownerModes.ownerMode.value)
        }

    @Test
    fun deletingTheAccountDeletesItsListingsAndTurnsOwnerModeOff() =
        runTest(main.dispatcher) {
            auth.signIn()
            ownerModes.setOwnerMode(true)
            val vm = viewModel()
            vm.onEvent(AccountEvent.DeleteAccountConfirmed)
            assertEquals(listOf(auth.account.id), ownerListings.deletedFor)
            assertFalse(ownerModes.ownerMode.value)
        }

    @Test
    fun showsTheAccountAndChangesTheTheme() =
        runTest(main.dispatcher) {
            auth.signIn(remembered = true)
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.account == null) state = awaitItem()
                assertEquals("Ada Lovelace", state.account?.displayName)
                assertTrue(state.canToggleBiometric)
                vm.onEvent(PreferenceEvent.ThemeSelected(ThemeMode.Dark))
                assertEquals(ThemeMode.Dark, awaitItem().themeMode)
                vm.onEvent(PreferenceEvent.BiometricToggled(true))
                assertTrue(awaitItem().biometricEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun languageIsPersistedAndAppliedToTheWholeApp() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertEquals(AppLanguage.System, loaded().language)
                vm.onEvent(PreferenceEvent.LanguageSelected(AppLanguage.Arabic))
                assertEquals(AppLanguage.Arabic, awaitItem().language)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(AppLanguage.Arabic, settings.settings.value.language)
            assertEquals(listOf(AppLanguage.Arabic), languages.applied)
            // Choosing the language already in effect does nothing (no needless activity recreation).
            vm.onEvent(PreferenceEvent.LanguageSelected(AppLanguage.Arabic))
            assertEquals(listOf(AppLanguage.Arabic), languages.applied)
        }

    @Test
    fun theInitialLanguageComesFromTheAppLocaleNotOnlyFromSettings() =
        runTest(main.dispatcher) {
            // Changed in Android 13+ system settings: AppCompat reports it even though DataStore does not know it.
            languages.language = AppLanguage.French
            viewModel().state.test {
                assertEquals(AppLanguage.French, loaded().language)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun displayCurrencyCanBeChosenAndResetToTheListingCurrency() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val state = loaded()
                assertNull(state.displayCurrency)
                assertEquals(testRates.asOf, state.ratesAsOf)
                vm.onEvent(PreferenceEvent.CurrencySelected(Currency.EUR))
                assertEquals(Currency.EUR, awaitItem().displayCurrency)
                vm.onEvent(PreferenceEvent.CurrencySelected(null))
                assertNull(awaitItem().displayCurrency)
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(settings.settings.value.currencyOverride)
            assertEquals(listOf(null, Currency.INR, Currency.USD, Currency.EUR, Currency.TND), DisplayCurrencyOptions)
        }

    @Test
    fun areaUnitSwitchesBetweenMetricAndImperial() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertEquals(AreaUnit.Metric, loaded().areaUnit)
                vm.onEvent(PreferenceEvent.AreaUnitSelected(AreaUnit.Imperial))
                assertEquals(AreaUnit.Imperial, awaitItem().areaUnit)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(AreaUnit.Imperial, settings.settings.value.areaUnit)
        }

    @Test
    fun reduceMotionOverrideIsStored() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertEquals(ReduceMotion.System, loaded().reduceMotion)
                vm.onEvent(PreferenceEvent.ReduceMotionSelected(ReduceMotion.On))
                assertEquals(ReduceMotion.On, awaitItem().reduceMotion)
                vm.onEvent(PreferenceEvent.ReduceMotionSelected(ReduceMotion.Off))
                assertEquals(ReduceMotion.Off, awaitItem().reduceMotion)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun eachNotificationToggleIsIndependent() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val initial = loaded()
                assertTrue(NotificationToggle.entries.all(initial::isEnabled))
                vm.onEvent(PreferenceEvent.NotificationToggled(NotificationToggle.Messages, false))
                val messagesOff = awaitItem()
                assertFalse(messagesOff.messageNotifications)
                assertTrue(messagesOff.viewingReminderNotifications && messagesOff.inAppNotifications)
                vm.onEvent(PreferenceEvent.NotificationToggled(NotificationToggle.ViewingReminders, false))
                assertFalse(awaitItem().viewingReminderNotifications)
                vm.onEvent(PreferenceEvent.NotificationToggled(NotificationToggle.InApp, false))
                assertFalse(awaitItem().inAppNotifications)
                vm.onEvent(PreferenceEvent.NotificationToggled(NotificationToggle.Messages, true))
                assertTrue(awaitItem().messageNotifications)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearingSearchHistoryRemovesOnlyTheCurrentUsersEntries() =
        runTest(main.dispatcher) {
            auth.signIn()
            history.record(auth.account.id, "2 bhk kochi")
            history.record(AuthRepository.GUEST_USER_ID, "studio tunis")
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(DataEvent.ClearSearchHistory)
                assertEquals(SettingsUiEffect.Show(SettingsMessage.SearchHistoryCleared), awaitItem())
            }
            assertTrue(history.observeRecent(auth.account.id).first().isEmpty())
            assertEquals(1, history.observeRecent(AuthRepository.GUEST_USER_ID).first().size)
        }

    @Test
    fun clearingTheCacheReportsIt() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(DataEvent.ClearCache)
                assertEquals(SettingsUiEffect.Show(SettingsMessage.CacheCleared), awaitItem())
            }
            assertEquals(1, cache.clears)
            assertFalse(vm.state.value.working)
        }

    @Test
    fun exportAsksForADocumentThenWritesTheUsersJsonThere() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(DataEvent.ExportRequested)
                assertEquals(SettingsUiEffect.CreateExportDocument("findora-data-2026-10-23.json"), awaitItem())
                vm.onEvent(DataEvent.ExportDestinationChosen("content://docs/export.json"))
                assertEquals(SettingsUiEffect.Show(SettingsMessage.ExportSaved), awaitItem())
            }
            assertEquals(listOf(auth.account.id), exporter.exported)
            assertEquals(mapOf("content://docs/export.json" to "{\"user\":\"user-1\"}"), documents.written)
        }

    @Test
    fun aCancelledPickerWritesNothingAndAFailedWriteIsReported() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(DataEvent.ExportDestinationChosen(null))
            assertTrue(exporter.exported.isEmpty())
            documents.fail = true
            vm.effects.test {
                vm.onEvent(DataEvent.ExportDestinationChosen("content://docs/readonly.json"))
                assertEquals(SettingsUiEffect.Show(SettingsMessage.ExportFailed), awaitItem())
            }
            assertEquals(listOf(AuthRepository.GUEST_USER_ID), exporter.exported)
        }

    @Test
    fun biometricLockNeedsARememberedSessionAndCapableDevice() =
        runTest(main.dispatcher) {
            auth.signIn(remembered = false)
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.account == null) state = awaitItem()
                assertFalse(state.canToggleBiometric)
                cancelAndIgnoreRemainingEvents()
            }
            vm.onEvent(PreferenceEvent.BiometricToggled(true))
            assertFalse(settings.settings.value.biometricLockEnabled)
        }

    @Test
    fun logoutAndDeleteSignOut() =
        runTest(main.dispatcher) {
            auth.signIn()
            settings.setBiometricLockEnabled(true)
            matchProfiles.save(auth.account.id, PreferenceProfile(minBedrooms = 2))
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(AccountEvent.DeleteAccountRequested)
                vm.onEvent(AccountEvent.DeleteAccountConfirmed)
                assertEquals(SettingsUiEffect.SignedOut, awaitItem())
                vm.onEvent(AccountEvent.Logout)
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

/** Skips the placeholder state until the settings and the rate table are in. */
private suspend fun ReceiveTurbine<SettingsUiState>.loaded(): SettingsUiState {
    var state = awaitItem()
    while (state.ratesAsOf == null) state = awaitItem()
    return state
}

private class RecordingCacheCleaner : CacheCleaner {
    var clears = 0

    override suspend fun clear(): Long {
        clears++
        return 1_024
    }
}

private class RecordingExporter : UserDataExporter {
    val exported = mutableListOf<String>()

    override suspend fun export(userId: String): String {
        exported += userId
        return "{\"user\":\"$userId\"}"
    }
}

private class RecordingDocumentWriter : DocumentWriter {
    val written = mutableMapOf<String, String>()
    var fail = false

    override suspend fun write(
        uri: String,
        text: String,
    ) {
        if (fail) throw IOException("read-only")
        written[uri] = text
    }
}
