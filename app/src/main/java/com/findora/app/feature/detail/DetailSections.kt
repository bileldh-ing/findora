package com.findora.app.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.model.Agent
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.PropertyCard
import com.findora.app.feature.home.labelRes
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

private const val COUNT_UP_MILLIS = 900
private const val COLLAPSED_LINES = 4
private const val MINUTES_PER_HOUR = 60
private val AvatarSize = 48.dp
private val SpecIconSize = 22.dp
private val SmallIconSize = 16.dp
private val BadgeSize = 20.dp
private val SimilarCardWidth = 200.dp
private val MinTouchTarget = 48.dp

@Composable
private fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/** Section heading, announced as a heading by TalkBack. */
@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

private val horizontalPadding: Modifier
    @Composable get() = Modifier.padding(horizontal = FindoraTheme.spacing.screenHorizontal)

/** Title, locality, the price counting up to the rent, rating with reviews and the verified badge. */
@Composable
fun SummarySection(
    property: Property,
    onShowVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(modifier.fillMaxWidth().then(horizontalPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = property.title,
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() }.testTag("detailTitle"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                FindoraIcons.MapPin,
                contentDescription = null,
                tint = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(SmallIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.xxs))
            Text(property.locality, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted)
        }
        CountUpPrice(money = property.rent, locale = locale)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingRow(rating = property.rating, reviews = property.reviewsCount, locale = locale)
            Spacer(Modifier.weight(1f))
            if (property.verified) VerifiedChip(onClick = onShowVerification)
        }
    }
}

/** The rent animates from zero on first display (skipped with reduced motion); TalkBack reads the final amount. */
@Composable
private fun CountUpPrice(
    money: Money,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val target = money.amount.toFloat()
    val animated = remember(money) { Animatable(if (reduceMotion) target else 0f) }
    LaunchedEffect(money, reduceMotion) {
        if (reduceMotion) {
            animated.snapTo(target)
        } else {
            animated.animateTo(target, tween(COUNT_UP_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val shown = Money(animated.value.roundToLong().coerceIn(0L, money.amount), money.currency)
    val finalText =
        stringResource(R.string.detail_price_per_month, PriceFormatter.format(money, locale, PriceStyle.Localized))
    Text(
        text = stringResource(R.string.detail_price_per_month, PriceFormatter.format(shown, locale, PriceStyle.Localized)),
        style = FindoraTheme.typography.title,
        color = FindoraTheme.colors.accent,
        modifier = modifier.clearAndSetSemantics { contentDescription = finalText }.testTag("detailPrice"),
    )
}

@Composable
private fun RatingRow(
    rating: Double,
    reviews: Int,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val ratingText = String.format(locale, "%.1f", rating)
    val reviewsText = pluralStringResource(R.plurals.detail_reviews, reviews, reviews)
    val description = stringResource(R.string.detail_rating_cd, ratingText) + ", " + reviewsText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description }.testTag("detailRating"),
    ) {
        Icon(FindoraIcons.Star, contentDescription = null, tint = FindoraTheme.colors.star, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(ratingText, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text("($reviewsText)", style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun VerifiedChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.detail_verified_cd)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .clip(FindoraTheme.shapes.chip)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = FindoraTheme.spacing.xs)
                .testTag("detailVerified"),
    ) {
        Image(rememberVectorPainter(FindoraIcons.VerifiedBadge), contentDescription = null, modifier = Modifier.size(BadgeSize))
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(
            text = stringResource(R.string.detail_verified),
            style = FindoraTheme.typography.bodyMedium,
            color = FindoraTheme.colors.accent,
        )
    }
}

/** Explains what the verified badge checks (and what it does not). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FindoraTheme.shapes.bottomSheetTop,
        containerColor = FindoraTheme.colors.background,
        modifier = modifier.testTag("verificationSheet"),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .padding(bottom = FindoraTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(rememberVectorPainter(FindoraIcons.VerifiedBadge), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(FindoraTheme.spacing.xs))
                SectionTitle(stringResource(R.string.detail_verified_title))
            }
            Text(
                text = stringResource(R.string.detail_verified_body),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onBackground,
            )
            PrimaryPillButton(
                text = stringResource(R.string.detail_verified_ok),
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Bedrooms, bathrooms, area (metric: the app has no unit setting) and floor. */
@Composable
fun SpecsRow(
    property: Property,
    modifier: Modifier = Modifier,
) {
    val floor =
        if (property.floor == 0) stringResource(R.string.detail_floor_ground) else property.floor.toString()
    Row(
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().then(horizontalPadding).testTag("detailSpecs"),
    ) {
        SpecItem(FindoraIcons.Bed, property.bedrooms.toString(), stringResource(R.string.detail_bedrooms), Modifier.weight(1f))
        SpecItem(FindoraIcons.Bath, property.bathrooms.toString(), stringResource(R.string.detail_bathrooms), Modifier.weight(1f))
        SpecItem(
            FindoraIcons.Area,
            stringResource(R.string.detail_area_value, property.areaSqm),
            stringResource(R.string.detail_area),
            Modifier.weight(1f),
        )
        SpecItem(FindoraIcons.Layers, floor, stringResource(R.string.detail_floor), Modifier.weight(1f))
    }
}

@Composable
private fun SpecItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .padding(vertical = FindoraTheme.spacing.sm, horizontal = FindoraTheme.spacing.xxs)
                .clearAndSetSemantics { contentDescription = "$label: $value" },
    ) {
        Icon(icon, contentDescription = null, tint = FindoraTheme.colors.onBackground, modifier = Modifier.size(SpecIconSize))
        Text(value, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground, maxLines = 1)
        Text(
            label,
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The description clipped to four lines with Read more / Show less when it is longer. */
@Composable
fun DescriptionSection(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }
    val reduceMotion = rememberReducedMotion()
    Column(modifier.fillMaxWidth().then(horizontalPadding)) {
        SectionTitle(stringResource(R.string.detail_about))
        Spacer(Modifier.size(FindoraTheme.spacing.xs))
        Text(
            text = description,
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = (if (reduceMotion) Modifier else Modifier.animateContentSize()).testTag("detailDescription"),
        )
        if (overflows || expanded) {
            TextLink(
                text = stringResource(if (expanded) R.string.detail_show_less else R.string.detail_read_more),
                onClick = { expanded = !expanded },
                color = FindoraTheme.colors.accent,
                modifier = Modifier.testTag("detailReadMore"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmenitiesSection(
    amenities: List<Amenity>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().then(horizontalPadding)) {
        SectionTitle(stringResource(R.string.filter_amenities))
        Spacer(Modifier.size(FindoraTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.fillMaxWidth().testTag("detailAmenities"),
        ) {
            amenities.forEach { amenity ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(FindoraTheme.shapes.chip)
                            .background(FindoraTheme.colors.surface)
                            .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xs),
                ) {
                    Icon(
                        FindoraIcons.Check,
                        contentDescription = null,
                        tint = FindoraTheme.colors.accent,
                        modifier = Modifier.size(SmallIconSize),
                    )
                    Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                    Text(
                        stringResource(amenity.labelRes()),
                        style = FindoraTheme.typography.caption,
                        color = FindoraTheme.colors.onBackground,
                    )
                }
            }
        }
    }
}

/** Initials avatar, name, typical response time, and Call (dialer) / Email (mail app) actions. */
@Composable
fun AgentCard(
    agent: Agent,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val response =
        if (agent.responseTimeMinutes < MINUTES_PER_HOUR) {
            stringResource(R.string.detail_agent_response_minutes, agent.responseTimeMinutes)
        } else {
            stringResource(R.string.detail_agent_response_hours, agent.responseTimeMinutes / MINUTES_PER_HOUR)
        }
    val callLabel = stringResource(R.string.detail_call_cd, agent.name)
    val emailLabel = stringResource(R.string.detail_email_cd, agent.name)
    Column(modifier.fillMaxWidth().then(horizontalPadding)) {
        SectionTitle(stringResource(R.string.detail_agent))
        Spacer(Modifier.size(FindoraTheme.spacing.sm))
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FindoraTheme.shapes.card)
                    .background(FindoraTheme.colors.surface)
                    .padding(FindoraTheme.spacing.md)
                    .testTag("detailAgent"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(AvatarSize).clip(CircleShape).background(Color(agent.avatar.backgroundArgb)),
                ) {
                    Text(agent.avatar.initials, style = FindoraTheme.typography.bodyMedium, color = Color.White)
                }
                Spacer(Modifier.width(FindoraTheme.spacing.sm))
                Column {
                    Text(agent.name, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            FindoraIcons.Clock,
                            contentDescription = null,
                            tint = FindoraTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(SmallIconSize),
                        )
                        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                        Text(response, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                PrimaryPillButton(
                    text = stringResource(R.string.detail_call),
                    onClick = onCall,
                    modifier = Modifier.weight(1f).semantics { contentDescription = callLabel }.testTag("detailCall"),
                )
                SecondaryPillButton(
                    text = stringResource(R.string.detail_email),
                    onClick = onEmail,
                    modifier = Modifier.weight(1f).semantics { contentDescription = emailLabel }.testTag("detailEmail"),
                )
            }
        }
    }
}

/** Locality and coordinates with a button that hands the point to any installed maps app (no API key). */
@Composable
fun LocationSection(
    property: Property,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().then(horizontalPadding).testTag("detailLocation"),
    ) {
        SectionTitle(stringResource(R.string.detail_location))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(
                FindoraIcons.FoldedMap,
                contentDescription = null,
                tint = FindoraTheme.colors.onBackground,
                modifier = Modifier.size(SpecIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Column {
                Text(property.locality, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
                Text(
                    text = stringResource(R.string.detail_coordinates, formatCoordinates(property.location)),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        SecondaryPillButton(
            text = stringResource(R.string.detail_open_maps),
            onClick = onOpenMap,
            modifier = Modifier.fillMaxWidth().testTag("detailOpenMap"),
        )
    }
}

/** Horizontal row of homes of the same type in the same region; each opens its own detail page. */
@Composable
fun SimilarSection(
    homes: List<Property>,
    favoriteIds: Set<String>,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.detail_similar), horizontalPadding)
        Spacer(Modifier.size(FindoraTheme.spacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = FindoraTheme.spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
            modifier = Modifier.fillMaxWidth().testTag("detailSimilar"),
        ) {
            items(homes, key = { it.id }) { home ->
                PropertyCard(
                    property = home,
                    isFavorite = home.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(home.id) },
                    onClick = { onOpen(home.id) },
                    modifier = Modifier.width(SimilarCardWidth),
                )
            }
        }
    }
}
