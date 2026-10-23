package com.findora.app.core.designsystem.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The in-app reduce-motion override from Settings: null follows the system, true always reduces decorative
 * motion, false always plays it. Provided once at the root.
 */
val LocalReducedMotionOverride = staticCompositionLocalOf<Boolean?> { null }

/** Pure decision behind [rememberReducedMotion], unit-tested. */
fun resolveReducedMotion(
    override: Boolean?,
    systemAnimatorScale: Float,
): Boolean = override ?: (systemAnimatorScale == 0f)

/**
 * True when decorative motion (Ken Burns, entrances, count-ups) should be skipped: the user forced it in
 * Settings, or, by default, turned animations off system-wide (Developer options or accessibility "Remove
 * animations", i.e. an animator duration scale of 0).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val override = LocalReducedMotionOverride.current
    return remember(context, override) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        resolveReducedMotion(override, scale)
    }
}
