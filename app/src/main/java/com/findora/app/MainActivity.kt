package com.findora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.navigation.AppViewModel
import com.findora.app.navigation.FindoraNavHost
import com.findora.app.navigation.LaunchState
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity (a ComponentActivity) because BiometricPrompt hosts its UI in a fragment. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The route guard restores the session before the first frame, so Welcome never flashes for signed-in users.
        splash.setKeepOnScreenCondition { appViewModel.launchState.value is LaunchState.Loading }
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val launchState by appViewModel.launchState.collectAsStateWithLifecycle()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            FindoraTheme(darkTheme = darkTheme) {
                (launchState as? LaunchState.Ready)?.let { FindoraNavHost(startDestination = it.startDestination) }
            }
        }
    }
}
