package com.findora.app.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import kotlinx.serialization.Serializable

val NavPillHeight = 68.dp
val NavPillMargin = 12.dp
private val IndicatorWidth = 22.dp
private val IndicatorHeight = 3.dp
private val NavIconSize = 24.dp
private val PillShadow = 14.dp

/** Destinations of the tab graph inside [FindoraRoute.Main]. */
sealed interface MainRoute {
    @Serializable
    data object Home : MainRoute

    @Serializable
    data object Locations : MainRoute

    @Serializable
    data object Saved : MainRoute

    @Serializable
    data object Settings : MainRoute
}

/** Tabs of the floating navigation pill. Chat joins between Saved and Settings when it ships. */
enum class MainTab(
    val route: MainRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val tag: String,
) {
    Home(MainRoute.Home, R.string.nav_home, FindoraIcons.Home, FindoraIcons.HomeFilled, "nav_home"),
    Locations(MainRoute.Locations, R.string.nav_locations, FindoraIcons.MapPinHouse, FindoraIcons.MapPinHouse, "nav_locations"),
    Saved(MainRoute.Saved, R.string.nav_saved, FindoraIcons.Heart, FindoraIcons.HeartFilled, "nav_saved"),
    Settings(MainRoute.Settings, R.string.settings_title, FindoraIcons.Settings, FindoraIcons.Settings, "nav_settings"),
}

/**
 * The reference's floating white navigation pill: active tab in accent blue with a small bar above it that
 * slides (spring) between tabs; inactive tabs in muted grey. Each item is a selectable tab for TalkBack.
 */
@Composable
fun FloatingNavPill(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val pillColor = if (FindoraTheme.colors.isDark) FindoraTheme.colors.surface else FindoraTheme.colors.background
    Surface(
        shape = FindoraTheme.shapes.navPill,
        color = pillColor,
        shadowElevation = PillShadow,
        modifier = modifier.fillMaxWidth().height(NavPillHeight).testTag("navPill"),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabs = MainTab.entries
            val itemWidth = maxWidth / tabs.size
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selected.ordinal + (itemWidth - IndicatorWidth) / 2,
                animationSpec =
                    if (reduceMotion) {
                        snap()
                    } else {
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    },
                label = "navIndicator",
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                tabs.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(IndicatorWidth)
                    .height(IndicatorHeight)
                    .clip(RoundedCornerShape(bottomStart = IndicatorHeight, bottomEnd = IndicatorHeight))
                    .background(FindoraTheme.colors.accent)
                    .testTag("navIndicator"),
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) FindoraTheme.colors.accent else FindoraTheme.colors.onSurfaceMuted,
        label = "navItemColor",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxHeight()
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .testTag(tab.tag),
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(NavIconSize),
        )
        Spacer(Modifier.height(2.dp))
        Text(text = stringResource(tab.labelRes), style = FindoraTheme.typography.label, color = color, maxLines = 1)
    }
}
