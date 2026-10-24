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

/** Demo segments: the Best Match quiz and explanation, Dream Match, Compare and Ask Findora. */
@DemoRecording
@RunWith(AndroidJUnit4::class)
class MatchDemoTest {
    private val robot = DemoRobot()

    @After
    fun lingerOnTheLastFrame() = robot.hold(BEAT_MILLIS)

    @Test
    fun bestMatchQuiz() =
        with(robot) {
            startSignedIn()
            resetHome()
            chapter("Find my best match")
            if (!tryTap(By.res("matchBannerAction"))) {
                tap("nav_settings")
                tap("settingsMatchPreferences")
            }
            waitFor("matchQuiz")
            hold(BEAT_MILLIS)
            dragBudget()
            tap("quizNext")
            answer("quizBeds_2", "quizBaths_1")
            answer("type_apartment", "type_house")
            answer("area_Fort Kochi", "area_Kochi", "area_Marine Drive")
            answer("lifestyle_quiet", "lifestyle_family")
            hold(BEAT_MILLIS)
            tap("quizNext")
            chapter("Matches ranked with scores")
            waitFor("home")
            hold(LONG_MILLIS)
            scroll("homeGrid")
            hold(BEAT_MILLIS)
            scroll("homeGrid", towardsEnd = false)
            tap(By.res(Pattern.compile("card_.*")), "best match")
            waitFor("detailList")
            chapter("Match ring and why this match")
            scrollUntil("detailList", By.res("matchWhy"))
            hold(BEAT_MILLIS)
            tap("matchWhy")
            hold(4_500)
            back()
            tap("detailBack")
        }

    @Test
    fun dreamMatchDeck() =
        with(robot) {
            startSignedIn()
            chapter("Dream Match")
            tap("nav_saved")
            waitFor("saved")
            scrollUntil("saved", By.res("dreamMatchEntry"))
            tap("dreamMatchEntry")
            waitFor("deck")
            hold(2_500)
            val card = By.res(Pattern.compile("deckCard_.*"))
            chapter("Swipe right to save")
            drag(waitFor(card, "deck card"), 0.45f, 0f)
            hold(BEAT_MILLIS)
            chapter("Swipe left to skip")
            drag(waitFor(card, "deck card"), -0.45f, 0f)
            hold(BEAT_MILLIS)
            chapter("Buttons and undo")
            tap("deckSave")
            hold(BEAT_MILLIS)
            tap("deckUndo")
            hold(BEAT_MILLIS)
            tap("deckSkip")
            hold(BEAT_MILLIS)
            drag(waitFor(card, "deck card"), 0.45f, 0f)
            hold(2_000)
            tap("deckBack")
        }

    @Test
    fun compareSideBySide() =
        with(robot) {
            startSignedIn()
            resetHome()
            chapter("Long press cards to compare")
            val cards = By.res(Pattern.compile("card_.*"))
            waitFor(cards, "listing cards")
            device.findObjects(cards).take(2).forEach {
                longPress(it)
                hold(BEAT_MILLIS)
            }
            waitFor("compareTray")
            hold(2_000)
            tap("compareTrayOpen")
            chapter("Compare table")
            waitFor("compare")
            hold(LONG_MILLIS)
            repeat(3) { scroll("compare") }
            hold(BEAT_MILLIS)
            repeat(3) { scroll("compare", towardsEnd = false) }
            back()
            tryTap(By.res("compareTrayClear"))
        }

    @Test
    fun askFindora() =
        with(robot) {
            startSignedIn()
            resetHome()
            chapter("Ask Findora")
            tap("searchPill")
            waitFor("search")
            tapText(str(R.string.ask_mode_ask))
            hold(2_000)
            type("searchField", str(R.string.ask_example_4))
            hideKeyboard()
            chapter("What Findora understood")
            hold(4_000)
            tap("askApply")
            waitFor("home")
            hold(LONG_MILLIS)
            scroll("homeGrid")
            hold(BEAT_MILLIS)
            resetHome()
        }

    private fun DemoRobot.dragBudget() {
        val slider = waitFor("quizBudgetSlider").visibleBounds
        hold(600)
        val y = slider.centerY()
        device.swipe(slider.right - slider.width() / 20, y, slider.left + slider.width() * 3 / 5, y, 40)
        hold(BEAT_MILLIS)
    }

    private fun DemoRobot.answer(vararg tags: String) {
        tags.forEach { tryTap(By.res(it)) }
        hold(600)
        tap("quizNext")
    }
}
