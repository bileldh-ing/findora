package com.findora.app.feature.locations

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.RegionPack

private val RegionIconSize = 24.dp

@Composable
fun LocationsRoute(
    onShowHome: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnShowHome by rememberUpdatedState(onShowHome)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LocationsUiEffect.ShowHome -> currentOnShowHome()
            }
        }
    }
    LocationsScreen(
        state = state,
        onSelectRegion = viewModel::selectRegion,
        onSelectLocality = viewModel::selectLocality,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/** Region packs as cards; tap a region to browse it, or a neighbourhood chip to search it on Home. */
@Composable
fun LocationsScreen(
    state: LocationsUiState,
    onSelectRegion: (String) -> Unit,
    onSelectLocality: (regionId: String, locality: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("locations"),
        contentPadding =
            PaddingValues(
                start = FindoraTheme.spacing.screenHorizontal,
                end = FindoraTheme.spacing.screenHorizontal,
                top = contentPadding.calculateTopPadding() + FindoraTheme.spacing.md,
                bottom = contentPadding.calculateBottomPadding() + FindoraTheme.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
    ) {
        item(key = "title") {
            Column {
                Text(
                    text = stringResource(R.string.locations_title),
                    style = FindoraTheme.typography.headline,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.locations_subtitle),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        items(state.regions, key = { it.id }) { region ->
            RegionCard(
                region = region,
                isCurrent = region.id == state.currentRegionId,
                onSelectRegion = { onSelectRegion(region.id) },
                onSelectLocality = { onSelectLocality(region.id, it) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionCard(
    region: RegionPack,
    isCurrent: Boolean,
    onSelectRegion: () -> Unit,
    onSelectLocality: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md)
                .testTag("regionCard_${region.id}"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FindoraTheme.shapes.chip)
                    .clickable(role = Role.Button, onClick = onSelectRegion)
                    .padding(vertical = FindoraTheme.spacing.xs)
                    .testTag("selectRegion_${region.id}"),
        ) {
            Icon(FindoraIcons.MapPinHouse, null, tint = FindoraTheme.colors.onBackground, modifier = Modifier.size(RegionIconSize))
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Text(
                text = region.name,
                style = FindoraTheme.typography.section,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.locations_current),
                    style = FindoraTheme.typography.label,
                    color = FindoraTheme.colors.onPrimaryAction,
                    modifier =
                        Modifier
                            .clip(FindoraTheme.shapes.chip)
                            .background(FindoraTheme.colors.primaryAction)
                            .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs),
                )
            } else {
                Icon(FindoraIcons.ChevronEnd, null, tint = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.size(RegionIconSize))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        ) {
            region.localities.forEach { locality ->
                CategoryChip(
                    text = locality.name,
                    selected = false,
                    onClick = { onSelectLocality(locality.name) },
                    modifier = Modifier.testTag("locality_${locality.name}"),
                )
            }
        }
    }
}
