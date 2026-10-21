package com.findora.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.findora.app.BuildConfig
import com.findora.app.feature.bootstrap.BootstrapScreen
import com.findora.app.feature.designsystem.DesignSystemScreen

@Composable
fun FindoraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    showDesignSystem: Boolean = BuildConfig.DEBUG,
) {
    NavHost(
        navController = navController,
        startDestination = FindoraRoute.Bootstrap,
        modifier = modifier,
    ) {
        composable<FindoraRoute.Bootstrap> {
            BootstrapScreen(
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
