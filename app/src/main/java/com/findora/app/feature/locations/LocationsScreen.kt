package com.findora.app.feature.locations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.InlineBanner
import com.findora.app.core.ui.StateMessage
import com.findora.app.feature.locations.map.OsmListingsMap

private val ControlSize = 48.dp
private val ControlIconSize = 22.dp

@Composable
fun LocationsRoute(
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenProperty by rememberUpdatedState(onOpenProperty)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MapUiEffect.OpenProperty -> currentOnOpenProperty(effect.propertyId)
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onEvent(if (granted) MapUiEvent.LocateMe else MapUiEvent.LocationPermissionDenied)
        }
    LocationsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onMyLocation = {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.onEvent(
                    MapUiEvent.LocateMe,
                )
            } else {
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * The Locations tab: an OpenStreetMap map of the current region with price-pill markers and a synced card
 * carousel, or (list view) the same listings as an accessible list. [map] draws the map; tests replace it.
 */
@Composable
fun LocationsScreen(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    map: @Composable (Modifier) -> Unit = { mapModifier -> OsmListingsMap(state, onEvent, mapModifier) },
) {
    val spacing = FindoraTheme.spacing
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("locations"),
    ) {
        if (state.mode == MapMode.Map) {
            map(Modifier.fillMaxSize())
        } else {
            ListingList(
                state = state,
                onOpen = { onEvent(MapUiEvent.OpenListing(it)) },
                contentPadding =
                    PaddingValues(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = contentPadding.calculateTopPadding() + TOP_CONTROLS_HEIGHT.dp,
                        bottom = contentPadding.calculateBottomPadding() + spacing.md,
                    ),
            )
        }
        TopControls(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
        )
        if (state.mode == MapMode.Map) {
            BottomControls(
                state = state,
                onEvent = onEvent,
                onMyLocation = onMyLocation,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = contentPadding.calculateBottomPadding() + spacing.xs),
            )
        } else {
            EmptyState(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun TopControls(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FindoraTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.xs, end = spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val switcherLabel = stringResource(R.string.map_region_switcher)
            LazyRow(
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { paneTitle = switcherLabel }
                        .testTag("regionSwitcher"),
                contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                items(state.regions, key = { it.id }) { region ->
                    CategoryChip(
                        text = region.name,
                        selected = region.id == state.region?.id,
                        onClick = { onEvent(MapUiEvent.SelectRegion(region.id)) },
                        modifier =
                            Modifier
                                .shadow(
                                    FindoraTheme.elevation.medium,
                                    FindoraTheme.shapes.chip,
                                ).testTag("region_${region.id}"),
                    )
                }
            }
            RoundControl(
                icon = if (state.mode == MapMode.Map) FindoraIcons.ListBullets else FindoraIcons.FoldedMap,
                label =
                    stringResource(
                        if (state.mode ==
                            MapMode.Map
                        ) {
                            R.string.map_view_list
                        } else {
                            R.string.map_view_map
                        },
                    ),
                onClick = { onEvent(MapUiEvent.ToggleMode) },
                tag = "mapModeToggle",
            )
        }
        AnimatedVisibility(
            visible = state.mode == MapMode.Map && state.showSearchThisArea,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PrimaryPillButton(
                text = stringResource(R.string.map_search_this_area),
                onClick = { onEvent(MapUiEvent.SearchThisArea) },
                modifier = Modifier.testTag("searchThisArea"),
            )
        }
        if (state.searchArea != null) {
            AreaChip(onClear = { onEvent(MapUiEvent.ClearSearchArea) })
        }
        Banners(state, onEvent, Modifier.padding(horizontal = spacing.screenHorizontal))
    }
}

@Composable
private fun AreaChip(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .shadow(FindoraTheme.elevation.medium, FindoraTheme.shapes.chip)
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.background)
                .clickable(role = Role.Button, onClick = onClear)
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs)
                .testTag("clearArea"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.map_area_active),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        Text(
            text = stringResource(R.string.map_show_whole_region),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.accent,
        )
    }
}

@Composable
private fun Banners(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        if (state.mode == MapMode.Map && state.tilesUnavailable) {
            InlineBanner(
                text = stringResource(R.string.map_offline),
                actionLabel = stringResource(R.string.map_view_list),
                onAction = { onEvent(MapUiEvent.ToggleMode) },
                modifier = Modifier.testTag("mapOffline"),
            )
        }
        state.notice?.let { notice ->
            InlineBanner(
                text =
                    stringResource(
                        when (notice) {
                            MapNotice.LocationDenied -> R.string.map_location_denied
                            MapNotice.LocationUnavailable -> R.string.map_location_unavailable
                        },
                    ),
                actionLabel = stringResource(R.string.map_dismiss),
                onAction = { onEvent(MapUiEvent.DismissNotice) },
                modifier = Modifier.testTag("mapNotice"),
            )
        }
    }
}

@Composable
private fun BottomControls(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FindoraTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.map_attribution),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onBackground,
                modifier =
                    Modifier
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.background.copy(alpha = ATTRIBUTION_ALPHA))
                        .padding(horizontal = spacing.xs, vertical = 2.dp)
                        .testTag("mapAttribution"),
            )
            Box(Modifier.weight(1f))
            if (state.canLocate) {
                RoundControl(
                    icon = FindoraIcons.Locate,
                    label = stringResource(R.string.map_my_location),
                    onClick = onMyLocation,
                    tag = "myLocation",
                )
            }
        }
        if (state.listings.isEmpty() && !state.isLoading) {
            EmptyState(state, onEvent, Modifier.padding(horizontal = spacing.screenHorizontal))
        } else {
            ListingCarousel(
                state = state,
                onSettle = { onEvent(MapUiEvent.SelectCard(it)) },
                onOpen = { onEvent(MapUiEvent.OpenListing(it)) },
            )
        }
    }
}

@Composable
private fun EmptyState(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading || state.listings.isNotEmpty()) return
    val inArea = state.searchArea != null
    StateMessage(
        icon = FindoraIcons.MapPinHouse,
        title = stringResource(if (inArea) R.string.map_empty_area else R.string.map_empty_filtered),
        body = pluralStringResource(R.plurals.map_homes_count, 0, 0),
        actionLabel =
            when {
                inArea -> stringResource(R.string.map_show_whole_region)
                state.isFiltered -> stringResource(R.string.action_clear_filters)
                else -> null
            },
        onAction = { onEvent(if (inArea) MapUiEvent.ClearSearchArea else MapUiEvent.ClearFilter) },
        modifier =
            modifier
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.background)
                .testTag("mapEmpty"),
    )
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(ControlSize)
                .shadow(FindoraTheme.elevation.medium, CircleShape)
                .clip(CircleShape)
                .background(FindoraTheme.colors.background)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(ControlIconSize),
        )
    }
}

private const val TOP_CONTROLS_HEIGHT = 72
private const val ATTRIBUTION_ALPHA = 0.8f
