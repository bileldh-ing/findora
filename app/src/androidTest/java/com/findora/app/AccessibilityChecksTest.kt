package com.findora.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
            composeRule.onNodeWithTag("welcomeGuest").performScrollTo().performClick()
        }
        waitFor("home")
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists("card_", prefix = true) }
        check("Home")

        composeRule
            .onAllNodes(hasTestTagPrefix("card_"))
            .onFirst()
            .performScrollTo()
            .performClick()
        waitFor("detailList")
        check("Detail")

        composeRule.onNodeWithTag("detailMessage").performScrollTo().performClick()
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

    /** Runs every ATF check on the current window and records each ERROR with the screen it was found on. */
    private fun check(screen: String) {
        composeRule.waitForIdle()
        val automation = instrumentation.uiAutomation
        automation.waitForIdle(IDLE_MILLIS, TIMEOUT_MILLIS)
        val root = checkNotNull(automation.rootInActiveWindow) { "No active window on $screen" }
        val hierarchy = AccessibilityHierarchyAndroid.newBuilder(root, composeRule.activity).build()
        val parameters = Parameters()
        automation.takeScreenshot()?.let { parameters.putScreenCapture(BitmapImage(it)) }
        AccessibilityCheckPreset
            .getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)
            .flatMap { it.runCheckOnHierarchy(hierarchy, null, parameters) }
            .filter { it.type == AccessibilityCheckResultType.ERROR }
            .forEach { findings += "[$screen] ${describe(it)}" }
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
    }
}
