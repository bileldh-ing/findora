package com.findora.app.feature.match.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Position of the top card while it is dragged or flung. Swipe directions are physical (screen) directions in
 * every language: right saves, left skips, up super-likes (see README, Dream Match, for the RTL decision).
 */
@Stable
class DeckSwipeState {
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)

    /** True from the moment a fling starts, so a second gesture or button press cannot act twice. */
    var isCommitting: Boolean = false
        private set

    suspend fun dragBy(delta: Offset) {
        if (isCommitting) return
        offsetX.snapTo(offsetX.value + delta.x)
        offsetY.snapTo(offsetY.value + delta.y)
    }

    /** Springs back to the centre after a drag that did not go far or fast enough. */
    suspend fun reset(reduceMotion: Boolean) {
        coroutineScope {
            if (reduceMotion) {
                launch { offsetX.snapTo(0f) }
                launch { offsetY.snapTo(0f) }
            } else {
                launch { offsetX.animateTo(0f, tween(RESET_MILLIS)) }
                launch { offsetY.animateTo(0f, tween(RESET_MILLIS)) }
            }
        }
    }

    /** Throws the card off screen in [action]'s direction (instantly when motion is reduced). */
    suspend fun fling(
        action: DeckAction,
        width: Float,
        height: Float,
        reduceMotion: Boolean,
    ) {
        isCommitting = true
        val (targetX, targetY) =
            when (action) {
                DeckAction.Save -> width * OFF_SCREEN to offsetY.value
                DeckAction.Skip -> -width * OFF_SCREEN to offsetY.value
                DeckAction.SuperLike -> offsetX.value to -height * OFF_SCREEN
            }
        coroutineScope {
            if (reduceMotion) {
                launch { offsetX.snapTo(targetX) }
                launch { offsetY.snapTo(targetY) }
            } else {
                launch { offsetX.animateTo(targetX, tween(FLING_MILLIS)) }
                launch { offsetY.animateTo(targetY, tween(FLING_MILLIS)) }
            }
        }
    }

    /** -1..1 horizontal drag progress towards a commit (negative = towards Skip). */
    fun horizontalProgress(width: Float): Float =
        if (width <= 0f) 0f else (offsetX.value / (width * DISTANCE_FRACTION)).coerceIn(-1f, 1f)

    /** 0..1 upward drag progress towards a super like. */
    fun upwardProgress(height: Float): Float =
        if (height <= 0f) 0f else (-offsetY.value / (height * UP_DISTANCE_FRACTION)).coerceIn(0f, 1f)

    /** Card tilt in degrees, following the horizontal drag. */
    fun rotation(width: Float): Float = horizontalProgress(width) * MAX_ROTATION

    companion object {
        /** Share of the card width a drag must cover to commit without speed. */
        const val DISTANCE_FRACTION = 0.3f

        /** Share of the card height an upward drag must cover to super-like without speed. */
        const val UP_DISTANCE_FRACTION = 0.2f
        const val MAX_ROTATION = 12f
        private const val OFF_SCREEN = 1.5f
        private const val FLING_MILLIS = 240
        private const val RESET_MILLIS = 220

        /**
         * Which action a released drag means: far enough or fast enough (a fling) in one direction. Upward wins
         * only when the drag is more vertical than horizontal; a downward drag never acts.
         */
        fun decide(
            offset: Offset,
            velocity: Velocity,
            width: Float,
            height: Float,
            flingVelocity: Float,
        ): DeckAction? {
            val vertical = abs(offset.y) > abs(offset.x)
            val farUp = -offset.y > height * UP_DISTANCE_FRACTION
            val fastUp = velocity.y < -flingVelocity && offset.y < 0f
            val farSide = abs(offset.x) > width * DISTANCE_FRACTION
            val fastSide = abs(velocity.x) > flingVelocity && velocity.x * offset.x > 0f
            return when {
                vertical && offset.y < 0f && (farUp || fastUp) -> DeckAction.SuperLike
                !vertical && (farSide || fastSide) -> if (offset.x > 0f) DeckAction.Save else DeckAction.Skip
                else -> null
            }
        }
    }
}
