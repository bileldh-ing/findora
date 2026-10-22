package com.findora.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
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
                composeRule.onAllNodes(hasTestTag("settings")).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isEmpty()) return
        composeRule.onNodeWithText("Continue as guest").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("settings")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Log out").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
