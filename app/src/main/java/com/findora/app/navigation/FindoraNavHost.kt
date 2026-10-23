package com.findora.app.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.findora.app.BuildConfig
import com.findora.app.core.ui.LocalNavAnimatedVisibilityScope
import com.findora.app.core.ui.LocalSharedTransitionScope
import com.findora.app.feature.about.AboutRoute
import com.findora.app.feature.auth.forgot.ForgotPasswordRoute
import com.findora.app.feature.auth.login.LoginRoute
import com.findora.app.feature.auth.signup.SignUpRoute
import com.findora.app.feature.auth.unlock.UnlockRoute
import com.findora.app.feature.auth.welcome.WelcomeRoute
import com.findora.app.feature.chat.ChatRoute
import com.findora.app.feature.compare.CompareRoute
import com.findora.app.feature.designsystem.DesignSystemScreen
import com.findora.app.feature.detail.DetailRoute
import com.findora.app.feature.match.deck.DeckRoute
import com.findora.app.feature.match.quiz.MatchQuizRoute
import com.findora.app.feature.notifications.NotificationsRoute
import com.findora.app.feature.saved.MatchesRoute
import com.findora.app.feature.savedsearch.SavedSearchLinkRoute
import com.findora.app.feature.search.SearchRoute
import com.findora.app.feature.viewing.ScheduleViewingRoute
import com.findora.app.feature.viewing.ViewingsRoute

/**
 * The root graph. A [SharedTransitionLayout] wraps it so a listing photo can travel from its card (inside the
 * Main tabs) into the detail page; [PropertyDeepLink] links open [FindoraRoute.PropertyDetail] directly.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FindoraNavHost(
    startDestination: FindoraRoute,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    showDesignSystem: Boolean = BuildConfig.DEBUG,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            FindoraGraph(startDestination, navController, showDesignSystem)
        }
    }
}

@Composable
private fun FindoraGraph(
    startDestination: FindoraRoute,
    navController: NavHostController,
    showDesignSystem: Boolean,
    modifier: Modifier = Modifier,
) {
    val openProperty: (String) -> Unit = { id -> navController.navigate(FindoraRoute.PropertyDetail(id)) }
    val openConversation: (String) -> Unit = { id -> navController.navigate(FindoraRoute.Chat(id)) }
    val messageAgent: (String) -> Unit = { id -> navController.navigate(FindoraRoute.ChatWithAgent(id)) }
    val toMain = { navController.navigateClearingBackStack(FindoraRoute.Main) }
    val toWelcome = { navController.navigateClearingBackStack(FindoraRoute.Welcome) }
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<FindoraRoute.Welcome> {
            WelcomeRoute(
                onLogin = { navController.navigate(FindoraRoute.Login) },
                onSignUp = { navController.navigate(FindoraRoute.SignUp) },
                onSignedIn = toMain,
            )
        }
        composable<FindoraRoute.Login> {
            LoginRoute(
                onBack = { navController.popBackStack() },
                onSignUp = {
                    navController.navigate(FindoraRoute.SignUp) { popUpTo(FindoraRoute.Login) { inclusive = true } }
                },
                onForgotPassword = { navController.navigate(FindoraRoute.ForgotPassword) },
                onSignedIn = toMain,
            )
        }
        composable<FindoraRoute.SignUp> {
            SignUpRoute(
                onBack = { navController.popBackStack() },
                onLogin = {
                    navController.navigate(FindoraRoute.Login) { popUpTo(FindoraRoute.SignUp) { inclusive = true } }
                },
                onSignedIn = toMain,
            )
        }
        composable<FindoraRoute.ForgotPassword> {
            ForgotPasswordRoute(
                onBack = { navController.popBackStack() },
                onBackToLogin = { navController.popBackStack(FindoraRoute.Login, inclusive = false) },
            )
        }
        composable<FindoraRoute.Unlock> {
            UnlockRoute(onUnlock = toMain, onSignedOut = toWelcome)
        }
        composable<FindoraRoute.Main> {
            val openDesignSystem: (() -> Unit)? =
                if (showDesignSystem) {
                    { navController.navigate(FindoraRoute.DesignSystem) }
                } else {
                    null
                }
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                MainShell(
                    onOpenSearch = { navController.navigate(FindoraRoute.Search) },
                    onOpenProperty = openProperty,
                    onSignedOut = toWelcome,
                    onCreateAccount = { navController.navigate(FindoraRoute.SignUp) },
                    onOpenViewings = { navController.navigate(FindoraRoute.Viewings) },
                    onOpenAbout = { navController.navigate(FindoraRoute.About) },
                    onOpenMatchQuiz = { navController.navigate(FindoraRoute.MatchQuiz) },
                    onOpenDreamMatch = { navController.navigate(FindoraRoute.DreamMatch) },
                    onOpenCompare = { navController.navigate(FindoraRoute.Compare) },
                    onOpenConversation = openConversation,
                    onOpenNotifications = { navController.navigate(FindoraRoute.Notifications) },
                    onOpenDesignSystem = openDesignSystem,
                )
            }
        }
        composable<FindoraRoute.PropertyDetail>(
            deepLinks = propertyDetailDeepLinks,
        ) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                DetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenProperty = openProperty,
                    onScheduleViewing = { id -> navController.navigate(FindoraRoute.ScheduleViewing(id)) },
                    onOpenCompare = { navController.navigate(FindoraRoute.Compare) { launchSingleTop = true } },
                    onMessageAgent = messageAgent,
                )
            }
        }
        composable<FindoraRoute.DreamMatch> {
            DeckRoute(
                onBack = { navController.popBackStack() },
                onOpenMatches = {
                    navController.navigate(FindoraRoute.Matches) {
                        popUpTo<FindoraRoute.DreamMatch> { inclusive = true }
                    }
                },
                onOpenProperty = openProperty,
                onScheduleViewing = { id -> navController.navigate(FindoraRoute.ScheduleViewing(id)) },
            )
        }
        composable<FindoraRoute.Matches> {
            MatchesRoute(
                onBack = { navController.popBackStack() },
                onOpenProperty = openProperty,
                onOpenDreamMatch = {
                    navController.navigate(FindoraRoute.DreamMatch) {
                        popUpTo<FindoraRoute.Matches> { inclusive = true }
                    }
                },
            )
        }
        composable<FindoraRoute.Compare> {
            CompareRoute(onBack = { navController.popBackStack() }, onOpenProperty = openProperty)
        }
        composable<FindoraRoute.ScheduleViewing> {
            ScheduleViewingRoute(
                onBack = { navController.popBackStack() },
                onFinish = { navController.popBackStack() },
                onOpenViewings = {
                    navController.navigate(FindoraRoute.Viewings) {
                        popUpTo<FindoraRoute.ScheduleViewing> { inclusive = true }
                    }
                },
                onCreateAccount = { navController.navigate(FindoraRoute.SignUp) },
                onMessageAgent = { id ->
                    navController.navigate(FindoraRoute.ChatWithAgent(id)) {
                        popUpTo<FindoraRoute.ScheduleViewing> { inclusive = true }
                    }
                },
            )
        }
        composable<FindoraRoute.Chat>(deepLinks = chatDeepLinks) {
            ChatRoute(onBack = { navController.popBackStack() }, onOpenProperty = openProperty)
        }
        composable<FindoraRoute.ChatWithAgent> {
            ChatRoute(onBack = { navController.popBackStack() }, onOpenProperty = openProperty)
        }
        composable<FindoraRoute.Notifications> {
            NotificationsRoute(
                onBack = { navController.popBackStack() },
                onOpenConversation = openConversation,
                onOpenProperty = openProperty,
                onOpenSavedSearch = { id -> navController.navigate(FindoraRoute.SavedSearchResults(id)) },
            )
        }
        composable<FindoraRoute.SavedSearchResults>(deepLinks = savedSearchDeepLinks) {
            SavedSearchLinkRoute(onDone = toMain)
        }
        composable<FindoraRoute.MatchQuiz> {
            MatchQuizRoute(
                onFinish = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            )
        }
        composable<FindoraRoute.About> {
            AboutRoute(onBack = { navController.popBackStack() })
        }
        composable<FindoraRoute.Viewings> {
            ViewingsRoute(onBack = { navController.popBackStack() }, onOpenProperty = openProperty)
        }
        composable<FindoraRoute.Search>(
            enterTransition = { fadeIn(tween(SEARCH_FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(SEARCH_FADE_MILLIS)) },
        ) {
            SearchRoute(onClose = { navController.popBackStack() })
        }
        if (showDesignSystem) {
            composable<FindoraRoute.DesignSystem> {
                DesignSystemScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private const val SEARCH_FADE_MILLIS = 200

/** `findora://property/{propertyId}` mapped onto [FindoraRoute.PropertyDetail]; shared with the routing test. */
internal val propertyDetailDeepLinks =
    listOf(navDeepLink<FindoraRoute.PropertyDetail>(basePath = PropertyDeepLink.BASE_PATH))

/** `findora://chat/{conversationId}` mapped onto [FindoraRoute.Chat]; shared with the routing test. */
internal val chatDeepLinks = listOf(navDeepLink<FindoraRoute.Chat>(basePath = ChatDeepLink.BASE_PATH))

/** `findora://search/{searchId}` mapped onto [FindoraRoute.SavedSearchResults]; shared with the routing test. */
internal val savedSearchDeepLinks =
    listOf(navDeepLink<FindoraRoute.SavedSearchResults>(basePath = SearchDeepLink.BASE_PATH))

/** Navigates to [route] and removes everything behind it (sign-in, sign-out). */
private fun NavHostController.navigateClearingBackStack(route: FindoraRoute) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
