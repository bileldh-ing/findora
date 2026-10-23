package com.findora.app.feature.match.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.component.GlassChip
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.match.DeckEntry
import com.findora.app.core.ui.ListingCoverPhoto
import com.findora.app.core.ui.match.MatchBadge
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private const val SCRIM_START = 0.4f
private const val STAMP_ROTATION = 14f
private val StampBorder = 3.dp
private val SpecIcon = 14.dp

/** Fling speed (per second) that commits a swipe however short the drag. */
private val FlingVelocity = 1000.dp

/**
 * The top card of the deck: photo, price, rating, match, specs. Dragging moves and tilts it with SAVE / SKIP /
 * SUPER LIKE stamps fading in; releasing past the distance or fling-velocity threshold commits the action
 * ([DeckSwipeState.decide]), otherwise it springs back. A tap opens the listing. TalkBack reads one summary and
 * offers Save, Skip and Super like as custom actions, so no gesture is ever required.
 */
@Composable
fun DeckCard(
    entry: DeckEntry,
    swipe: DeckSwipeState,
    onCommit: (DeckAction) -> Unit,
    onOpen: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val property = entry.property
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val spokenPrice = PriceFormatter.format(property.rent, locale, PriceStyle.Localized)
    val rating = String.format(locale, "%.1f", property.rating)
    val summary =
        buildString {
            append(stringResource(R.string.card_description, property.title, property.locality, spokenPrice, rating))
            if (entry.matchPercent != null) {
                append(' ').append(stringResource(R.string.match_badge, entry.matchPercent)).append('.')
            }
            append(' ').append(stringResource(R.string.deck_card_hint))
        }
    val saveLabel = stringResource(R.string.deck_action_save)
    val skipLabel = stringResource(R.string.deck_action_skip)
    val superLabel = stringResource(R.string.deck_action_super_like)
    val openLabel = stringResource(R.string.action_open_listing)
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val commit: (DeckAction) -> Unit = { action ->
        if (!swipe.isCommitting) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                swipe.fling(action, cardSize.width.toFloat(), cardSize.height.toFloat(), reduceMotion)
                currentOnCommit(action)
            }
        }
    }
    Box(
        modifier =
            modifier
                .onSizeChanged { cardSize = it }
                .graphicsLayer {
                    translationX = swipe.offsetX.value
                    translationY = swipe.offsetY.value
                    rotationZ = if (reduceMotion) 0f else swipe.rotation(size.width)
                }.clip(FindoraTheme.shapes.card)
                .background(Color(property.coverImage.dominantColorArgb))
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                    onClick(label = openLabel) {
                        currentOnOpen()
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(saveLabel) { true.also { commit(DeckAction.Save) } },
                            CustomAccessibilityAction(skipLabel) { true.also { commit(DeckAction.Skip) } },
                            CustomAccessibilityAction(superLabel) { true.also { commit(DeckAction.SuperLike) } },
                        )
                }.pointerInput(property.id) { detectTapGestures(onTap = { currentOnOpen() }) }
                .pointerInput(property.id, reduceMotion) {
                    val tracker = VelocityTracker()
                    var thresholdCrossed = false
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            thresholdCrossed = false
                        },
                        onDragEnd = {
                            val offset = Offset(swipe.offsetX.value, swipe.offsetY.value)
                            val action =
                                DeckSwipeState.decide(
                                    offset = offset,
                                    velocity = tracker.calculateVelocity(),
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    flingVelocity = FlingVelocity.toPx(),
                                )
                            if (action == null) scope.launch { swipe.reset(reduceMotion) } else commit(action)
                        },
                        onDragCancel = { scope.launch { swipe.reset(reduceMotion) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            swipe.dragBy(dragAmount)
                            tracker.addPosition(change.uptimeMillis, Offset(swipe.offsetX.value, swipe.offsetY.value))
                            val crossed =
                                abs(swipe.horizontalProgress(size.width.toFloat())) >= 1f ||
                                    swipe.upwardProgress(size.height.toFloat()) >= 1f
                            if (crossed && !thresholdCrossed) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            thresholdCrossed = crossed
                        }
                    }
                }.testTag("deckCard_${property.id}"),
    ) {
        DeckCardContent(entry = entry, rating = rating)
        DeckStamps(swipe = swipe, cardSize = cardSize, saveLabel = saveLabel, skipLabel = skipLabel, superLabel = superLabel)
    }
}

/** A card's static content, also used for the card waiting underneath the top one. */
@Composable
fun DeckCardContent(
    entry: DeckEntry,
    rating: String,
    modifier: Modifier = Modifier,
) {
    val property = entry.property
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val price = PriceFormatter.formatPerMonth(property.rent, locale, stringResource(R.string.per_month))
    val onPhoto = FindoraTheme.colors.onPhoto
    Box(modifier.fillMaxSize()) {
        ListingCoverPhoto(property, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(SCRIM_START to Color.Transparent, 1f to FindoraTheme.colors.scrimEnd)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
            modifier = Modifier.align(Alignment.BottomStart).padding(FindoraTheme.spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs)) {
                GlassChip { Text(price, style = FindoraTheme.typography.label, color = onPhoto, maxLines = 1) }
                GlassChip {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FindoraIcons.Star, null, tint = FindoraTheme.colors.star, modifier = Modifier.size(SpecIcon))
                        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                        Text(rating, style = FindoraTheme.typography.label, color = onPhoto)
                    }
                }
                entry.matchPercent?.let { MatchBadge(percent = it) }
            }
            Text(
                text = property.title,
                style = FindoraTheme.typography.title,
                color = onPhoto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spec(FindoraIcons.MapPin, property.locality)
                Spec(FindoraIcons.Bed, property.bedrooms.toString())
                Spec(FindoraIcons.Bath, property.bathrooms.toString())
                Spec(FindoraIcons.Area, stringResource(R.string.detail_area_value, property.areaSqm))
            }
        }
    }
}

@Composable
private fun Spec(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = FindoraTheme.colors.onPhoto, modifier = Modifier.size(SpecIcon))
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(
            text = text,
            style = FindoraTheme.typography.label,
            color = FindoraTheme.colors.onPhoto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** SAVE (start of the card), SKIP (end) and SUPER LIKE (bottom) stamps, fading in with the drag. */
@Composable
private fun DeckStamps(
    swipe: DeckSwipeState,
    cardSize: IntSize,
    saveLabel: String,
    skipLabel: String,
    superLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(FindoraTheme.spacing.lg)) {
        Stamp(
            text = saveLabel,
            color = FindoraTheme.colors.success,
            alpha = { swipe.horizontalProgress(cardSize.width.toFloat()).coerceAtLeast(0f) },
            rotation = -STAMP_ROTATION,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Stamp(
            text = skipLabel,
            color = FindoraTheme.colors.error,
            alpha = { (-swipe.horizontalProgress(cardSize.width.toFloat())).coerceAtLeast(0f) },
            rotation = STAMP_ROTATION,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Stamp(
            text = superLabel,
            color = FindoraTheme.colors.accent,
            alpha = { swipe.upwardProgress(cardSize.height.toFloat()) },
            rotation = 0f,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun Stamp(
    text: String,
    color: Color,
    alpha: () -> Float,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    Text(
        text = text.uppercase(locale),
        style = FindoraTheme.typography.headline,
        color = color,
        modifier =
            modifier
                .graphicsLayer {
                    this.alpha = alpha()
                    rotationZ = rotation
                }.border(StampBorder, color, FindoraTheme.shapes.chip)
                .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs),
    )
}
