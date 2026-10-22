package com.findora.app.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.PropertyCard
import com.findora.app.core.ui.PropertyCardSkeleton
import com.findora.app.core.ui.StateMessage

private const val SKELETON_COUNT = 4

@Composable
fun SavedRoute(
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavedScreen(
        state = state,
        onToggleFavorite = viewModel::toggleFavorite,
        onOpenProperty = onOpenProperty,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/** The current user's saved homes in the same two-column grid as Home; removing one animates it out. */
@Composable
fun SavedScreen(
    state: SavedUiState,
    onToggleFavorite: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontal = FindoraTheme.spacing.screenHorizontal
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding =
            PaddingValues(
                start = horizontal + contentPadding.calculateStartPadding(layoutDirection),
                end = horizontal + contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + FindoraTheme.spacing.md,
                bottom = contentPadding.calculateBottomPadding() + FindoraTheme.spacing.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("saved"),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.saved_title),
                style = FindoraTheme.typography.headline,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.padding(bottom = FindoraTheme.spacing.xs).semantics { heading() },
            )
        }
        when {
            state.isLoading -> items(SKELETON_COUNT, key = { "skeleton_$it" }) { PropertyCardSkeleton() }
            state.homes.isEmpty() ->
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        icon = FindoraIcons.Heart,
                        title = stringResource(R.string.saved_empty_title),
                        body = stringResource(R.string.saved_empty_body),
                        modifier = Modifier.testTag("savedEmpty"),
                    )
                }
            else ->
                items(state.homes, key = { it.id }) { home ->
                    PropertyCard(
                        property = home,
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(home.id) },
                        onClick = { onOpenProperty(home.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
        }
    }
}
