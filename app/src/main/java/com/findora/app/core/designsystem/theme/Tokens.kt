package com.findora.app.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Spacing scale 4/8/12/16/24/32 from the spec. */
@Immutable
data class FindoraSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val screenHorizontal: Dp = 24.dp,
    val buttonHorizontal: Dp = 36.dp,
)

/** Corner radii from the spec (card 28, chip 20, pill 28, nav 32, sheet 32). */
@Immutable
data class FindoraShapes(
    val card: RoundedCornerShape = RoundedCornerShape(28.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(20.dp),
    val buttonPill: RoundedCornerShape = RoundedCornerShape(28.dp),
    val searchPill: RoundedCornerShape = RoundedCornerShape(28.dp),
    val navPill: RoundedCornerShape = RoundedCornerShape(32.dp),
    val bottomSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
)

/** Very soft ambient elevations only (0 to 8dp). */
@Immutable
data class FindoraElevation(
    val none: Dp = 0.dp,
    val low: Dp = 2.dp,
    val medium: Dp = 4.dp,
    val high: Dp = 8.dp,
)

/** Central motion specs: fast, springy, 200-450 ms. Compose already honours the system animator scale. */
@Immutable
data class FindoraMotion(
    val shortMillis: Int = 200,
    val mediumMillis: Int = 300,
    val longMillis: Int = 450,
) {
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> standard(): FiniteAnimationSpec<T> = tween(durationMillis = mediumMillis)

    fun offsetSpring(): FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}

val LocalFindoraSpacing = staticCompositionLocalOf { FindoraSpacing() }
val LocalFindoraShapes = staticCompositionLocalOf { FindoraShapes() }
val LocalFindoraElevation = staticCompositionLocalOf { FindoraElevation() }
val LocalFindoraMotion = staticCompositionLocalOf { FindoraMotion() }
