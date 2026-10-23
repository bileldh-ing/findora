package com.findora.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraLogoMark
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.adaptive.ProvideAdaptiveLayout

private const val MAX_BADGE = 99

/**
 * The tab chrome of [MainShell], chosen by window width: the reference's floating pill over the content on phones,
 * a [NavigationRail] beside it on tablets, foldables and landscape (medium and expanded widths). [content] receives
 * the padding that keeps it clear of the system bars and the pill, and lays out for the space left beside the rail.
 */
@Composable
fun MainScaffold(
    useRail: Boolean,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<MainTab, Int> = emptyMap(),
    reduceMotion: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Moving between the pill and the rail (a fold or a rotation) keeps the tab content's state.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { padding: PaddingValues -> currentContent(padding) } }
    if (useRail) {
        Row(modifier.fillMaxSize().testTag("mainShell")) {
            MainNavigationRail(selected = selected, onSelect = onSelect, badges = badges)
            ProvideAdaptiveLayout(Modifier.weight(1f).fillMaxHeight()) {
                movableContent(PaddingValues(top = statusBar, bottom = navigationBar + NavPillMargin))
            }
        }
    } else {
        Box(modifier.fillMaxSize().testTag("mainShell")) {
            movableContent(PaddingValues(top = statusBar, bottom = navigationBar + NavPillHeight + NavPillMargin * 2))
            FloatingNavPill(
                selected = selected,
                onSelect = onSelect,
                reduceMotion = reduceMotion,
                badges = badges,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = NavPillMargin),
            )
        }
    }
}

/** The five tabs as a Material 3 navigation rail, the Findora badge on top; each item is a selectable tab. */
@Composable
fun MainNavigationRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<MainTab, Int> = emptyMap(),
) {
    val colors =
        NavigationRailItemDefaults.colors(
            selectedIconColor = FindoraTheme.colors.accent,
            selectedTextColor = FindoraTheme.colors.accent,
            indicatorColor = FindoraTheme.colors.accent.copy(alpha = INDICATOR_ALPHA),
            unselectedIconColor = FindoraTheme.colors.onSurfaceMuted,
            unselectedTextColor = FindoraTheme.colors.onSurfaceMuted,
        )
    val container = if (FindoraTheme.colors.isDark) FindoraTheme.colors.surface else FindoraTheme.colors.background
    NavigationRail(
        containerColor = container,
        windowInsets =
            WindowInsets.statusBars
                .union(WindowInsets.navigationBars)
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        header = {
            FindoraLogoMark(
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.padding(vertical = FindoraTheme.spacing.sm).size(RailLogoSize),
            )
        },
        modifier = modifier.testTag("navRail"),
    ) {
        Spacer(Modifier.weight(1f))
        MainTab.entries.forEach { tab ->
            val badge = badges[tab] ?: 0
            val unread = pluralStringResource(R.plurals.chat_unread_cd, badge, badge)
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badge > 0) {
                                Badge(
                                    containerColor = FindoraTheme.colors.accent,
                                    contentColor = FindoraTheme.colors.onPhoto,
                                    modifier = Modifier.testTag("${tab.tag}_badge"),
                                ) { Text(if (badge > MAX_BADGE) "$MAX_BADGE+" else badge.toString()) }
                            }
                        },
                    ) {
                        Icon(if (tab == selected) tab.selectedIcon else tab.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                colors = colors,
                modifier =
                    Modifier
                        .testTag(tab.tag)
                        .then(if (badge > 0) Modifier.semantics { stateDescription = unread } else Modifier),
            )
            Spacer(Modifier.height(FindoraTheme.spacing.xs))
        }
        Spacer(Modifier.weight(1f))
    }
}

private const val INDICATOR_ALPHA = 0.14f
private val RailLogoSize = 32.dp
