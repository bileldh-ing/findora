package com.findora.app.navigation

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.findora.app.core.ui.LocalNavAnimatedVisibilityScope
import com.findora.app.core.ui.adaptive.ListDetailLayout
import com.findora.app.core.ui.adaptive.LocalAdaptiveLayout
import com.findora.app.feature.chat.ChatRoute
import com.findora.app.feature.chat.ConversationsRoute
import com.findora.app.feature.detail.DetailRoute
import com.findora.app.feature.home.HomeRoute
import com.findora.app.feature.locations.LocationsRoute
import com.findora.app.feature.saved.SavedRoute
import com.findora.app.feature.settings.SettingsRoute

/** Navigation out of the tabs, handled by the root graph. */
data class MainShellActions(
    val onOpenSearch: () -> Unit,
    val onOpenProperty: (String) -> Unit,
    val onSignedOut: () -> Unit,
    val onCreateAccount: () -> Unit,
    val onOpenViewings: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenMatchQuiz: () -> Unit,
    val onOpenDreamMatch: () -> Unit,
    val onOpenCompare: () -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenDesignSystem: (() -> Unit)?,
    val onScheduleViewing: (String) -> Unit,
    val onMessageAgent: (String) -> Unit,
    val onOpenOwnerDashboard: () -> Unit,
    val onEditListing: (String) -> Unit,
)

/**
 * The signed-in (or guest) app: the tab destinations under [MainScaffold] (floating pill on phones, navigation rail on
 * wider windows). On expanded widths Home and Chat become list-detail: a listing or a thread opens in the pane beside
 * the list (its own small graph) instead of covering it.
 */
@Composable
fun MainShell(
    actions: MainShellActions,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainShellViewModel = hiltViewModel(),
) {
    val unreadMessages by viewModel.unreadMessages.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab =
        MainTab.entries.firstOrNull { tab ->
            backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
        } ?: MainTab.Home
    val reduceMotion = rememberReducedMotion()
    val fadeMillis = FindoraTheme.motion.shortMillis
    val layout = LocalAdaptiveLayout.current
    val selectTab: (MainTab) -> Unit = { tab -> navController.navigateToTab(tab) }

    MainScaffold(
        useRail = layout.useNavigationRail,
        selected = currentTab,
        onSelect = selectTab,
        badges = mapOf(MainTab.Chat to unreadMessages),
        reduceMotion = reduceMotion,
        modifier = modifier,
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoute.Home,
            enterTransition = { if (reduceMotion) fadeIn(snap()) else fadeIn(tween(fadeMillis)) },
            exitTransition = { if (reduceMotion) fadeOut(snap()) else fadeOut(tween(fadeMillis)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<MainRoute.Home> {
                HomeTab(actions, contentPadding, onOpenProfile = { selectTab(MainTab.Settings) })
            }
            composable<MainRoute.Locations> {
                LocationsRoute(onOpenProperty = actions.onOpenProperty, contentPadding = contentPadding)
            }
            composable<MainRoute.Saved> {
                SavedRoute(
                    onOpenProperty = actions.onOpenProperty,
                    onOpenDreamMatch = actions.onOpenDreamMatch,
                    onShowSearchResults = { selectTab(MainTab.Home) },
                    contentPadding = contentPadding,
                )
            }
            composable<MainRoute.Chat> {
                ChatTab(actions, contentPadding)
            }
            composable<MainRoute.Settings> {
                SettingsRoute(
                    onSignedOut = actions.onSignedOut,
                    onCreateAccount = actions.onCreateAccount,
                    onOpenViewings = actions.onOpenViewings,
                    onOpenMatchPreferences = actions.onOpenMatchQuiz,
                    onOpenAbout = actions.onOpenAbout,
                    onOpenDesignSystem = actions.onOpenDesignSystem,
                    onOpenOwnerDashboard = actions.onOpenOwnerDashboard,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/** Home, with the selected listing beside the feed on expanded widths. */
@Composable
private fun HomeTab(
    actions: MainShellActions,
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalAdaptiveLayout.current
    val paneNav = rememberNavController()
    val twoPanes = layout.useTwoPanes
    ListDetailLayout(
        twoPanes = twoPanes,
        fold = layout.fold,
        modifier = modifier,
        list = {
            HomeRoute(
                onOpenSearch = actions.onOpenSearch,
                onOpenProfile = onOpenProfile,
                onOpenProperty = if (twoPanes) paneNav::showDetail else actions.onOpenProperty,
                onOpenMatchQuiz = actions.onOpenMatchQuiz,
                onOpenDreamMatch = actions.onOpenDreamMatch,
                onOpenCompare = actions.onOpenCompare,
                onOpenNotifications = actions.onOpenNotifications,
                contentPadding = contentPadding,
            )
        },
        detail = {
            NavHost(navController = paneNav, startDestination = PaneRoute.Empty) {
                composable<PaneRoute.Empty> { EmptyPane(PaneRoute.Kind.Listing, contentPadding) }
                composable<FindoraRoute.PropertyDetail> {
                    // The list stays on screen, so the photo is not shared between the panes.
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides null) {
                        DetailRoute(
                            onBack = { paneNav.popBackStack() },
                            onOpenProperty = paneNav::showDetail,
                            onScheduleViewing = actions.onScheduleViewing,
                            onOpenCompare = actions.onOpenCompare,
                            onMessageAgent = actions.onMessageAgent,
                            onEditListing = actions.onEditListing,
                        )
                    }
                }
            }
        },
    )
}

/** Conversations, with the open thread beside the list on expanded widths. */
@Composable
private fun ChatTab(
    actions: MainShellActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val layout = LocalAdaptiveLayout.current
    val paneNav = rememberNavController()
    val twoPanes = layout.useTwoPanes
    ListDetailLayout(
        twoPanes = twoPanes,
        fold = layout.fold,
        modifier = modifier,
        list = {
            ConversationsRoute(
                onOpenConversation =
                    if (twoPanes) {
                        { id -> paneNav.showOnly(FindoraRoute.Chat(id)) }
                    } else {
                        actions.onOpenConversation
                    },
                contentPadding = contentPadding,
            )
        },
        detail = {
            NavHost(navController = paneNav, startDestination = PaneRoute.Empty) {
                composable<PaneRoute.Empty> { EmptyPane(PaneRoute.Kind.Conversation, contentPadding) }
                composable<FindoraRoute.Chat> {
                    ChatRoute(onBack = { paneNav.popBackStack() }, onOpenProperty = actions.onOpenProperty)
                }
            }
        },
    )
}

/** Opens a listing in the detail pane; listings opened from it (similar homes) stack, Back returns. */
private fun NavHostController.showDetail(propertyId: String) {
    navigate(FindoraRoute.PropertyDetail(propertyId)) { launchSingleTop = true }
}

/** Replaces whatever the pane shows with [route] (one thread at a time). */
private fun NavHostController.showOnly(route: FindoraRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
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
