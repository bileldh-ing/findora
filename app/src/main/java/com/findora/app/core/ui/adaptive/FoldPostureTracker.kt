package com.findora.app.core.ui.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * The fold of a foldable device as Compose state, from Jetpack WindowManager: flat (or no fold) on every other device.
 */
@Composable
fun rememberFoldPosture(activity: Activity): FoldPosture {
    val density = LocalDensity.current
    var posture by remember { mutableStateOf(FoldPosture.Flat) }
    LaunchedEffect(activity, density) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            posture = info.toPosture(density)
        }
    }
    return posture
}

internal fun WindowLayoutInfo.toPosture(density: Density): FoldPosture {
    val fold = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return FoldPosture.Flat
    val vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL
    return with(density) {
        FoldPosture(
            isSeparating = fold.isSeparating,
            isVertical = vertical,
            isHalfOpened = fold.state == FoldingFeature.State.HALF_OPENED,
            start = (if (vertical) fold.bounds.left else fold.bounds.top).toDp(),
            end = (if (vertical) fold.bounds.right else fold.bounds.bottom).toDp(),
        )
    }
}
