package com.findora.app.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.feature.settings.AccountEvent
import com.findora.app.feature.settings.DataEvent
import com.findora.app.feature.settings.PreferenceEvent
import com.findora.app.feature.settings.SettingsScreen
import com.findora.app.feature.settings.SettingsUiEvent
import com.findora.app.feature.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SettingsUiEvent>()
    private var openedAbout = 0
    private var openedSystemSettings = 0

    private fun show(state: SettingsUiState = SettingsUiState(ratesAsOf = LocalDate.of(2026, 10, 23))) {
        compose.setContent {
            FindoraTheme {
                SettingsScreen(
                    state = state,
                    onEvent = { events += it },
                    onCreateAccount = {},
                    onOpenViewings = {},
                    onOpenMatchPreferences = {},
                    onOpenAbout = { openedAbout++ },
                    onOpenSystemNotificationSettings = { openedSystemSettings++ },
                    onOpenDesignSystem = null,
                )
            }
        }
    }

    @Test
    fun themeAndLanguageCanBeSwitched() {
        show()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithTag("settingsLanguage").performScrollTo().performClick()
        compose.onNodeWithTag("languageDialog").assertIsDisplayed()
        compose.onNodeWithText("Français").performClick()
        assertEquals(
            listOf(PreferenceEvent.ThemeSelected(ThemeMode.Dark), PreferenceEvent.LanguageSelected(AppLanguage.French)),
            events,
        )
        compose.onNodeWithTag("languageDialog").assertDoesNotExist()
    }

    @Test
    fun currencyPickerOffersListingCurrencyAndTheFourCurrenciesWithTheRatesDate() {
        show()
        compose.onNodeWithText("Prices are shown in each listing's own currency.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settingsCurrency").performScrollTo().performClick()
        compose.onNodeWithTag("currencyDialog-0").assertIsDisplayed()
        (1..4).forEach { compose.onNodeWithTag("currencyDialog-$it").assertExists() }
        compose.onNodeWithTag("currencyDialog-3").performClick()
        assertEquals(PreferenceEvent.CurrencySelected(Currency.EUR), events.single())
    }

    @Test
    fun aConvertingCurrencyShowsTheIndicativeRatesNote() {
        show(SettingsUiState(displayCurrency = Currency.USD, ratesAsOf = LocalDate.of(2026, 10, 23)))
        compose
            .onNodeWithText("Indicative rates as of Oct 23, 2026", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun unitsNotificationsAndMotionSendTheirEvents() {
        show(SettingsUiState(messageNotifications = false))
        compose.onNodeWithText("Square feet (sq ft)").performScrollTo().performClick()
        compose
            .onNodeWithTag("notifyMessages")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        compose.onNodeWithTag("notifyViewingReminders").assertIsOn()
        compose.onNodeWithTag("settingsSystemNotifications").performScrollTo().performClick()
        compose.onNodeWithTag("settingsReduceMotion").performScrollTo()
        compose.onNodeWithText("On").performClick()
        assertEquals(
            listOf(
                PreferenceEvent.AreaUnitSelected(AreaUnit.Imperial),
                PreferenceEvent.NotificationToggled(NotificationToggle.Messages, true),
                PreferenceEvent.ReduceMotionSelected(ReduceMotion.On),
            ),
            events,
        )
        assertEquals(1, openedSystemSettings)
        compose.onNodeWithTag("settingsTextSize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("currently 100%", substring = true).assertIsDisplayed()
    }

    @Test
    fun privacyAndDataControlsWork() {
        show()
        compose.onNodeWithTag("settingsClearHistory").performScrollTo().performClick()
        compose.onNodeWithTag("settingsClearCache").performScrollTo().performClick()
        compose.onNodeWithTag("settingsExport").performScrollTo().performClick()
        compose.onNodeWithTag("settingsAbout").performScrollTo().performClick()
        compose.onNodeWithTag("settingsPrivacyPolicy").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(DataEvent.ClearSearchHistory, DataEvent.ClearCache, DataEvent.ExportRequested), events)
        assertEquals(1, openedAbout)
    }

    @Test
    fun busyStateDisablesExportAndClearCache() {
        show(SettingsUiState(working = true))
        compose.onNodeWithTag("settingsExport").performScrollTo().performClick()
        compose.onNodeWithTag("settingsClearCache").performScrollTo().performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun logoutStillWorks() {
        show()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        assertEquals(AccountEvent.Logout, events.single())
    }
}
