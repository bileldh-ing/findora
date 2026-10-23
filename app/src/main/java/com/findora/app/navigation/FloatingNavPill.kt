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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val BadgeSize = 16.dp
private val BadgeOffset = 7.dp
private val BadgeFontSize = 10.sp
private const val MAX_BADGE = 99

/** Destinations of the tab graph inside [FindoraRoute.Main]. */
sealed interface MainRoute {
    @Serializable
    data object Home : MainRoute

    @Serializable
    data object Locations : MainRoute

    @Serializable
    data object Saved : MainRoute

    @Serializable
    data object Chat : MainRoute

    @Serializable
    data object Settings : MainRoute
}

/** Tabs of the floating navigation pill, in reading order. */
enum class MainTab(
    val route: MainRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val tag: String,
) {
    Home(MainRoute.Home, R.string.nav_home, FindoraIcons.Home, FindoraIcons.HomeFilled, "nav_home"),
    Locations(
        MainRoute.Locations,
        R.string.nav_locations,
        FindoraIcons.MapPinHouse,
        FindoraIcons.MapPinHouse,
        "nav_locations",
    ),
    Saved(MainRoute.Saved, R.string.nav_saved, FindoraIcons.Heart, FindoraIcons.HeartFilled, "nav_saved"),
    Chat(MainRoute.Chat, R.string.nav_chat, FindoraIcons.Chat, FindoraIcons.Chat, "nav_chat"),
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
    /** Unread counts per tab (Chat), drawn as a blue badge on the icon and read by TalkBack. */
    badges: Map<MainTab, Int> = emptyMap(),
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
                        badge = badges[tab] ?: 0,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
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
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) FindoraTheme.colors.accent else FindoraTheme.colors.onSurfaceMuted,
        label = "navItemColor",
    )
    val unreadDescription = pluralStringResource(R.plurals.chat_unread_cd, badge, badge)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxHeight()
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .then(
                    if (badge > 0) {
                        Modifier.semantics { stateDescription = unreadDescription }
                    } else {
                        Modifier
                    },
                ).testTag(tab.tag),
    ) {
        Box {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(NavIconSize),
            )
            if (badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = BadgeOffset, y = -BadgeOffset)
                            .defaultMinSize(minWidth = BadgeSize, minHeight = BadgeSize)
                            .clip(CircleShape)
                            .background(FindoraTheme.colors.accent)
                            .padding(horizontal = 3.dp)
                            .testTag("${tab.tag}_badge"),
                ) {
                    Text(
                        text = if (badge > MAX_BADGE) "$MAX_BADGE+" else badge.toString(),
                        style = FindoraTheme.typography.label.copy(fontSize = BadgeFontSize),
                        color = FindoraTheme.colors.onPhoto,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = stringResource(tab.labelRes), style = FindoraTheme.typography.label, color = color, maxLines = 1)
    }
}
