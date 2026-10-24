package com.findora.app.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.findora.app.R
import com.findora.app.demo.DemoRobot.Companion.BEAT_MILLIS
import com.findora.app.demo.DemoRobot.Companion.LONG_MILLIS
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Demo segments: Settings (language with Arabic RTL, currency, units, notifications), export, About, owner, dark. */
@DemoRecording
@RunWith(AndroidJUnit4::class)
class SettingsDemoTest {
    private val robot = DemoRobot()

    @After
    fun lingerOnTheLastFrame() = robot.hold(BEAT_MILLIS)

    private fun demo(script: DemoRobot.() -> Unit) = robot.script()

    @Test
    fun languageSwitchWithArabicRtl() =
        demo {
            openSettings()
            chapter("Switch the app to Arabic")
            scrollUntil("settings", By.res("settingsLanguage"))
            tap("settingsLanguage")
            tapText(str(R.string.language_arabic))
            waitFor("settings")
            hold(LONG_MILLIS)
            chapter("Right-to-left Home and listing")
            tap("nav_home")
            waitFor("home")
            hold(LONG_MILLIS)
            scroll("homeGrid")
            tap(By.res(Pattern.compile("card_.*")), "a listing")
            waitFor("detailList")
            hold(LONG_MILLIS)
            tap("detailBack")
            chapter("Back to English")
            tap("nav_settings")
            waitFor("settings")
            scrollUntil("settings", By.res("settingsLanguage"))
            tap("settingsLanguage")
            tapText(str(R.string.language_english))
            waitFor("settings")
            hold(2_000)
        }

    @Test
    fun currencyUnitsAndNotificationToggles() =
        demo {
            openSettings()
            chapter("Display currency")
            scrollUntil("settings", By.res("settingsCurrency"))
            tap("settingsCurrency")
            tap(By.textStartsWith("EUR"), "EUR")
            hold(BEAT_MILLIS)
            chapter("Units")
            scrollUntil("settings", By.res("settingsAreaUnit"))
            tapSegment("settingsAreaUnit", index = 1, count = 2)
            hold(BEAT_MILLIS)
            tap("nav_home")
            waitFor("home")
            hold(LONG_MILLIS)
            chapter("Notification toggles")
            tap("nav_settings")
            scrollUntil("settings", By.res("notifyMessages"))
            for (toggle in listOf("notifyMessages", "notifyInApp")) {
                tap(toggle)
                hold(BEAT_MILLIS)
                tap(toggle)
            }
            chapter("Back to the listing currency and metres")
            scrollUntil("settings", By.res("settingsCurrency"), towardsEnd = false)
            tap("settingsCurrency")
            tapText(str(R.string.currency_listing))
            scrollUntil("settings", By.res("settingsAreaUnit"))
            tapSegment("settingsAreaUnit", index = 0, count = 2)
            hold(BEAT_MILLIS)
        }

    @Test
    fun exportMyData() =
        demo {
            openSettings()
            chapter("Export my data")
            scrollUntil("settings", By.res("settingsExport"), maxSwipes = 14)
            tap("settingsExport")
            if (documentsUiShowing()) {
                hold(2_500)
                tryTap(By.text(SAVE_PATTERN).clickable(true), 6_000)
            }
            waitFor("settings")
            hold(LONG_MILLIS)
        }

    @Test
    fun aboutScreen() =
        demo {
            openSettings()
            chapter("About Findora")
            scrollUntil("settings", By.res("settingsAbout"), maxSwipes = 16)
            tap("settingsAbout")
            waitFor("about")
            hold(2_500)
            repeat(5) { scroll("aboutList") }
            hold(BEAT_MILLIS)
            tap("aboutBack")
        }

    @Test
    fun ownerMode() =
        demo {
            openSettings()
            chapter("Owner mode")
            scrollUntil("settings", By.res("settingsOwnerMode"), maxSwipes = 14)
            if (!exists("settingsOwnerDashboard")) tap("settingsOwnerMode")
            // Switching owner mode on opens the dashboard; when it was already on, the shortcut row does.
            if (!appears("ownerDashboard", 4_000)) {
                scrollUntil("settings", By.res("settingsOwnerDashboard"))
                tap("settingsOwnerDashboard")
            }
            waitFor("ownerDashboard")
            hold(LONG_MILLIS)
            chapter("List your property")
            tap("ownerCreateListing")
            waitFor("listingWizard")
            hold(BEAT_MILLIS)
            tap("type_apartment")
            type("field_title", "Sunny two-bedroom flat by Marine Drive")
            type("field_description", "Bright corner flat with a sea-facing balcony, ten minutes from the metro.")
            hideKeyboard()
            hold(BEAT_MILLIS)
            tap("wizardNext")
            hold(BEAT_MILLIS)
            tryTap(By.res("region_kerala"))
            if (scrollUntil("listingWizard", By.res("locality_Marine Drive"))) tap("locality_Marine Drive")
            hold(LONG_MILLIS)
            chapter("Drafts autosave: back to the dashboard")
            tap("wizardBack")
            tap("wizardBack")
            hold(LONG_MILLIS)
            if (exists("ownerDashboardList")) repeat(2) { scroll("ownerDashboardList") }
            hold(BEAT_MILLIS)
        }

    @Test
    fun darkMode() =
        demo {
            openSettings()
            chapter("Dark theme")
            scrollUntil("settings", By.res("settingsTheme"))
            tapSegment("settingsTheme", index = 2, count = 3)
            hold(LONG_MILLIS)
            tap("nav_home")
            waitFor("home")
            hold(2_000)
            scroll("homeGrid")
            tap(By.res(Pattern.compile("card_.*")), "a listing")
            waitFor("detailList")
            hold(2_000)
            scroll("detailList")
            hold(BEAT_MILLIS)
            tap("detailBack")
            tap("nav_locations")
            hold(4_000)
            tap("nav_chat")
            hold(2_000)
            chapter("Back to the system theme")
            tap("nav_settings")
            scrollUntil("settings", By.res("settingsTheme"), towardsEnd = false)
            tapSegment("settingsTheme", index = 0, count = 3)
            hold(2_000)
        }

    private fun DemoRobot.openSettings() {
        startSignedIn()
        tap("nav_settings")
        waitFor("settings")
        hold(BEAT_MILLIS)
    }

    private companion object {
        val SAVE_PATTERN: Pattern = Pattern.compile("(?i)save")
    }
}
