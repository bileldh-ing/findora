package com.findora.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Package of the app under test, passed by the Gradle plugin (`targetAppId`), so it also follows id suffixes. */
val targetAppId: String
    get() =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId instrumentation argument is missing (run through Gradle, see docs/PERFORMANCE.md)")

private const val TIMEOUT_MILLIS = 15_000L
private const val SCROLL_FLINGS = 2

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
        device.wait(Until.findObject(By.res("welcomeGuest")), TIMEOUT_MILLIS)?.click()
    }
    check(device.wait(Until.hasObject(By.res("home")), TIMEOUT_MILLIS)) { "Home did not open" }
}

/** Flings the listing grid down and back up: the hottest path in the app (paging, Coil, card composition). */
fun MacrobenchmarkScope.scrollHomeFeed() {
    check(device.wait(Until.hasObject(By.res(CARD)), TIMEOUT_MILLIS)) { "Home feed showed no listing card" }
    val grid = device.findObject(By.res("homeGrid"))
    grid.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    repeat(SCROLL_FLINGS) {
        grid.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(SCROLL_FLINGS) {
        grid.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** Opens the first visible listing, scrolls its detail page, then goes back to Home. */
fun MacrobenchmarkScope.openDetailAndGoBack() {
    val card = device.wait(Until.findObject(By.res(CARD)), TIMEOUT_MILLIS) ?: error("No listing card to open")
    card.click()
    check(device.wait(Until.hasObject(By.res("detailList")), TIMEOUT_MILLIS)) { "Detail did not open" }
    val detail = device.findObject(By.res("detailList"))
    detail.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    detail.fling(Direction.DOWN)
    device.waitForIdle()
    device.pressBack()
    check(device.wait(Until.hasObject(By.res("home")), TIMEOUT_MILLIS)) { "Back did not return to Home" }
}
