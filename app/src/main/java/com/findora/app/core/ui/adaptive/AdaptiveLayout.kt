package com.findora.app.core.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

/** Width buckets of the Material window size classes (600 dp and 840 dp breakpoints). */
enum class WidthClass { Compact, Medium, Expanded }

/**
 * A hinge or fold that splits the window ([isSeparating]): [start] and [end] are its edges from the window's left
 * edge when it is vertical. A book-posture fold is honoured by the list-detail layout; other postures only need the
 * layouts to stay usable, which they do because they never assume a fixed size.
 */
@Immutable
data class FoldPosture(
    val isSeparating: Boolean = false,
    val isVertical: Boolean = false,
    val isHalfOpened: Boolean = false,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
) {
    /** A vertical hinge the two panes should sit on either side of. */
    val splitsVertically: Boolean get() = isSeparating && isVertical

    companion object {
        val Flat = FoldPosture()
    }
}

/**
 * What the screens adapt to, derived from the space they are given (not the display), so a split-screen window, a
 * foldable's inner screen and a forced test size all behave alike.
 */
@Immutable
data class AdaptiveLayout(
    val width: Dp,
    val height: Dp,
    val widthClass: WidthClass,
    /** Less than 480 dp tall: a phone in landscape. Tall headers and heroes should shrink or go side by side. */
    val isShort: Boolean,
    val fold: FoldPosture = FoldPosture.Flat,
) {
    /** Navigation rail instead of the floating pill. */
    val useNavigationRail: Boolean get() = widthClass != WidthClass.Compact

    /** List and detail side by side (Home and a listing, Chat and a thread, Settings sections). */
    val useTwoPanes: Boolean get() = widthClass == WidthClass.Expanded || fold.splitsVertically

    /** Columns of the Home grid: 2 on phones, 3 on medium windows, 4 when expanded (or per pane in two-pane mode). */
    val gridColumns: Int
        get() =
            when (widthClass) {
                WidthClass.Compact -> COMPACT_COLUMNS
                WidthClass.Medium -> MEDIUM_COLUMNS
                WidthClass.Expanded -> EXPANDED_COLUMNS
            }

    companion object {
        const val COMPACT_COLUMNS = 2
        const val MEDIUM_COLUMNS = 3
        const val EXPANDED_COLUMNS = 4

        /** A typical phone in portrait: the default when nothing provides a layout (previews of single screens). */
        val Phone = from(width = 411.dp, height = 891.dp)

        fun from(
            width: Dp,
            height: Dp,
            fold: FoldPosture = FoldPosture.Flat,
        ): AdaptiveLayout {
            val sizeClass = WindowSizeClass.compute(width.value, height.value)
            val widthClass =
                when (sizeClass.windowWidthSizeClass) {
                    WindowWidthSizeClass.EXPANDED -> WidthClass.Expanded
                    WindowWidthSizeClass.MEDIUM -> WidthClass.Medium
                    else -> WidthClass.Compact
                }
            return AdaptiveLayout(
                width = width,
                height = height,
                widthClass = widthClass,
                isShort = sizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT,
                fold = fold,
            )
        }
    }
}

/** The current [AdaptiveLayout]; provided at the root by [ProvideAdaptiveLayout]. */
val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayout.Phone }

/** Measures the space available to [content] and provides the matching [AdaptiveLayout]. */
@Composable
fun ProvideAdaptiveLayout(
    modifier: Modifier = Modifier,
    fold: FoldPosture = FoldPosture.Flat,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val layout = remember(maxWidth, maxHeight, fold) { AdaptiveLayout.from(maxWidth, maxHeight, fold) }
        CompositionLocalProvider(LocalAdaptiveLayout provides layout) { content() }
    }
}
