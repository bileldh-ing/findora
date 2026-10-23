package com.findora.app.feature.auth.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.ConcaveArcShape
import com.findora.app.core.designsystem.component.FindoraLogoBadge
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.auth.common.FormErrorText

private const val HERO_FRACTION = 0.6f
private val HeroMinHeight = 340.dp
private val LandscapeMaxHeight = 480.dp
private val PanelMaxWidth = 560.dp
private val ArcDepth = 44.dp
private const val KEN_BURNS_SCALE = 1.12f
private const val KEN_BURNS_MILLIS = 14_000
private const val PARALLAX_FACTOR = 0.5f
private const val BADGE_START_SCALE = 0.6f
private const val STAGGER_MILLIS = 110
private val EntranceRise = 24.dp
private val WelcomeTitleSize = 40.sp
private val BrandSize = 34.sp
private val TaglineSize = 18.sp
private val SubtitleSize = 16.sp
private val SubtitleLineHeight = 24.sp
private const val TITLE_ALPHA = 0.86f
private val ScrimColor = Color(0x73000000)

@Composable
fun WelcomeRoute(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WelcomeUiEffect.SignedIn -> currentOnSignedIn()
            }
        }
    }
    WelcomeScreen(
        state = state,
        onLogin = onLogin,
        onSignUp = onSignUp,
        onTryDemo = { viewModel.onEvent(WelcomeUiEvent.TryDemo) },
        onContinueAsGuest = { viewModel.onEvent(WelcomeUiEvent.ContinueAsGuest) },
        modifier = modifier,
    )
}

/**
 * Welcome, pixel-faithful to the reference: full-bleed hero photo cut by a concave arc, the logo badge and
 * "FINDORA / Dream House" over a soft scrim, then WELCOME, the subtitle and the two pills. Below them the
 * demo-account and guest shortcuts. The photo drifts (Ken Burns), the content rises in a stagger, and the
 * photo scrolls slower than the panel (parallax); all motion is skipped when animations are turned off.
 */
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onTryDemo: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    val scroll = rememberScrollState()
    val entrance = rememberEntrance(reduceMotion)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("welcome"),
    ) {
        if (maxWidth > maxHeight && maxHeight < LandscapeMaxHeight) {
            // Landscape phone (or a short window): the hero on the start side, the panel scrolling beside it.
            Row(Modifier.fillMaxSize().testTag("welcomeLandscape")) {
                WelcomeHero(
                    badgeScale = entrance.badgeScale,
                    parallaxPx = 0f,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scroll)
                            .safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(FindoraTheme.spacing.md))
                    WelcomePanel(
                        state = state,
                        entrance = entrance,
                        onLogin = onLogin,
                        onSignUp = onSignUp,
                        onTryDemo = onTryDemo,
                        onContinueAsGuest = onContinueAsGuest,
                    )
                }
            }
        } else {
            val heroHeight = (maxHeight * HERO_FRACTION).coerceAtLeast(HeroMinHeight)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WelcomeHero(
                    badgeScale = entrance.badgeScale,
                    parallaxPx = scroll.value * PARALLAX_FACTOR,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxWidth().height(heroHeight),
                )
                Spacer(Modifier.height(FindoraTheme.spacing.lg))
                WelcomePanel(
                    state = state,
                    entrance = entrance,
                    onLogin = onLogin,
                    onSignUp = onSignUp,
                    onTryDemo = onTryDemo,
                    onContinueAsGuest = onContinueAsGuest,
                    modifier = Modifier.widthIn(max = PanelMaxWidth),
                )
            }
        }
    }
}

@Composable
private fun WelcomeHero(
    badgeScale: Float,
    parallaxPx: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val zoom =
        if (reduceMotion) {
            1f
        } else {
            val transition = rememberInfiniteTransition(label = "kenBurns")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = KEN_BURNS_SCALE,
                animationSpec = infiniteRepeatable(tween(KEN_BURNS_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
                label = "kenBurnsScale",
            )
            value
        }
    Box(modifier = modifier.clip(ConcaveArcShape(ArcDepth)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.welcome_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationY = parallaxPx
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(ScrimColor, Color.Transparent))),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = ArcDepth),
        ) {
            FindoraLogoBadge(
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    },
            )
            Spacer(Modifier.height(FindoraTheme.spacing.sm))
            Text(
                text = stringResource(R.string.brand_name),
                style = FindoraTheme.typography.headline.copy(fontSize = BrandSize, lineHeight = BrandSize),
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.brand_tagline),
                style = FindoraTheme.typography.section.copy(fontSize = TaglineSize),
                color = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePanel(
    state: WelcomeUiState,
    entrance: Entrance,
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onTryDemo: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rise = with(LocalDensity.current) { EntranceRise.toPx() }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.buttonHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.staggered(entrance.title, rise),
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = FindoraTheme.typography.display.copy(fontSize = WelcomeTitleSize, letterSpacing = 0.02.em),
                color = FindoraTheme.colors.onBackground.copy(alpha = TITLE_ALPHA),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(FindoraTheme.spacing.sm))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = FindoraTheme.typography.body.copy(fontSize = SubtitleSize, lineHeight = SubtitleLineHeight),
                color = FindoraTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.xl))
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.staggered(entrance.buttons, rise),
        ) {
            PrimaryPillButton(text = stringResource(R.string.action_login), onClick = onLogin, enabled = !state.isBusy)
            SecondaryPillButton(
                text = stringResource(R.string.action_sign_up),
                onClick = onSignUp,
                enabled = !state.isBusy,
            )
            state.demoLockoutSeconds?.let { FormErrorText(text = stringResource(R.string.error_locked_out, it)) }
            if (state.demoUnavailable) FormErrorText(text = stringResource(R.string.error_demo_unavailable))
            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextLink(
                    text = stringResource(R.string.action_try_demo),
                    onClick = onTryDemo,
                    color = FindoraTheme.colors.accent,
                    enabled = !state.isBusy,
                )
                TextLink(
                    text = stringResource(R.string.action_continue_guest),
                    onClick = onContinueAsGuest,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    enabled = !state.isBusy,
                )
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
    }
}

/** Animated progress (0..1) of each entrance step. */
private class Entrance(
    val badgeScale: Float,
    val title: Float,
    val buttons: Float,
)

@Composable
private fun rememberEntrance(reduceMotion: Boolean): Entrance {
    val badge = remember { Animatable(if (reduceMotion) 1f else BADGE_START_SCALE) }
    val title = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val buttons = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val motion = FindoraTheme.motion
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        badge.animateTo(1f, motion.bouncy())
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        title.animateTo(1f, tween(motion.longMillis, delayMillis = STAGGER_MILLIS))
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        buttons.animateTo(1f, tween(motion.longMillis, delayMillis = 2 * STAGGER_MILLIS))
    }
    return Entrance(badge.value, title.value, buttons.value)
}

private fun Modifier.staggered(
    progress: Float,
    risePx: Float,
): Modifier =
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * risePx
    }
