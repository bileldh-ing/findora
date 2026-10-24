package com.findora.app.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.findora.app.demo.DemoRobot.Companion.BEAT_MILLIS
import com.findora.app.demo.DemoRobot.Companion.LONG_MILLIS
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Demo segments: the OpenStreetMap Locations tab, chat with the demo agent and the notifications center. */
@DemoRecording
@RunWith(AndroidJUnit4::class)
class ConnectDemoTest {
    private val robot = DemoRobot()

    @After
    fun lingerOnTheLastFrame() = robot.hold(BEAT_MILLIS)

    @Test
    fun openStreetMapLocations() =
        with(robot) {
            startSignedIn()
            resetHome()
            chapter("Locations on OpenStreetMap")
            tap("nav_locations")
            waitFor("locations")
            hold(6_000)
            chapter("Carousel and markers stay in sync")
            if (exists("mapCarousel")) {
                swipeHorizontally("mapCarousel")
                hold(2_000)
                swipeHorizontally("mapCarousel")
                hold(2_000)
            }
            chapter("Pan and zoom, then search this area")
            device.findObject(By.res("osmMap"))?.let { map ->
                map.pinchOpen(0.35f, 600)
                hold(2_500)
            }
            tryTap(By.res("searchThisArea"))
            hold(2_000)
            tryTap(By.res("clearArea"))
            chapter("List alternative")
            tap("mapModeToggle")
            hold(LONG_MILLIS)
            tap("mapModeToggle")
            hold(2_000)
            if (tryTap(By.res(Pattern.compile("mapCard_.*")))) {
                waitFor("detailList")
                hold(2_500)
                tap("detailBack")
            }
        }

    @Test
    fun chatWithDemoAgent() =
        with(robot) {
            startSignedIn()
            resetHome()
            tap(By.res(Pattern.compile("card_.*")), "first listing")
            waitFor("detailList")
            chapter("Message the agent")
            scrollUntil("detailList", By.res("detailMessage"), maxSwipes = 16)
            tap("detailMessage")
            waitFor("chat")
            hold(BEAT_MILLIS)
            type("chatInput", "Hi! Is this home still available next month?")
            tap("chatSend")
            chapter("Typing indicator and automated reply")
            waitForReply()
            hideKeyboard()
            chapter("Quick replies")
            tap("quick_VisitThisWeek")
            waitForReply()
            tap("quick_PriceNegotiable")
            waitForReply()
            tap("chatBack")
            tryTap(By.res("detailBack"))
            chapter("Chat tab")
            tap("nav_chat")
            waitFor("conversations")
            hold(LONG_MILLIS)
        }

    @Test
    fun notificationsCenter() =
        with(robot) {
            startSignedIn()
            chapter("Notifications center")
            tap("bell")
            waitFor("notifications")
            hold(LONG_MILLIS)
            if (tryTap(By.res(Pattern.compile("notification_.*")))) {
                hold(LONG_MILLIS)
                back()
                if (!exists("notifications")) tap("bell")
            }
            tryTap(By.res("markAllRead"))
            hold(2_500)
            tap("notificationsBack")
            hold(BEAT_MILLIS)
        }

    /** The demo agent shows its typing dots, then answers after a human-looking delay. */
    private fun DemoRobot.waitForReply() {
        device.wait(Until.hasObject(By.res("typingIndicator")), 6_000)
        hold(BEAT_MILLIS)
        waitGone("typingIndicator", 20_000)
        hold(LONG_MILLIS)
    }
}
