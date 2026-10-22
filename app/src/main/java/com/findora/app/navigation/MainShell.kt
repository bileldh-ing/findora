package com.findora.app.navigation

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.home.HomeRoute
import com.findora.app.feature.locations.LocationsRoute
import com.findora.app.feature.saved.SavedRoute
import com.findora.app.feature.settings.SettingsRoute

/**
 * The signed-in (or guest) app: the tab destinations with the floating navigation pill drawn over them.
 * Every tab gets bottom content padding for the pill so no card or button is ever hidden behind it.
 */
@Composable
fun MainShell(
    onOpenSearch: () -> Unit,
    onOpenProperty: (String) -> Unit,
    onSignedOut: () -> Unit,
    onCreateAccount: () -> Unit,
    onOpenViewings: () -> Unit,
    onOpenDesignSystem: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab =
        MainTab.entries.firstOrNull { tab ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
        } ?: MainTab.Home
    val reduceMotion = rememberReducedMotion()
    val fadeMillis = FindoraTheme.motion.shortMillis
    val contentPadding =
        PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    NavPillHeight + NavPillMargin * 2,
        )
    val selectTab: (MainTab) -> Unit = { tab -> navController.navigateToTab(tab) }

    Box(modifier = modifier.fillMaxSize().testTag("mainShell")) {
        NavHost(
            navController = navController,
            startDestination = MainRoute.Home,
            enterTransition = { if (reduceMotion) fadeIn(snap()) else fadeIn(tween(fadeMillis)) },
            exitTransition = { if (reduceMotion) fadeOut(snap()) else fadeOut(tween(fadeMillis)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<MainRoute.Home> {
                HomeRoute(
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = { selectTab(MainTab.Settings) },
                    onOpenProperty = onOpenProperty,
                    contentPadding = contentPadding,
                )
            }
            composable<MainRoute.Locations> {
                LocationsRoute(onShowHome = { selectTab(MainTab.Home) }, contentPadding = contentPadding)
            }
            composable<MainRoute.Saved> {
                SavedRoute(onOpenProperty = onOpenProperty, contentPadding = contentPadding)
            }
            composable<MainRoute.Settings> {
                SettingsRoute(
                    onSignedOut = onSignedOut,
                    onCreateAccount = onCreateAccount,
                    onOpenViewings = onOpenViewings,
                    onOpenDesignSystem = onOpenDesignSystem,
                    contentPadding = contentPadding,
                )
            }
        }
        FloatingNavPill(
            selected = currentTab,
            onSelect = selectTab,
            reduceMotion = reduceMotion,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = NavPillMargin),
        )
    }
}

/** Standard bottom-navigation behaviour: one copy of each tab, state saved and restored, back returns to Home. */
private fun NavHostController.navigateToTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
