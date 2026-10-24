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

/** Demo segments: Welcome and sign-in, the Home feed, search, filters, favourites, saved searches and the widget. */
@DemoRecording
@RunWith(AndroidJUnit4::class)
class DiscoverDemoTest {
    private val robot = DemoRobot()

    @After
    fun lingerOnTheLastFrame() = robot.hold(BEAT_MILLIS)

    private fun demo(script: DemoRobot.() -> Unit) = robot.script()

    @Test
    fun welcomeAndDemoSignIn() =
        demo {
            launch()
            if (!exists("welcome")) signOut()
            chapter("Welcome")
            waitFor("welcome")
            hold(4_000)
            scroll("welcome", fraction = 0.25f)
            hold(BEAT_MILLIS)
            chapter("Log in with the demo account")
            tapText(str(R.string.action_login))
            waitFor("login")
            type("loginEmail", DEMO_EMAIL)
            type("loginPassword", DEMO_PASSWORD)
            hideKeyboard()
            tap("loginSubmit")
            waitFor("home")
            hold(LONG_MILLIS)
        }

    @Test
    fun homeFeedAndFloatingNav() =
        demo {
            startSignedIn()
            resetHome()
            chapter("Home feed")
            hold(2_500)
            repeat(3) { scroll("homeGrid") }
            hold(BEAT_MILLIS)
            repeat(3) { scroll("homeGrid", towardsEnd = false) }
            chapter("Category chips")
            tap("chip_1")
            hold(BEAT_MILLIS)
            tap("chip_2")
            hold(BEAT_MILLIS)
            tap("chip_0")
            chapter("Floating navigation pill")
            for (tab in listOf("nav_locations", "nav_saved", "nav_chat", "nav_settings", "nav_home")) {
                tap(tab)
                hold(BEAT_MILLIS)
            }
        }

    @Test
    fun searchWithSuggestions() =
        demo {
            startSignedIn()
            resetHome()
            chapter("Search")
            tap("searchPill")
            waitFor("search")
            type("searchField", "Fort Ko")
            hold(2_000)
            hideKeyboard()
            if (!tryTap(By.res(Pattern.compile("suggestion_.*")))) tapText("Fort Kochi")
            waitFor("home")
            hold(2_500)
            scroll("homeGrid")
            hold(BEAT_MILLIS)
            scroll("homeGrid", towardsEnd = false)
            tryTap(By.res("clearQuery"))
            hold(BEAT_MILLIS)
        }

    @Test
    fun filtersSheet() =
        demo {
            startSignedIn()
            resetHome()
            chapter("Filters")
            tap("filterButton")
            waitFor(By.text(str(R.string.filter_title)), "filter sheet")
            hold(BEAT_MILLIS)
            tap(By.text(str(R.string.filter_at_least, 2)), "2+ bedrooms")
            scrollUntil(null, By.text(str(R.string.filter_furnished)))
            hold(BEAT_MILLIS)
            scrollUntil(null, By.text(str(R.string.sort_price_low)))
            tapText(str(R.string.sort_price_low))
            hold(BEAT_MILLIS)
            scrollUntil(null, By.text(str(R.string.action_show_results)))
            tapText(str(R.string.action_show_results))
            waitFor("filterBadge")
            hold(2_500)
            repeat(2) { scroll("homeGrid") }
            hold(BEAT_MILLIS)
            resetHome()
        }

    @Test
    fun favourites() =
        demo {
            startSignedIn()
            resetHome()
            chapter("Double-tap a card to save it")
            val cards = By.res(Pattern.compile("card_.*"))
            waitFor(cards, "a listing card")
            doubleTap(findAll(cards).first())
            hold(BEAT_MILLIS)
            chapter("Heart another home")
            findAll(By.res(Pattern.compile("heart_.*"))).getOrNull(1)?.let {
                hold(600)
                it.click()
            }
            hold(BEAT_MILLIS)
            chapter("Saved tab")
            tap("nav_saved")
            waitFor("saved")
            hold(2_500)
            tap(By.res(Pattern.compile("card_.*")), "a saved home")
            waitFor("detailList")
            hold(2_500)
            tap("detailBack")
            hold(BEAT_MILLIS)
        }

    @Test
    fun savedSearchesAndAlerts() =
        demo {
            startSignedIn()
            resetHome()
            chapter("Filter, then save the search")
            tap("filterButton")
            waitFor(By.text(str(R.string.filter_title)), "filter sheet")
            scrollUntil(null, By.text(str(R.string.filter_verified)))
            tapText(str(R.string.filter_verified))
            scrollUntil(null, By.text(str(R.string.action_show_results)))
            tapText(str(R.string.action_show_results))
            waitFor("filterBadge")
            tap("saveSearch")
            type(By.clazz("android.widget.EditText"), "Verified homes in Kochi", "search name")
            hideKeyboard()
            tapText(str(R.string.saved_search_save))
            hold(2_500)
            chapter("Saved > Searches with alerts")
            tap("nav_saved")
            tapText(str(R.string.saved_tab_searches))
            waitFor(By.res(Pattern.compile("savedSearch-.*")), "a saved search")
            hold(BEAT_MILLIS)
            tryTap(By.text(str(R.string.saved_search_frequency_instant)))
            hold(BEAT_MILLIS)
            tap(By.res(Pattern.compile("savedSearchRun-.*")), "run the search")
            waitFor("home")
            hold(2_500)
            resetHome()
        }

    @Test
    fun picksWidget() =
        demo {
            startSignedIn()
            chapter("Pin the Findora picks widget")
            hold(BEAT_MILLIS)
            val pinned = pinPicksWidget()
            hold(BEAT_MILLIS)
            device.pressHome()
            hold(6_000)
            if (pinned) {
                chapter("Open a home from the widget")
                tryTap(By.text(WIDGET_ROW_PATTERN))
                hold(LONG_MILLIS)
            }
        }

    private fun DemoRobot.signOut() {
        waitFor("mainShell")
        tap("nav_settings")
        scrollUntil("settings", By.text(str(R.string.action_logout)))
        tapText(str(R.string.action_logout))
        waitFor("welcome")
    }

    private companion object {
        const val DEMO_EMAIL = "demo@findora.app"
        const val DEMO_PASSWORD = "Demo1234"
        val WIDGET_ROW_PATTERN: Pattern = Pattern.compile(".*(House|Haven|Nest|Suites|Apartments|Villa|Residency).*")
    }
}
