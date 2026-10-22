package com.findora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real [MainActivity] (with the real Hilt application) on a device or emulator and
 * checks it reaches RESUMED with a Compose hierarchy attached: catches manifest, DI, splash-screen
 * and theme wiring crashes that JVM tests cannot see.
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
}
