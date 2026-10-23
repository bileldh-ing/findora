package com.findora.app.core.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme

private const val LIST_FRACTION = 0.42f
private val MinListWidth = 320.dp
private val MaxListWidth = 560.dp
private val DividerWidth = 1.dp

/**
 * A list and its detail. With [twoPanes] the list keeps the start side (about 42% of the width, 320 to 560 dp) and
 * the detail fills the rest, split exactly at the hinge of a book-posture foldable ([fold]); otherwise only [list] is
 * shown and the detail is a separate destination. Each pane re-measures its [AdaptiveLayout], so a list pane lays
 * out like the compact or medium window it effectively is. Pane order follows the layout direction (detail on the
 * left in RTL). The list keeps its state (scroll position, input) when the window switches between one and two panes.
 */
@Composable
fun ListDetailLayout(
    twoPanes: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fold: FoldPosture = FoldPosture.Flat,
) {
    val currentList by rememberUpdatedState(list)
    val movableList = remember { movableContentOf { currentList() } }
    if (!twoPanes) {
        Box(modifier.fillMaxSize().testTag("singlePane")) { movableList() }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("twoPanes")) {
        val hinge = fold.takeIf { it.splitsVertically && it.start > 0.dp && it.end <= maxWidth }
        val listWidth: Dp = hinge?.start ?: (maxWidth * LIST_FRACTION).coerceIn(MinListWidth, MaxListWidth)
        val gap: Dp = hinge?.let { it.end - it.start } ?: DividerWidth
        Row(Modifier.fillMaxSize()) {
            ProvideAdaptiveLayout(Modifier.width(listWidth).fillMaxHeight().testTag("listPane")) { movableList() }
            Spacer(
                Modifier
                    .width(gap)
                    .fillMaxHeight()
                    .background(FindoraTheme.colors.outline),
            )
            ProvideAdaptiveLayout(Modifier.weight(1f).fillMaxHeight().testTag("detailPane")) { detail() }
        }
    }
}
