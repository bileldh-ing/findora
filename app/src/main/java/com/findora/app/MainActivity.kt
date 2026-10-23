package com.findora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.core.designsystem.component.LocalReducedMotionOverride
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.ui.LocalDisplayPreferences
import com.findora.app.navigation.AppViewModel
import com.findora.app.navigation.FindoraNavHost
import com.findora.app.navigation.FindoraRoute
import com.findora.app.navigation.LaunchState
import dagger.hilt.android.AndroidEntryPoint

/**
 * AppCompatActivity (a FragmentActivity, which BiometricPrompt needs to host its UI) so the per-app language set
 * with AppCompatDelegate.setApplicationLocales also applies below Android 13: AppCompat recreates the activity
 * with the new locale and layout direction, so text and RTL mirroring change at once.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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
            val displayPreferences by appViewModel.displayPreferences.collectAsStateWithLifecycle()
            val reducedMotionOverride by appViewModel.reducedMotionOverride.collectAsStateWithLifecycle()
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
            CompositionLocalProvider(
                LocalDisplayPreferences provides displayPreferences,
                LocalReducedMotionOverride provides reducedMotionOverride,
            ) {
                FindoraTheme(darkTheme = darkTheme) {
                    (launchState as? LaunchState.Ready)?.let { ready ->
                        // A listing link is honoured only for a restored session going straight to the app. It is
                        // dropped behind the biometric lock or on Welcome, so a link can never skip the route guard.
                        if (ready.startDestination != FindoraRoute.Main) intent?.data = null
                        FindoraNavHost(startDestination = ready.startDestination)
                    }
                }
            }
        }
    }
}
