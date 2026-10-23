package com.findora.app.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.DisplayMoney
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.displayMoney
import com.findora.app.feature.home.labelRes
import java.util.Locale
import kotlin.math.roundToLong

private const val COUNT_UP_MILLIS = 900
private const val COLLAPSED_LINES = 4
private val SpecIconSize = 22.dp
private val SmallIconSize = 16.dp
private val BadgeSize = 20.dp
private val SummaryGap = 6.dp
private val StarSize = 18.dp
private val MinTouchTarget = 48.dp

/** Section heading, announced as a heading by TalkBack. */
@Composable
internal fun SectionTitle(
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

/** Title, locality, the price counting up to the rent, rating with reviews and the verified badge. */
@Composable
fun SummarySection(
    property: Property,
    onShowVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(
        modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(SummaryGap),
    ) {
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
        CountUpPrice(display = displayMoney(property.rent), locale = locale)
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
    display: DisplayMoney,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val money = display.money
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
        stringResource(
            R.string.detail_price_per_month,
            PriceFormatter.formatDisplay(display, locale, PriceStyle.Localized),
        )
    val shownText = PriceFormatter.formatDisplay(DisplayMoney(shown, display.approximate), locale, PriceStyle.Localized)
    Text(
        text = stringResource(R.string.detail_price_per_month, shownText),
        style = FindoraTheme.typography.title,
        color = FindoraTheme.colors.accent,
        modifier = modifier.testTag("detailPrice").clearAndSetSemantics { contentDescription = finalText },
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
        modifier = modifier.testTag("detailRating").clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            FindoraIcons.Star,
            contentDescription = null,
            tint = FindoraTheme.colors.star,
            modifier = Modifier.size(StarSize),
        )
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
        Image(
            rememberVectorPainter(FindoraIcons.VerifiedBadge),
            contentDescription = null,
            modifier = Modifier.size(BadgeSize),
        )
        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
        Text(
            text = stringResource(R.string.detail_verified),
            style = FindoraTheme.typography.bodyMedium,
            color = FindoraTheme.colors.accent,
        )
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
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FindoraTheme.spacing.screenHorizontal,
                ).testTag("detailSpecs"),
    ) {
        val beds = stringResource(R.string.detail_bedrooms)
        val baths = stringResource(R.string.detail_bathrooms)
        SpecItem(FindoraIcons.Bed, property.bedrooms.toString(), beds, Modifier.weight(1f))
        SpecItem(FindoraIcons.Bath, property.bathrooms.toString(), baths, Modifier.weight(1f))
        SpecItem(
            FindoraIcons.Area,
            areaText(property.areaSqm),
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
        Icon(
            icon,
            contentDescription = null,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(SpecIconSize),
        )
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
    Column(modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal)) {
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
    Column(modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal)) {
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
