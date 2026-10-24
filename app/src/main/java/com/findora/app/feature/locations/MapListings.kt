package com.findora.app.feature.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.ListingCoverPhoto
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.match.MatchBadge
import com.findora.app.core.ui.rentPerMonthText
import com.findora.app.core.ui.rentText
import com.findora.app.core.ui.spokenAreaText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.util.Locale
import kotlin.math.abs

private val CardHeight = 112.dp
private val PhotoSize = 112.dp
private val PinSize = 12.dp
private const val CAROUSEL_CARD_FRACTION = 0.86f

/**
 * The cards under the map, one per visible listing, in the same order as [MapUiState.listings].
 * Swiping settles on a card and reports it ([onSettle]) so the map pans to its marker; a new [selectedIndex]
 * (a tapped marker) scrolls the carousel to that card. Lazy rows mirror themselves in right-to-left locales.
 */
@Composable
fun ListingCarousel(
    state: MapUiState,
    onSettle: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val listings = state.listings
    val currentListings by rememberUpdatedState(listings)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val selectedIndex = state.selectedIndex
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 &&
            listState.centeredIndex() != selectedIndex
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(listState) {
        // Only a finished swipe selects: the first emission (no scroll yet) is skipped.
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .filter { inProgress -> !inProgress }
            .collect {
                currentListings.getOrNull(listState.centeredIndex())?.let { currentOnSettle(it.id) }
            }
    }
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = FindoraTheme.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier = modifier.fillMaxWidth().testTag("mapCarousel"),
    ) {
        items(listings, key = { it.id }) { home ->
            MapListingCard(
                property = home,
                selected = home.id == state.selectedId,
                matchPercent = state.matchScores[home.id],
                onClick = { onOpen(home.id) },
                modifier = Modifier.fillParentMaxWidth(CAROUSEL_CARD_FRACTION),
            )
        }
    }
}

/** The accessible alternative to the map: the same listings as a vertical list. */
@Composable
fun ListingList(
    state: MapUiState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("mapList"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
    ) {
        items(state.listings, key = { it.id }) { home ->
            MapListingCard(
                property = home,
                selected = home.id == state.selectedId,
                matchPercent = state.matchScores[home.id],
                onClick = { onOpen(home.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Compact horizontal listing card: photo, price, name, neighbourhood and specs; a tap opens the listing. */
@Composable
fun MapListingCard(
    property: Property,
    selected: Boolean,
    matchPercent: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val colors = FindoraTheme.colors
    val price = rentPerMonthText(property.rent, locale)
    val specs =
        stringResource(R.string.map_card_specs, property.bedrooms, property.bathrooms, areaText(property.areaSqm))
    val spokenSpecs =
        stringResource(
            R.string.map_card_specs_cd,
            property.bedrooms,
            property.bathrooms,
            spokenAreaText(property.areaSqm),
        )
    val rating = String.format(locale, "%.1f", property.rating)
    val spokenPrice = rentText(property.rent, locale)
    val summary =
        buildString {
            append(stringResource(R.string.card_description, property.title, property.locality, spokenPrice, rating))
            append(' ').append(spokenSpecs).append('.')
            if (property.verified) append(' ').append(stringResource(R.string.card_verified))
            if (matchPercent != null) append(' ').append(stringResource(R.string.match_badge, matchPercent)).append('.')
        }
    val openLabel = stringResource(R.string.action_open_listing)
    Row(
        modifier =
            modifier
                .height(CardHeight)
                .clip(FindoraTheme.shapes.card)
                .background(colors.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.accent else colors.outline,
                    shape = FindoraTheme.shapes.card,
                ).clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                    role = Role.Button
                    this.selected = selected
                    onClick(label = openLabel) {
                        onClick()
                        true
                    }
                }.testTag("mapCard_${property.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(PhotoSize).clip(FindoraTheme.shapes.card)) {
            ListingCoverPhoto(property)
            if (matchPercent != null) {
                MatchBadge(percent = matchPercent, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(text = price, style = FindoraTheme.typography.bodyMedium, color = colors.accent, maxLines = 1)
            Text(
                text = property.title,
                style = FindoraTheme.typography.bodyMedium,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FindoraIcons.MapPin, null, tint = colors.onSurfaceMuted, modifier = Modifier.size(PinSize))
                Text(
                    text = property.locality,
                    style = FindoraTheme.typography.caption,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(text = specs, style = FindoraTheme.typography.caption, color = colors.onSurfaceMuted, maxLines = 1)
        }
        Box(Modifier.width(FindoraTheme.spacing.xxs))
    }
}

/** The item whose centre is closest to the viewport centre (the card a snap fling settles on). */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: firstVisibleItemIndex
}
