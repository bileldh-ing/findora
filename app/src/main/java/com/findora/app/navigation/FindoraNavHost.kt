package com.findora.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.findora.app.BuildConfig
import com.findora.app.feature.auth.forgot.ForgotPasswordRoute
import com.findora.app.feature.auth.login.LoginRoute
import com.findora.app.feature.auth.signup.SignUpRoute
import com.findora.app.feature.auth.unlock.UnlockRoute
import com.findora.app.feature.auth.welcome.WelcomeRoute
import com.findora.app.feature.designsystem.DesignSystemScreen
import com.findora.app.feature.settings.SettingsRoute

@Composable
fun FindoraNavHost(
    startDestination: FindoraRoute,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    showDesignSystem: Boolean = BuildConfig.DEBUG,
) {
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
            SettingsRoute(
                onSignedOut = toWelcome,
                onCreateAccount = { navController.navigate(FindoraRoute.SignUp) },
                onOpenDesignSystem =
                    if (showDesignSystem) {
                        { navController.navigate(FindoraRoute.DesignSystem) }
                    } else {
                        null
                    },
            )
        }
        if (showDesignSystem) {
            composable<FindoraRoute.DesignSystem> {
                DesignSystemScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Navigates to [route] and removes everything behind it (sign-in, sign-out). */
private fun NavHostController.navigateClearingBackStack(route: FindoraRoute) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
