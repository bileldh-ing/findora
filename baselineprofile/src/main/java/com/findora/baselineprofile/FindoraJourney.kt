package com.findora.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Package of the app under test, passed by the Gradle plugin (`targetAppId`), so it also follows id suffixes. */
val targetAppId: String
    get() =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId instrumentation argument is missing (run through Gradle, see docs/PERFORMANCE.md)")

private const val TIMEOUT_MILLIS = 15_000L
private const val SHORT_WAIT_MILLIS = 2_000L
private const val SCROLL_FLINGS = 2
private const val STALE_RETRIES = 3

/** Gestures start a fifth of the screen width in from each edge (system back gesture, floating nav pill). */
private const val GESTURE_MARGIN_DIVISOR = 5
private val CARD = Pattern.compile("card_.*")
private val FIRST_SCREEN = Pattern.compile("welcome|home")

/**
 * Waits until the app shows Welcome or Home. A fresh install opens Welcome, which the journey leaves as a guest;
 * the guest session survives process restarts, so later iterations land on Home directly.
 */
fun MacrobenchmarkScope.waitForFirstScreen() {
    check(device.wait(Until.hasObject(By.res(FIRST_SCREEN)), TIMEOUT_MILLIS)) { "Neither Welcome nor Home appeared" }
}

/** Welcome -> "Continue as guest" -> Home, when the app opened on Welcome. */
fun MacrobenchmarkScope.continueAsGuestIfNeeded() {
    if (device.hasObject(By.res("welcome"))) {
        // On short screens the link sits below the fold of Welcome's scrolling panel.
        val guest =
            device.wait(Until.findObject(By.res("welcomeGuest")), SHORT_WAIT_MILLIS)
                ?: device
                    .findObject(By.scrollable(true))
                    ?.scrollUntil(Direction.DOWN, Until.findObject(By.res("welcomeGuest")))
        checkNotNull(guest) { "Continue as guest not found on Welcome" }.click()
    }
    check(device.wait(Until.hasObject(By.res("home")), TIMEOUT_MILLIS)) { "Home did not open" }
}

/** Flings the listing grid down and back up: the hottest path in the app (paging, Coil, card composition). */
fun MacrobenchmarkScope.scrollHomeFeed() {
    check(device.wait(Until.hasObject(By.res(CARD)), TIMEOUT_MILLIS)) { "Home feed showed no listing card" }
    repeat(SCROLL_FLINGS) {
        device.flingFresh(By.res("homeGrid"), Direction.DOWN)
        device.waitForIdle()
    }
    repeat(SCROLL_FLINGS) {
        device.flingFresh(By.res("homeGrid"), Direction.UP)
        device.waitForIdle()
    }
}

/** Opens the first visible listing, scrolls its detail page, then goes back to Home. */
fun MacrobenchmarkScope.openDetailAndGoBack() {
    device.actOnFresh(By.res(CARD)) { it.click() }
    check(device.wait(Until.hasObject(By.res("detailList")), TIMEOUT_MILLIS)) { "Detail did not open" }
    device.flingFresh(By.res("detailList"), Direction.DOWN)
    device.waitForIdle()
    device.pressBack()
    check(device.wait(Until.hasObject(By.res("home")), TIMEOUT_MILLIS)) { "Back did not return to Home" }
}

/** Flings the node matched by [selector], keeping clear of the screen edges (back gesture, floating nav). */
private fun UiDevice.flingFresh(
    selector: BySelector,
    direction: Direction,
) = actOnFresh(selector) {
    it.setGestureMargin(displayWidth / GESTURE_MARGIN_DIVISOR)
    it.fling(direction)
}

/**
 * Finds [selector] afresh and acts on it, looking it up again when Compose replaced the node in between
 * (StaleObjectException): lazy grids and the collapsing Home header recompose while the journey runs.
 */
private fun UiDevice.actOnFresh(
    selector: BySelector,
    action: (UiObject2) -> Unit,
) {
    var lastStale: StaleObjectException? = null
    repeat(STALE_RETRIES) {
        val target = wait(Until.findObject(selector), TIMEOUT_MILLIS) ?: error("$selector did not appear")
        try {
            action(target)
            return
        } catch (stale: StaleObjectException) {
            lastStale = stale
            waitForIdle()
        }
    }
    throw checkNotNull(lastStale)
}
