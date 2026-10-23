package com.findora.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile from the critical user journey: cold start -> Home feed scroll ->
 * listing detail -> back. Run with `./gradlew :app:generateBaselineProfile` on an API 33+ emulator or device;
 * the result is written to app/src/release/generated/baselineProfiles (see docs/PERFORMANCE.md).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            waitForFirstScreen()
            continueAsGuestIfNeeded()
            scrollHomeFeed()
            openDetailAndGoBack()
        }
}
