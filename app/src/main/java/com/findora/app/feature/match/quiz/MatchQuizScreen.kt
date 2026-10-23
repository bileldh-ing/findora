package com.findora.app.feature.match.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val ProgressHeight = 6.dp
private val BackButtonSize = 48.dp
private val BackIconSize = 22.dp

@Composable
fun MatchQuizRoute(
    onFinished: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchQuizViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MatchQuizUiEffect.Finished -> currentOnFinished()
                MatchQuizUiEffect.Closed -> currentOnClose()
            }
        }
    }
    MatchQuizScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * The preference quiz: an animated progress bar and "Step n of 6", one question per step sliding in from the
 * reading direction (a cross-fade when motion is reduced), Back (also the system back) and Skip at the top,
 * and Next / "See my matches" at the bottom.
 */
@Composable
fun MatchQuizScreen(
    state: MatchQuizUiState,
    onEvent: (MatchQuizUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    BackHandler { onEvent(MatchQuizUiEvent.Back) }
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = if (reduceMotion) snap() else FindoraTheme.motion.standard(),
        label = "quizProgress",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progressLabel = stringResource(R.string.quiz_progress, state.stepNumber, state.stepCount)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("matchQuiz"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.md),
        ) {
            QuizBackButton(
                label = stringResource(if (state.step.ordinal == 0) R.string.quiz_close else R.string.back),
                onClick = { onEvent(MatchQuizUiEvent.Back) },
            )
            Spacer(Modifier.weight(1f))
            TextLink(
                text = stringResource(R.string.quiz_skip),
                onClick = { onEvent(MatchQuizUiEvent.Skip) },
                enabled = !state.isSaving,
                modifier = Modifier.testTag("quizSkip"),
            )
        }
        Column(Modifier.padding(horizontal = FindoraTheme.spacing.screenHorizontal)) {
            Text(
                text = progressLabel,
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag("quizProgressLabel"),
            )
            Spacer(Modifier.height(FindoraTheme.spacing.xs))
            LinearProgressIndicator(
                progress = { progress },
                color = FindoraTheme.colors.primaryAction,
                trackColor = FindoraTheme.colors.outline,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ProgressHeight)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = progressLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                        }.testTag("quizProgress"),
            )
        }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                when {
                    reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                    else -> {
                        // Forward steps enter from the end side of the reading direction (left in Arabic).
                        val forward = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        val direction = if (rtl) -forward else forward
                        (slideInHorizontally { direction * it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -direction * it / 2 } + fadeOut())
                    }
                }
            },
            label = "quizStep",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { step ->
            Column(
                verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                        .padding(top = FindoraTheme.spacing.lg, bottom = FindoraTheme.spacing.md)
                        .testTag("quizStep_${step.name}"),
            ) {
                Text(
                    text = stringResource(step.titleRes()),
                    style = FindoraTheme.typography.headline,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                QuizStepContent(step = step, state = state, onEvent = onEvent)
            }
        }
        Box(
            Modifier.padding(
                horizontal = FindoraTheme.spacing.screenHorizontal,
                vertical = FindoraTheme.spacing.sm,
            ),
        ) {
            PrimaryPillButton(
                text = stringResource(if (state.isLastStep) R.string.quiz_finish else R.string.quiz_next),
                onClick = { onEvent(MatchQuizUiEvent.Next) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("quizNext"),
            )
        }
    }
}

@Composable
private fun QuizBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(BackButtonSize)
                .clip(CircleShape)
                .background(FindoraTheme.colors.surface)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag("quizBack"),
    ) {
        Icon(
            FindoraIcons.ArrowBack,
            contentDescription = null,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(BackIconSize),
        )
    }
}

private fun QuizStep.titleRes(): Int =
    when (this) {
        QuizStep.Budget -> R.string.quiz_budget_title
        QuizStep.Rooms -> R.string.quiz_rooms_title
        QuizStep.Types -> R.string.quiz_types_title
        QuizStep.Locations -> R.string.quiz_locations_title
        QuizStep.Lifestyle -> R.string.quiz_lifestyle_title
        QuizStep.MustHaves -> R.string.quiz_must_haves_title
    }
