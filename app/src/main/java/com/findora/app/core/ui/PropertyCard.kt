package com.findora.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.findora.app.R
import com.findora.app.core.designsystem.component.GlassChip
import com.findora.app.core.designsystem.component.ShimmerBox
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.model.Property
import com.findora.app.core.image.offlineFallback
import com.findora.app.core.ui.match.MatchBadge
import kotlinx.coroutines.launch
import java.util.Locale

private const val CARD_ASPECT = 0.92f
private const val SCRIM_START = 0.45f
private val BadgeSize = 26.dp
private val HeartButtonSize = 36.dp
private val HeartIconSize = 18.dp
private val StarSize = 12.dp
private val PinSize = 12.dp
private val BurstSize = 72.dp
private const val BURST_SCALE = 1.4f
private const val BURST_START = 0.6f
private const val LOCALITY_ALPHA = 0.9f
private const val BURST_MILLIS = 450

/**
 * The listing card from the reference: full-bleed photo (dominant-colour placeholder, offline fallback),
 * bottom scrim, verified shield, glass price pill, glass rating chip, name and locality. A heart button and
 * a double-tap on the photo save the home (double-tap only adds, with a heart burst); a single tap opens the
 * listing ([onClick]). [matchPercent] adds the "92% match" badge (only when the user has a preference profile).
 * TalkBack reads one summary (including the match), activates [onClick] and offers Save / Remove as a custom
 * action. The photo is the shared element that grows into the detail page.
 */
@Composable
fun PropertyCard(
    property: Property,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchPercent: Int? = null,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val price = PriceFormatter.formatPerMonth(property.rent, locale, stringResource(R.string.per_month))
    val rating = String.format(locale, "%.1f", property.rating)
    val summary =
        buildString {
            // TalkBack reads a currency-formatted amount ("₹10,000") rather than the design's "10.000/ Month".
            val spokenPrice = PriceFormatter.format(property.rent, locale, PriceStyle.Localized)
            append(stringResource(R.string.card_description, property.title, property.locality, spokenPrice, rating))
            if (property.verified) append(' ').append(stringResource(R.string.card_verified))
            if (matchPercent != null) append(' ').append(stringResource(R.string.match_badge, matchPercent)).append('.')
            if (isFavorite) append(' ').append(stringResource(R.string.card_saved))
        }
    val actionLabel = stringResource(if (isFavorite) R.string.action_unsave else R.string.action_save)
    val openLabel = stringResource(R.string.action_open_listing)
    val currentOnClick by rememberUpdatedState(onClick)
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .clip(FindoraTheme.shapes.card)
                .background(Color(property.coverImage.dominantColorArgb))
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                    role = Role.Button
                    onClick(label = openLabel) {
                        currentOnClick()
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(actionLabel) {
                                onToggleFavorite()
                                true
                            },
                        )
                }.pointerInput(isFavorite) {
                    detectTapGestures(
                        onTap = { currentOnClick() },
                        onDoubleTap = {
                            if (!isFavorite) onToggleFavorite()
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, tween(BURST_MILLIS))
                                burst.snapTo(0f)
                            }
                        },
                    )
                }.testTag("card_${property.id}"),
    ) {
        SharedPropertyPhoto(propertyId = property.id) { photoModifier -> CardPhoto(property, photoModifier) }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        SCRIM_START to Color.Transparent,
                        1f to FindoraTheme.colors.scrimEnd,
                    ),
                ),
        )
        CardOverlay(property = property, price = price, rating = rating, matchPercent = matchPercent)
        HeartButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            label = actionLabel,
            tag = "heart_${property.id}",
            modifier = Modifier.align(Alignment.BottomEnd).padding(FindoraTheme.spacing.sm),
        )
        if (burst.value > 0f) {
            Icon(
                imageVector = FindoraIcons.HeartFilled,
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(BurstSize)
                        .graphicsLayer {
                            val scale = BURST_START + burst.value * (BURST_SCALE - BURST_START)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - burst.value
                        },
            )
        }
    }
}

@Composable
private fun CardPhoto(
    property: Property,
    modifier: Modifier = Modifier,
) {
    val image = property.coverImage
    val context = LocalContext.current
    val request =
        remember(image.url) {
            ImageRequest
                .Builder(context)
                .data(image.url)
                .offlineFallback(image.fallbackAsset)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        placeholder = ColorPainter(Color(image.dominantColorArgb)),
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun CardOverlay(
    property: Property,
    price: String,
    rating: String,
    matchPercent: Int?,
    modifier: Modifier = Modifier,
) {
    val onPhoto = FindoraTheme.colors.onPhoto
    Column(modifier = modifier.fillMaxSize().padding(FindoraTheme.spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (property.verified) {
                Image(
                    painter = rememberVectorPainter(FindoraIcons.VerifiedBadge),
                    contentDescription = null,
                    modifier = Modifier.size(BadgeSize),
                )
            } else {
                Spacer(Modifier.size(BadgeSize))
            }
            GlassChip {
                Text(text = price, style = FindoraTheme.typography.label, color = onPhoto, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FindoraIcons.Star,
                        contentDescription = null,
                        tint = FindoraTheme.colors.star,
                        modifier = Modifier.size(StarSize),
                    )
                    Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                    Text(text = rating, style = FindoraTheme.typography.label, color = onPhoto)
                }
            }
            if (matchPercent != null) MatchBadge(percent = matchPercent)
        }
        Spacer(Modifier.size(FindoraTheme.spacing.xxs))
        Text(
            text = property.title,
            style = FindoraTheme.typography.bodyMedium,
            color = onPhoto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = HeartButtonSize),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = HeartButtonSize)) {
            Icon(FindoraIcons.MapPin, contentDescription = null, tint = onPhoto, modifier = Modifier.size(PinSize))
            Spacer(Modifier.width(FindoraTheme.spacing.xxs))
            Text(
                text = property.locality,
                style = FindoraTheme.typography.label,
                color = onPhoto.copy(alpha = LOCALITY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeartButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(HeartButtonSize)
                .clip(CircleShape)
                .background(FindoraTheme.colors.glassFill)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
    ) {
        Icon(
            imageVector = if (isFavorite) FindoraIcons.HeartFilled else FindoraIcons.Heart,
            contentDescription = null,
            tint = if (isFavorite) FindoraTheme.colors.error else Color.White,
            modifier = Modifier.size(HeartIconSize),
        )
    }
}

/** Card-shaped loading skeleton. */
@Composable
fun PropertyCardSkeleton(modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .clip(FindoraTheme.shapes.card),
    )
}
