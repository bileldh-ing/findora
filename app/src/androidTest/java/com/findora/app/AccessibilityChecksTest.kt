package com.findora.app

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Runs Google's Accessibility Test Framework (the engine behind Accessibility Scanner and Espresso's
 * AccessibilityChecks) on the real accessibility tree of Welcome, Home, listing detail, chat and Settings, as
 * TalkBack sees it through UiAutomation, with a screenshot so the text and image contrast checks run too.
 * Any ERROR (missing label, touch target under 48dp, low contrast, duplicate clickable bounds...) fails the test
 * with the screen, element and message, so a regression shows up as a CI annotation.
 *
 * A screen is scanned up to [SCAN_ATTEMPTS] times and only errors found on every scan are reported: a scan can
 * land mid-transition (the outgoing screen's half-clipped nodes) on a slow emulator, while a real defect is there
 * every time.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val findings = mutableListOf<String>()

    @Test
    fun mainScreens_haveNoAccessibilityErrors() {
        grantNotifications()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists("welcome") || exists("mainShell") }
        if (exists("welcome")) {
            check("Welcome")
            composeRule.onNodeWithTag("welcomeGuest").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        }
        waitFor("home")
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists("card_", prefix = true) }
        check("Home")

        // Semantics clicks: on short emulator screens a scrolled-to node can sit under the floating nav pill.
        composeRule
            .onNodeWithTag(waitForSettledFirstCard())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor("detailList")
        check("Detail")

        // The contact card is a lazy item below the fold: scroll the list until it is composed, then open chat.
        composeRule.onNodeWithTag("detailList").performScrollToNode(hasTestTag("detailMessage"))
        composeRule.onNodeWithTag("detailMessage").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("chat")
        check("Chat")
        composeRule.onNodeWithTag("chatBack").performClick()
        waitFor("detailBack")
        composeRule.onNodeWithTag("detailBack").performClick()
        waitFor("home")

        composeRule.onNodeWithTag("nav_settings").performClick()
        waitFor("settings")
        check("Settings")
        // Leaves the device signed out, as a fresh install, for the other instrumented tests.
        composeRule.onNodeWithText("Log out").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor("welcome")

        assertTrue(
            "Accessibility Test Framework errors:\n" + findings.joinToString("\n"),
            findings.isEmpty(),
        )
    }

    /** Scans the current window and records each ERROR that persists across scans, with the screen's name. */
    private fun check(screen: String) {
        var persistent = scan(screen)
        var attempt = 1
        while (persistent.isNotEmpty() && attempt < SCAN_ATTEMPTS) {
            SystemClock.sleep(RESCAN_DELAY_MILLIS)
            persistent = persistent intersect scan(screen)
            attempt++
        }
        findings += persistent
    }

    /** Runs every ATF check on the current window and returns each ERROR with the screen it was found on. */
    private fun scan(screen: String): Set<String> {
        composeRule.waitForIdle()
        val automation = instrumentation.uiAutomation
        automation.waitForIdle(IDLE_MILLIS, TIMEOUT_MILLIS)
        val root = checkNotNull(automation.rootInActiveWindow) { "No active window on $screen" }
        val hierarchy = AccessibilityHierarchyAndroid.newBuilder(root, composeRule.activity).build()
        val parameters = Parameters()
        automation.takeScreenshot()?.let { parameters.putScreenCapture(BitmapImage(it)) }
        return AccessibilityCheckPreset
            .getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)
            .flatMap { it.runCheckOnHierarchy(hierarchy, null, parameters) }
            .filter { it.type == AccessibilityCheckResultType.ERROR }
            .mapTo(mutableSetOf()) { "[$screen] ${describe(it)}" }
    }

    private fun describe(result: AccessibilityHierarchyCheckResult): String {
        val element = result.element
        val label =
            listOfNotNull(
                element?.resourceName?.toString(),
                element?.contentDescription?.toString(),
                element?.text?.toString(),
            ).joinToString(" / ").ifEmpty { element?.className?.toString().orEmpty() }
        return "${result.sourceCheckClass.simpleName} on '$label': ${result.getMessage(Locale.ENGLISH)}"
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun exists(
        tag: String,
        prefix: Boolean = false,
    ): Boolean {
        val matcher = if (prefix) hasTestTagPrefix(tag) else hasTestTag(tag)
        return composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    /** The feed can re-sort for a few frames after it loads; returns the first card once it stopped moving. */
    private fun waitForSettledFirstCard(): String {
        var first: String? = null
        var since = 0L
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            val current =
                composeRule
                    .onAllNodes(hasTestTagPrefix("card_"))
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.config
                    ?.getOrNull(SemanticsProperties.TestTag)
            val now = SystemClock.uptimeMillis()
            if (current == null || current != first) {
                first = current
                since = now
            }
            current != null && now - since >= SETTLE_MILLIS
        }
        return checkNotNull(first)
    }

    private fun hasTestTagPrefix(prefix: String) =
        SemanticsMatcher("test tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    private fun waitFor(tag: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val IDLE_MILLIS = 500L
        const val SETTLE_MILLIS = 750L
        const val SCAN_ATTEMPTS = 3
        const val RESCAN_DELAY_MILLIS = 1_000L
    }
}
