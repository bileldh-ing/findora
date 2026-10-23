package com.findora.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.findora.app.core.designsystem.theme.FindoraTheme
import kotlinx.coroutines.launch

private val ChipHeight = 40.dp

/**
 * Horizontally scrolling category chips with one shared selected pill that slides (and resizes) between
 * chips instead of each chip recolouring on its own. Each chip is a selectable tab for TalkBack.
 */
@Composable
fun SlidingChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val target = bounds[selectedIndex]
    val motion = FindoraTheme.motion
    val pillOffset = remember { Animatable(0.dp, Dp.VectorConverter) }
    val pillWidth = remember { Animatable(0.dp, Dp.VectorConverter) }
    var pillPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val (offset, width) = target ?: return@LaunchedEffect
        if (pillPlaced) {
            launch { pillOffset.animateTo(offset, motion.snappy()) }
            pillWidth.animateTo(width, motion.snappy())
        } else {
            // The first position is applied at once: animating from 0 made the pill fly in from the left edge,
            // which in RTL is the far end of the row.
            pillOffset.snapTo(offset)
            pillWidth.snapTo(width)
            pillPlaced = true
        }
    }
    Box(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(contentPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
            labels.indices.forEach { index ->
                Box(
                    Modifier
                        .defaultMinSize(minHeight = ChipHeight)
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.surface)
                        // Layout-only twin of the real chip: hidden from TalkBack and tests.
                        .clearAndSetSemantics {},
                ) {
                    // Invisible spacer chip: same size as the label chip, painted under it.
                    Text(
                        text = labels[index],
                        style = FindoraTheme.typography.bodyMedium,
                        color = Color.Transparent,
                        modifier =
                            Modifier.padding(
                                horizontal = FindoraTheme.spacing.md,
                                vertical = FindoraTheme.spacing.xs,
                            ),
                    )
                }
            }
        }
        if (pillPlaced) {
            Box(
                Modifier
                    // positionInParent is measured from the left edge in every layout direction, so the pill is
                    // anchored to the left edge and offset absolutely (start-aligned it sat off-screen in RTL).
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(pillOffset.value.roundToPx(), 0) }
                    .width(pillWidth.value)
                    .height(ChipHeight)
                    .clip(FindoraTheme.shapes.chip)
                    .background(FindoraTheme.colors.primaryAction),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    if (selected) FindoraTheme.colors.onPrimaryAction else FindoraTheme.colors.onSurfaceMuted,
                    motion.standard(),
                    label = "chipText",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .defaultMinSize(minHeight = ChipHeight)
                            .clip(FindoraTheme.shapes.chip)
                            .onPlaced { coordinates ->
                                // Measured in the Box's space, where the pill is drawn: in RTL a row narrower than
                                // the screen sits at the right edge, so its own offset has to be added.
                                val rowX = coordinates.parentLayoutCoordinates?.positionInParent()?.x ?: 0f
                                with(density) {
                                    bounds[index] =
                                        (rowX + coordinates.positionInParent().x).toDp() to
                                        coordinates.size.width.toDp()
                                }
                            }.selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                            .testTag("chip_$index"),
                ) {
                    Text(
                        text = label,
                        style = FindoraTheme.typography.bodyMedium,
                        color = textColor,
                        modifier =
                            Modifier.padding(
                                horizontal = FindoraTheme.spacing.md,
                                vertical = FindoraTheme.spacing.xs,
                            ),
                    )
                }
            }
        }
    }
}
