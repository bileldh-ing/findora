package com.findora.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.findora.app.core.designsystem.theme.FindoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// No TLS is used in these tests; skipping Conscrypt also lets them run on ARM64 Linux hosts.
@ConscryptMode(ConscryptMode.Mode.OFF)
class FindoraNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun launchesToThemedBootstrapScreen() {
        compose.setContent { FindoraTheme { FindoraNavHost(showDesignSystem = true) } }
        compose.onNodeWithTag("bootstrap").assertIsDisplayed()
        compose.onNodeWithText("WELCOME").assertIsDisplayed()
    }

    @Test
    fun designSystemButtonNavigatesAndBackReturns() {
        compose.setContent { FindoraTheme { FindoraNavHost(showDesignSystem = true) } }
        compose.onNodeWithText("Open design system").performClick()
        compose.onNodeWithTag("designSystem").assertIsDisplayed()
        compose.onAllNodesWithText("Back")[0].performClick()
        compose.onNodeWithTag("bootstrap").assertIsDisplayed()
    }

    @Test
    fun releaseBuildsRenderNoDesignSystemButton() {
        compose.setContent { FindoraTheme(darkTheme = true) { FindoraNavHost(showDesignSystem = false) } }
        compose.onNodeWithTag("bootstrap").assertIsDisplayed()
        compose.onNodeWithText("Open design system").assertDoesNotExist()
    }
}
