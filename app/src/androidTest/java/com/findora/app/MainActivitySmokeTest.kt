package com.findora.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real [MainActivity] (with the real Hilt graph, Room, DataStore and the Android Keystore) on a
 * device or emulator: catches manifest, DI, splash-screen and theme wiring crashes that JVM tests cannot see.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_launches_and_renders_compose_content() {
        composeRule.waitForIdle()
        composeRule.onRoot().assertExists()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
    }

    @Test
    fun guestJourney_fromWelcomeIntoTheAppAndBack() {
        // A fresh install has no session, so the route guard opens Welcome.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasTestTag("mainShell")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isEmpty()) return
        composeRule.onNodeWithText("Continue as guest").performScrollTo().performClick()
        // Signing in lands on Home: the reference header and the seeded feed.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("home")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Your New House").assertIsDisplayed()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTagPrefix("card_")).fetchSemanticsNodes().isNotEmpty()
        }
        // Category chips filter the real Room-backed feed.
        composeRule.onNodeWithTag("chip_1").performClick()
        composeRule.onNodeWithTag("chip_1").assertIsSelected()
        scheduleAViewingFromTheFirstListing()
        // The floating nav pill reaches every tab; Settings signs out back to Welcome.
        composeRule.onNodeWithTag("nav_saved").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("saved")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("settings")).fetchSemanticsNodes().isNotEmpty()
        }
        // A semantics click: once scrolled to the bottom edge the row can sit under the floating nav pill.
        composeRule.onNodeWithText("Log out").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Home card -> detail -> Schedule a viewing -> first free slot -> confirm -> My viewings -> back to Home. */
    private fun scheduleAViewingFromTheFirstListing() {
        // Granted up front so the Android 13+ notification prompt (asked at confirm time) never covers the UI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        waitForTag("card_", prefix = true)
        composeRule.onAllNodes(hasTestTagPrefix("card_"))[0].performClick()
        waitForTag("detailSchedule")
        composeRule.onNodeWithTag("detailSchedule").performClick()
        waitForTag("slot_", prefix = true)
        composeRule.onAllNodes(hasTestTagPrefix("slot_").and(isEnabled()))[0].performScrollTo().performClick()
        composeRule.onNodeWithTag("scheduleConfirm").performClick()
        waitForTag("scheduleBooked")
        composeRule.onNodeWithText("Viewing booked").assertIsDisplayed()
        composeRule.onNodeWithTag("bookedMyViewings").performScrollTo().performClick()
        waitForTag("viewingsList")
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        // Viewings replaced the schedule screen, so back returns to the listing, then Home.
        composeRule.onNodeWithTag("viewingBack").performClick()
        waitForTag("detailBack")
        composeRule.onNodeWithTag("detailBack").performClick()
        waitForTag("home")
    }

    private fun waitForTag(
        tag: String,
        prefix: Boolean = false,
    ) {
        val matcher = if (prefix) hasTestTagPrefix(tag) else hasTestTag(tag)
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hasTestTagPrefix(prefix: String) =
        SemanticsMatcher("test tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
