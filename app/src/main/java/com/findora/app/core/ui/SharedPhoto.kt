package com.findora.app.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.findora.app.core.designsystem.component.rememberReducedMotion

/** The app-wide [SharedTransitionScope] (set by the root nav host); null in previews and isolated tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The [AnimatedVisibilityScope] of the root destination a composable belongs to; null outside a nav host. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-element key of a listing's cover photo, the same on the card and on the detail page. */
fun propertyPhotoKey(propertyId: String): String = "property-photo-$propertyId"

/**
 * Lays out [content] with a modifier that makes it the shared cover photo of [propertyId], so the photo
 * grows from the card into the detail carousel and back. Plain layout when there is no transition scope
 * or the user turned animations off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedPropertyPhoto(
    propertyId: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalNavAnimatedVisibilityScope.current
    val reduceMotion = rememberReducedMotion()
    val photoModifier =
        if (sharedScope == null || visibilityScope == null || reduceMotion) {
            modifier
        } else {
            with(sharedScope) {
                modifier.sharedElement(
                    state = rememberSharedContentState(key = propertyPhotoKey(propertyId)),
                    animatedVisibilityScope = visibilityScope,
                )
            }
        }
    content(photoModifier)
}
