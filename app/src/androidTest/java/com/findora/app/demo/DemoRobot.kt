package com.findora.app.demo

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.findora.app.R
import com.findora.app.widget.FindoraPicksWidgetReceiver
import java.util.Locale
import java.util.regex.Pattern

/**
 * Drives the real app the way a person would, for the demo recordings: every tap, long press, swipe and keystroke
 * is a real input event injected through UiAutomator (so the "show taps" dots appear in the clip), with deliberate
 * pauses between steps. Screens are found by their Compose test tags, which MainActivity exposes as resource ids;
 * dialogs and bottom sheets live in their own windows without those ids, so they are addressed by their text.
 *
 * Compose test rules are deliberately not used: with animations on, the Welcome Ken Burns drift and other
 * infinite transitions would keep the Compose clock busy and make every idle wait time out.
 */
@Suppress("TooManyFunctions")
class DemoRobot {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    val context: Context = instrumentation.targetContext
    private val packageName: String = context.packageName

    init {
        // Animations never let the window go idle; the robot paces itself instead of waiting for idle.
        Configurator.getInstance().waitForIdleTimeout = 0L
        Configurator.getInstance().actionAcknowledgmentTimeout = ACK_MILLIS
    }

    fun str(
        @StringRes id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    /** A string as it reads in another app language (after the in-app language switch). */
    fun strIn(
        languageTag: String,
        @StringRes id: Int,
    ): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return context.createConfigurationContext(configuration).getString(id)
    }

    fun chapter(title: String) {
        Log.i(TAG, "Demo step: $title")
    }

    fun hold(millis: Long = BEAT_MILLIS) = SystemClock.sleep(millis)

    // ---- Launch and session -------------------------------------------------------------------------------

    /** Starts the app in a fresh task, optionally through a VIEW link, and waits for its first screen. */
    fun launch(link: String? = null) {
        grantNotifications()
        val intent =
            if (link == null) {
                checkNotNull(context.packageManager.getLaunchIntentForPackage(packageName))
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        waitForAny("welcome", "mainShell", "detail", "home")
    }

    /** Opens the app signed in with the demo account (signing in from Welcome when needed) on Home. */
    fun startSignedIn() {
        launch()
        if (exists("welcome")) {
            scrollUntil("welcome", By.text(str(R.string.action_try_demo)), maxSwipes = 3)
            tap(By.text(str(R.string.action_try_demo)), "Try demo account")
        }
        waitFor("home")
        hold(SETTLE_MILLIS)
    }

    /** Clears a typed query and any filter left by an earlier segment, so the feed starts complete. */
    fun resetHome() {
        if (exists("clearQuery")) quickTap(By.res("clearQuery"))
        if (exists("filterBadge")) {
            quickTap(By.res("filterButton"))
            waitFor(By.text(str(R.string.action_show_results)), "filter sheet")
            quickTap(By.text(str(R.string.action_reset)))
            quickTap(By.text(str(R.string.action_show_results)))
        }
        hold(SHORT_MILLIS)
    }

    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- Finding ------------------------------------------------------------------------------------------

    fun exists(tag: String): Boolean = device.hasObject(By.res(tag))

    fun exists(selector: BySelector): Boolean = device.hasObject(selector)

    fun waitFor(
        tag: String,
        timeout: Long = TIMEOUT_MILLIS,
    ): UiObject2 = waitFor(By.res(tag), tag, timeout)

    fun waitFor(
        selector: BySelector,
        what: String,
        timeout: Long = TIMEOUT_MILLIS,
    ): UiObject2 = device.wait(Until.findObject(selector), timeout) ?: throw AssertionError("'$what' did not appear")

    fun waitGone(
        tag: String,
        timeout: Long = TIMEOUT_MILLIS,
    ) {
        device.wait(Until.gone(By.res(tag)), timeout)
    }

    private fun waitForAny(vararg tags: String) {
        val deadline = SystemClock.uptimeMillis() + LAUNCH_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (tags.any { exists(it) }) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("None of ${tags.joinToString()} appeared")
    }

    // ---- Gestures -----------------------------------------------------------------------------------------

    fun tap(tag: String) = tap(By.res(tag), tag)

    fun tapText(text: String) = tap(By.text(text), text)

    /** Waits for the target, pauses so the viewer sees it, taps its centre with a real touch, then lets it land. */
    fun tap(
        selector: BySelector,
        what: String,
    ) {
        waitFor(selector, what)
        hold(PRE_TAP_MILLIS)
        retrying(selector, what) { it.click() }
        hold(AFTER_TAP_MILLIS)
    }

    /** Taps when the target shows up within [timeout]; optional steps never fail a segment. */
    fun tryTap(
        selector: BySelector,
        timeout: Long = OPTIONAL_TIMEOUT_MILLIS,
    ): Boolean {
        if (device.wait(Until.findObject(selector), timeout) == null) return false
        hold(PRE_TAP_MILLIS)
        val tapped = runCatching { retrying(selector, selector.toString()) { it.click() } }.isSuccess
        hold(AFTER_TAP_MILLIS)
        return tapped
    }

    private fun quickTap(selector: BySelector) {
        retrying(selector, selector.toString()) { it.click() }
        hold(SHORT_MILLIS)
    }

    fun longPress(obj: UiObject2) {
        hold(PRE_TAP_MILLIS)
        obj.longClick()
        hold(AFTER_TAP_MILLIS)
    }

    /** Two quick taps on the centre of [obj] (the card double-tap that saves a home with a heart burst). */
    fun doubleTap(obj: UiObject2) {
        hold(PRE_TAP_MILLIS)
        val center = obj.visibleCenter
        device.click(center.x, center.y)
        SystemClock.sleep(DOUBLE_TAP_GAP_MILLIS)
        device.click(center.x, center.y)
        hold(AFTER_TAP_MILLIS)
    }

    /** Types [text] one character at a time into the field matched by [selector], like a person typing. */
    fun type(
        selector: BySelector,
        text: String,
        what: String,
    ) {
        tap(selector, what)
        for (end in 1..text.length) {
            retrying(selector, what) { it.text = text.substring(0, end) }
            SystemClock.sleep(KEY_MILLIS)
        }
        hold(SHORT_MILLIS)
    }

    fun type(
        tag: String,
        text: String,
    ) = type(By.res(tag), text, tag)

    /** Hides the soft keyboard if it is showing (Back would otherwise navigate). */
    fun hideKeyboard() {
        val state = device.executeShellCommand("dumpsys input_method")
        if (state.contains("mInputShown=true") || state.contains("isInputViewShown=true")) {
            device.pressBack()
            hold(SHORT_MILLIS)
        }
    }

    fun back() {
        hold(PRE_TAP_MILLIS)
        device.pressBack()
        hold(AFTER_TAP_MILLIS)
    }

    /** A slow vertical drag inside [tag] (or the middle of the screen): up scrolls the content down. */
    fun scroll(
        tag: String? = null,
        towardsEnd: Boolean = true,
        fraction: Float = SCROLL_FRACTION,
    ) {
        val bounds = area(tag)
        val x = bounds.centerX()
        val from = bounds.top + (bounds.height() * if (towardsEnd) SCROLL_FROM else 1 - SCROLL_FROM).toInt()
        val distance = (bounds.height() * fraction).toInt()
        val to = if (towardsEnd) from - distance else from + distance
        device.swipe(x, from, x, to, SCROLL_STEPS)
        hold(SCROLL_SETTLE_MILLIS)
    }

    /** Scrolls [tag] slowly until [target] sits comfortably on screen; returns whether it got there. */
    fun scrollUntil(
        tag: String?,
        target: BySelector,
        maxSwipes: Int = MAX_SWIPES,
        towardsEnd: Boolean = true,
    ): Boolean {
        repeat(maxSwipes) {
            if (isComfortablyVisible(target)) return true
            scroll(tag, towardsEnd)
        }
        return isComfortablyVisible(target)
    }

    /** A slow horizontal drag across [tag]: towards the start edge moves to the next page in left-to-right. */
    fun swipeHorizontally(
        tag: String,
        toLeft: Boolean = true,
        steps: Int = SWIPE_STEPS,
    ) {
        val bounds = area(tag)
        val y = bounds.centerY()
        val left = bounds.left + (bounds.width() * EDGE).toInt()
        val right = bounds.right - (bounds.width() * EDGE).toInt()
        if (toLeft) device.swipe(right, y, left, y, steps) else device.swipe(left, y, right, y, steps)
        hold(SCROLL_SETTLE_MILLIS)
    }

    /** Drags from the centre of [obj] by the given fractions of the screen (the Dream Match card swipes). */
    fun drag(
        obj: UiObject2,
        dxFraction: Float,
        dyFraction: Float,
    ) {
        val center = obj.visibleCenter
        val endX = (center.x + device.displayWidth * dxFraction).toInt()
        val endY = (center.y + device.displayHeight * dyFraction).toInt()
        device.swipe(center.x, center.y, endX, endY, DRAG_STEPS)
        hold(AFTER_TAP_MILLIS)
    }

    fun isComfortablyVisible(selector: BySelector): Boolean {
        val bounds = runCatching { device.findObject(selector)?.visibleBounds }.getOrNull() ?: return false
        return bounds.height() >= MIN_VISIBLE_PX && bounds.top <= device.displayHeight * VISIBLE_TOP_LIMIT
    }

    private fun area(tag: String?): Rect =
        tag?.let { runCatching { device.findObject(By.res(it))?.visibleBounds }.getOrNull() }
            ?: Rect(0, device.displayHeight / SCREEN_TOP_DIVISOR, device.displayWidth, device.displayHeight * 4 / 5)

    private fun retrying(
        selector: BySelector,
        what: String,
        action: (UiObject2) -> Unit,
    ) {
        repeat(STALE_RETRIES) {
            val target = device.wait(Until.findObject(selector), TIMEOUT_MILLIS)
            if (target != null) {
                try {
                    action(target)
                    return
                } catch (stale: StaleObjectException) {
                    Log.w(TAG, "Stale $what, retrying", stale)
                }
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Could not act on '$what'")
    }

    // ---- System surfaces ----------------------------------------------------------------------------------

    /** Opens a link the way another app would (am start), with the app in the background. */
    fun openLinkFromOutside(link: String) {
        device.executeShellCommand("am start -a android.intent.action.VIEW -d $link $packageName")
    }

    fun showNotificationShade(millis: Long) {
        device.openNotification()
        hold(millis)
        device.pressBack()
        hold(SHORT_MILLIS)
    }

    /**
     * Asks the launcher to pin the "Findora picks" widget and accepts its confirmation. Returns false when the
     * launcher does not support pinning or no confirmation appeared.
     */
    fun pinPicksWidget(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val requested =
            manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(ComponentName(context, FindoraPicksWidgetReceiver::class.java), null, null)
        return requested && tryTap(By.text(ADD_WIDGET_PATTERN).clickable(true), WIDGET_DIALOG_TIMEOUT_MILLIS)
    }

    fun documentsUiShowing(): Boolean = device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI_PATTERN)), TIMEOUT_MILLIS)

    companion object {
        const val TAG = "FindoraDemo"
        const val BEAT_MILLIS = 1_500L
        const val LONG_MILLIS = 3_000L
        const val SHORT_MILLIS = 500L
        private const val SETTLE_MILLIS = 1_500L
        private const val PRE_TAP_MILLIS = 600L
        private const val AFTER_TAP_MILLIS = 1_100L
        private const val KEY_MILLIS = 55L
        private const val DOUBLE_TAP_GAP_MILLIS = 90L
        private const val TIMEOUT_MILLIS = 15_000L
        private const val LAUNCH_TIMEOUT_MILLIS = 20_000L
        private const val OPTIONAL_TIMEOUT_MILLIS = 4_000L
        private const val WIDGET_DIALOG_TIMEOUT_MILLIS = 8_000L
        private const val POLL_MILLIS = 250L
        private const val ACK_MILLIS = 300L
        private const val STALE_RETRIES = 4
        private const val SCROLL_STEPS = 70
        private const val SWIPE_STEPS = 45
        private const val DRAG_STEPS = 35
        private const val SCROLL_SETTLE_MILLIS = 700L
        private const val SCROLL_FRACTION = 0.4f
        private const val SCROLL_FROM = 0.75f
        private const val EDGE = 0.15f
        private const val MAX_SWIPES = 10
        private const val MIN_VISIBLE_PX = 90
        private const val VISIBLE_TOP_LIMIT = 0.72f
        private const val SCREEN_TOP_DIVISOR = 4
        private val ADD_WIDGET_PATTERN: Pattern = Pattern.compile("(?i)(add to home screen|add automatically|add)")
        private val DOCUMENTS_UI_PATTERN: Pattern = Pattern.compile(".*documentsui.*")
    }
}
