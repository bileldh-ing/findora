package com.findora.app.feature.match.deck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.ShimmerBox
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.StateMessage
import kotlinx.coroutines.launch
import java.util.Locale

private val BigButton = 64.dp
private val SmallButton = 48.dp
private val BigIcon = 28.dp
private val SmallIcon = 22.dp
private val TopButton = 48.dp
private val ProgressHeight = 4.dp
private const val BEHIND_SCALE = 0.94f
private const val BEHIND_ALPHA = 0.85f

@Composable
fun DeckRoute(
    onBack: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenProperty: (String) -> Unit,
    onScheduleViewing: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnScheduleViewing by rememberUpdatedState(onScheduleViewing)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DeckUiEffect.OpenScheduleViewing -> currentOnScheduleViewing(effect.propertyId)
            }
        }
    }
    DeckScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenMatches = onOpenMatches,
        onOpenProperty = onOpenProperty,
        modifier = modifier,
    )
}

/**
 * Dream Match: a stack of listing cards (best match first) with "n / total" progress. Swipe right or Save to
 * keep a home, left or Skip to pass, up or Super like to save and book a viewing; Undo puts the last card back.
 * The buttons do everything the gestures do (and TalkBack offers them as card actions). Once the stack is
 * empty the user can see their matches or deal again.
 */
@Composable
fun DeckScreen(
    state: DeckUiState,
    onEvent: (DeckUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .testTag("deck"),
    ) {
        DeckTopBar(state = state, onBack = onBack, reduceMotion = reduceMotion)
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        when {
            state.isLoading -> ShimmerBox(Modifier.weight(1f).fillMaxWidth().clip(FindoraTheme.shapes.card))
            state.loadFailed ->
                StateMessage(
                    icon = FindoraIcons.AlertCircle,
                    title = stringResource(R.string.error_title),
                    body = stringResource(R.string.error_body),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { onEvent(DeckUiEvent.Retry) },
                    modifier = Modifier.weight(1f).testTag("deckError"),
                )
            state.isFinished ->
                DeckFinished(
                    state = state,
                    onOpenMatches = onOpenMatches,
                    onRestart = { onEvent(DeckUiEvent.Restart) },
                    onUndo = { onEvent(DeckUiEvent.Undo) },
                    modifier = Modifier.weight(1f),
                )
            else -> DeckStack(state, onEvent, onOpenProperty, reduceMotion, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeckTopBar(
    state: DeckUiState,
    onBack: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = if (reduceMotion) snap() else tween(FindoraTheme.motion.mediumMillis),
        label = "deckProgress",
    )
    Column(modifier.fillMaxWidth().padding(top = FindoraTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = FindoraIcons.ArrowBack,
                label = stringResource(R.string.back),
                onClick = onBack,
                size = TopButton,
                iconSize = SmallIcon,
                tag = "deckBack",
            )
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.deck_title),
                    style = FindoraTheme.typography.title,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text =
                        stringResource(
                            if (state.rankedByMatch) R.string.deck_ranked_match else R.string.deck_ranked_recommended,
                        ),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
            if (state.total > 0) {
                val spoken = stringResource(R.string.deck_progress_description, state.position, state.total)
                Text(
                    text = stringResource(R.string.deck_progress, state.position, state.total),
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onBackground,
                    modifier =
                        Modifier.testTag("deckProgress").clearAndSetSemantics {
                            contentDescription = spoken
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(ProgressHeight)
                .clip(CircleShape)
                .background(FindoraTheme.colors.outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(ProgressHeight)
                    .clip(CircleShape)
                    .background(FindoraTheme.colors.primaryAction),
            )
        }
    }
}

@Composable
private fun DeckStack(
    state: DeckUiState,
    onEvent: (DeckUiEvent) -> Unit,
    onOpenProperty: (String) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    val swipe = remember(current.property.id) { DeckSwipeState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val act: (DeckAction) -> Unit = { action -> onEvent(DeckUiEvent.Act(action)) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { cardSize = it }) {
            state.next?.let { next ->
                key(next.property.id) {
                    DeckCardContent(
                        entry = next,
                        rating = String.format(locale, "%.1f", next.property.rating),
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    scaleX = BEHIND_SCALE
                                    scaleY = BEHIND_SCALE
                                    alpha = BEHIND_ALPHA
                                }.clip(FindoraTheme.shapes.card)
                                .clearAndSetSemantics { },
                    )
                }
            }
            key(current.property.id) {
                DeckCard(
                    entry = current,
                    swipe = swipe,
                    onCommit = act,
                    onOpen = { onOpenProperty(current.property.id) },
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        DeckButtons(
            canUndo = state.canUndo,
            onUndo = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEvent(DeckUiEvent.Undo)
            },
            onAction = { action ->
                if (!swipe.isCommitting) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        swipe.fling(action, cardSize.width.toFloat(), cardSize.height.toFloat(), reduceMotion)
                        act(action)
                    }
                }
            },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.md))
    }
}

/**
 * Undo, Skip, Super like, Save. Laid out left to right in every language so each button sits on the side its
 * swipe goes (Skip left, Save right), matching the physical gesture directions.
 */
@Composable
private fun DeckButtons(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onAction: (DeckAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth(),
        ) {
            RoundIconButton(
                icon = FindoraIcons.Undo,
                label = stringResource(R.string.deck_action_undo),
                onClick = onUndo,
                size = SmallButton,
                iconSize = SmallIcon,
                enabled = canUndo,
                tag = "deckUndo",
            )
            RoundIconButton(
                icon = FindoraIcons.Close,
                label = stringResource(R.string.deck_action_skip),
                onClick = { onAction(DeckAction.Skip) },
                size = BigButton,
                iconSize = BigIcon,
                tint = FindoraTheme.colors.error,
                tag = "deckSkip",
            )
            RoundIconButton(
                icon = FindoraIcons.Sparkles,
                label = stringResource(R.string.deck_action_super_like),
                onClick = { onAction(DeckAction.SuperLike) },
                size = SmallButton,
                iconSize = SmallIcon,
                tint = FindoraTheme.colors.accent,
                tag = "deckSuperLike",
            )
            RoundIconButton(
                icon = FindoraIcons.HeartFilled,
                label = stringResource(R.string.deck_action_save),
                onClick = { onAction(DeckAction.Save) },
                size = BigButton,
                iconSize = BigIcon,
                tint = FindoraTheme.colors.success,
                tag = "deckSave",
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = FindoraTheme.colors.onBackground,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(FindoraTheme.colors.surface)
                .border(1.dp, FindoraTheme.colors.outline, CircleShape)
                .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else FindoraTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun DeckFinished(
    state: DeckUiState,
    onOpenMatches: () -> Unit,
    onRestart: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = state.total == 0
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().testTag("deckFinished"),
    ) {
        StateMessage(
            icon = FindoraIcons.Sparkles,
            title = stringResource(if (empty) R.string.deck_empty_title else R.string.deck_done_title),
            body =
                if (empty) {
                    stringResource(R.string.deck_empty_body)
                } else {
                    pluralStringResource(R.plurals.deck_done_body, state.savedCount, state.savedCount)
                },
        )
        PrimaryPillButton(
            text = stringResource(R.string.deck_see_matches),
            onClick = onOpenMatches,
            modifier = Modifier.testTag("deckSeeMatches"),
        )
        SecondaryPillButton(
            text = stringResource(R.string.deck_restart),
            onClick = onRestart,
            modifier = Modifier.testTag("deckRestart"),
        )
        if (state.canUndo) {
            SecondaryPillButton(
                text = stringResource(R.string.deck_action_undo),
                onClick = onUndo,
                modifier = Modifier.testTag("deckUndoLast"),
            )
        }
    }
}
