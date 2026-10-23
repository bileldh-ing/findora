package com.findora.app.core.ui.adaptive

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.settings.SettingsScreen
import com.findora.app.feature.settings.SettingsUiState
import com.findora.app.navigation.MainScaffold
import com.findora.app.navigation.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Window size classes and the layouts that follow them, at forced phone, foldable and tablet sizes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class AdaptiveLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val phone = DpSize(411.dp, 891.dp)
    private val foldable = DpSize(673.dp, 841.dp)
    private val tablet = DpSize(1280.dp, 800.dp)

    private fun show(
        size: DpSize,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                FindoraTheme { ProvideAdaptiveLayout { content() } }
            }
        }
    }

    @Test
    fun sizeClassesFollowTheMaterialBreakpoints() {
        val phoneLayout = AdaptiveLayout.from(411.dp, 891.dp)
        assertEquals(WidthClass.Compact, phoneLayout.widthClass)
        assertEquals(2, phoneLayout.gridColumns)
        assertFalse(phoneLayout.useNavigationRail)
        assertFalse(phoneLayout.useTwoPanes)
        val fold = AdaptiveLayout.from(673.dp, 841.dp)
        assertEquals(WidthClass.Medium, fold.widthClass)
        assertEquals(3, fold.gridColumns)
        assertTrue(fold.useNavigationRail)
        assertFalse(fold.useTwoPanes)
        val tabletLayout = AdaptiveLayout.from(1280.dp, 800.dp)
        assertEquals(WidthClass.Expanded, tabletLayout.widthClass)
        assertEquals(4, tabletLayout.gridColumns)
        assertTrue(tabletLayout.useTwoPanes)
        val landscape = AdaptiveLayout.from(891.dp, 411.dp)
        assertTrue(landscape.isShort)
        assertTrue(landscape.useNavigationRail)
    }

    @Test
    fun aBookPostureHingeSplitsTheWindowIntoTwoPanes() {
        val hinge =
            FoldPosture(isSeparating = true, isVertical = true, isHalfOpened = true, start = 336.dp, end = 337.dp)
        assertTrue(AdaptiveLayout.from(673.dp, 841.dp, hinge).useTwoPanes)
        val tabletop = hinge.copy(isVertical = false)
        assertFalse(AdaptiveLayout.from(673.dp, 841.dp, tabletop).useTwoPanes)
    }

    @Test
    fun phonesKeepTheFloatingPill() {
        show(phone) { Shell() }
        compose.onNodeWithTag("navPill").assertExists()
        compose.onNodeWithTag("navRail").assertDoesNotExist()
    }

    @Test
    fun foldablesAndTabletsUseTheNavigationRail() {
        var selected: MainTab? = null
        show(foldable) { Shell(onSelect = { selected = it }) }
        compose.onNodeWithTag("navRail").assertExists()
        compose.onNodeWithTag("navPill").assertDoesNotExist()
        compose.onNodeWithTag(MainTab.Chat.tag).performClick()
        assertEquals(MainTab.Chat, selected)
    }

    @Test
    fun theRailShowsTheUnreadBadge() {
        show(tablet) { Shell(badges = mapOf(MainTab.Chat to 3)) }
        compose.onNodeWithTag("navRail").assertExists()
        compose.onNodeWithTag("${MainTab.Chat.tag}_badge", useUnmergedTree = true).assertExists()
    }

    @Test
    fun expandedWidthShowsListAndDetailSideBySide() {
        show(tablet) { ListDetail() }
        compose.onNodeWithTag("twoPanes").assertExists()
        compose.onNodeWithTag("listContent").assertExists()
        compose.onNodeWithTag("detailContent").assertExists()
    }

    @Test
    fun compactWidthShowsOnlyTheList() {
        show(phone) { ListDetail() }
        compose.onNodeWithTag("singlePane").assertExists()
        compose.onNodeWithTag("listContent").assertExists()
        compose.onNodeWithTag("detailContent").assertDoesNotExist()
    }

    @Test
    fun settingsSplitIntoTwoPanesOnTablets() {
        show(tablet) { Settings() }
        compose.onNodeWithTag("settingsPrimaryPane").assertExists()
        compose.onNodeWithTag("settingsPreferencesPane").assertExists()
    }

    @Test
    fun settingsStayOneColumnOnPhones() {
        show(phone) { Settings() }
        compose.onNodeWithTag("settings").assertExists()
        compose.onNodeWithTag("settingsPrimaryPane").assertDoesNotExist()
    }

    @Composable
    private fun Shell(
        onSelect: (MainTab) -> Unit = {},
        badges: Map<MainTab, Int> = emptyMap(),
    ) {
        MainScaffold(
            useRail = LocalAdaptiveLayout.current.useNavigationRail,
            selected = MainTab.Home,
            onSelect = onSelect,
            badges = badges,
        ) { Text("content") }
    }

    @Composable
    private fun ListDetail() {
        ListDetailLayout(
            twoPanes = LocalAdaptiveLayout.current.useTwoPanes,
            list = { Text("list", Modifier.testTag("listContent")) },
            detail = { Text("detail", Modifier.testTag("detailContent")) },
        )
    }

    @Composable
    private fun Settings() {
        SettingsScreen(
            state = SettingsUiState(),
            onEvent = {},
            onCreateAccount = {},
            onOpenViewings = {},
            onOpenMatchPreferences = {},
            onOpenAbout = {},
            onOpenSystemNotificationSettings = {},
            onOpenDesignSystem = null,
        )
    }
}
