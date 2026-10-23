package com.findora.app.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.SlidingChipRow
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.PropertyCard
import com.findora.app.core.ui.PropertyCardSkeleton
import com.findora.app.core.ui.StateMessage
import com.findora.app.feature.match.deck.DreamMatchEntry
import com.findora.app.feature.savedsearch.SavedSearchRow
import com.findora.app.feature.savedsearch.SavedSearchesUiEffect
import com.findora.app.feature.savedsearch.SavedSearchesUiEvent
import com.findora.app.feature.savedsearch.SavedSearchesUiState
import com.findora.app.feature.savedsearch.SavedSearchesViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

private const val SKELETON_COUNT = 4
private val BackButton = 48.dp
private val BackIcon = 22.dp

/** The lists of the Saved tab: saved homes, Dream Match matches and saved searches. */
enum class SavedTab { Saved, Matches, Searches }

@Composable
fun SavedRoute(
    onOpenProperty: (String) -> Unit,
    onOpenDreamMatch: () -> Unit,
    onShowSearchResults: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SavedViewModel = hiltViewModel(),
    searchesViewModel: SavedSearchesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searches by searchesViewModel.state.collectAsStateWithLifecycle()
    val currentOnShowResults by rememberUpdatedState(onShowSearchResults)
    LaunchedEffect(searchesViewModel) {
        searchesViewModel.effects.collect { effect ->
            when (effect) {
                SavedSearchesUiEffect.ShowResults -> currentOnShowResults()
            }
        }
    }
    SavedScreen(
        state = state,
        onToggleFavorite = viewModel::toggleFavorite,
        onOpenProperty = onOpenProperty,
        onOpenDreamMatch = onOpenDreamMatch,
        modifier = modifier,
        contentPadding = contentPadding,
        searches = searches,
        onSearchEvent = searchesViewModel::onEvent,
    )
}

/** Dream Match's "See my matches": the Matches list on its own page with a Back button. */
@Composable
fun MatchesRoute(
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    onOpenDreamMatch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchesScreen(
        state = state,
        onBack = onBack,
        onToggleFavorite = viewModel::toggleFavorite,
        onOpenProperty = onOpenProperty,
        onOpenDreamMatch = onOpenDreamMatch,
        modifier = modifier,
    )
}

/**
 * The current user's saved homes in the same two-column grid as Home, with a Saved / Matches switch (Matches
 * are the homes kept in Dream Match) and the way into Dream Match; removing a home animates it out.
 */
@Composable
fun SavedScreen(
    state: SavedUiState,
    onToggleFavorite: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenDreamMatch: (() -> Unit)? = null,
    searches: SavedSearchesUiState = SavedSearchesUiState(isLoading = false),
    onSearchEvent: (SavedSearchesUiEvent) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(SavedTab.Saved.ordinal) }
    val selected = SavedTab.entries[tab]
    SavedGrid(contentPadding = contentPadding, modifier = modifier.testTag("saved")) {
        fullWidth("title") {
            Text(
                text = stringResource(R.string.saved_title),
                style = FindoraTheme.typography.headline,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.padding(bottom = FindoraTheme.spacing.xs).semantics { heading() },
            )
        }
        fullWidth("tabs") {
            SlidingChipRow(
                labels =
                    listOf(
                        stringResource(R.string.saved_tab_saved),
                        stringResource(R.string.saved_tab_matches),
                        stringResource(R.string.saved_tab_searches),
                    ),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.testTag("savedTabs"),
            )
        }
        if (onOpenDreamMatch != null && selected != SavedTab.Searches) {
            fullWidth("dreamMatch") { DreamMatchEntry(onClick = onOpenDreamMatch) }
        }
        when (selected) {
            SavedTab.Saved ->
                homes(
                    state = state,
                    homes = state.homes,
                    empty = EmptyCopy(R.string.saved_empty_title, R.string.saved_empty_body, "savedEmpty"),
                    onToggleFavorite = onToggleFavorite,
                    onOpenProperty = onOpenProperty,
                )
            SavedTab.Matches ->
                homes(
                    state = state,
                    homes = state.matches,
                    empty = EmptyCopy(R.string.matches_empty_title, R.string.matches_empty_body, "matchesEmpty"),
                    onToggleFavorite = onToggleFavorite,
                    onOpenProperty = onOpenProperty,
                )
            SavedTab.Searches -> savedSearches(searches, onSearchEvent)
        }
    }
}

private fun LazyGridScope.savedSearches(
    state: SavedSearchesUiState,
    onEvent: (SavedSearchesUiEvent) -> Unit,
) {
    when {
        state.isLoading -> Unit
        state.items.isEmpty() ->
            fullWidth("empty_searches") {
                StateMessage(
                    icon = FindoraIcons.Bookmark,
                    title = stringResource(R.string.saved_searches_empty_title),
                    body = stringResource(R.string.saved_searches_empty_body),
                    modifier = Modifier.testTag("savedSearchesEmpty"),
                )
            }
        else ->
            state.items.forEach { item ->
                fullWidth("search_${item.search.id}") {
                    SavedSearchRow(item = item, onEvent = onEvent)
                }
            }
    }
}

/** The Matches list alone, opened from the end of the Dream Match deck. */
@Composable
fun MatchesScreen(
    state: SavedUiState,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    onOpenDreamMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SavedGrid(contentPadding = PaddingValues(), modifier = modifier.statusBarsPadding().testTag("matches")) {
        fullWidth("title") {
            val back = stringResource(R.string.back)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(BackButton)
                            .clip(CircleShape)
                            .background(FindoraTheme.colors.surface)
                            .clickable(onClickLabel = back, onClick = onBack)
                            .semantics { contentDescription = back }
                            .testTag("matchesBack"),
                ) {
                    Icon(FindoraIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(BackIcon))
                }
                Text(
                    text = stringResource(R.string.saved_tab_matches),
                    style = FindoraTheme.typography.headline,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.padding(start = FindoraTheme.spacing.sm).semantics { heading() },
                )
            }
        }
        homes(
            state = state,
            homes = state.matches,
            empty = EmptyCopy(R.string.matches_empty_title, R.string.matches_empty_body, "matchesEmpty"),
            onToggleFavorite = onToggleFavorite,
            onOpenProperty = onOpenProperty,
            onEmptyAction = onOpenDreamMatch,
        )
    }
}

private data class EmptyCopy(
    val title: Int,
    val body: Int,
    val tag: String,
)

@Composable
private fun SavedGrid(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
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
        modifier = modifier.fillMaxSize().background(FindoraTheme.colors.background),
        content = content,
    )
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.homes(
    state: SavedUiState,
    homes: List<Property>,
    empty: EmptyCopy,
    onToggleFavorite: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    onEmptyAction: (() -> Unit)? = null,
) {
    when {
        state.isLoading -> items(SKELETON_COUNT, key = { "skeleton_$it" }) { PropertyCardSkeleton() }
        homes.isEmpty() ->
            fullWidth("empty_${empty.tag}") {
                StateMessage(
                    icon = FindoraIcons.Heart,
                    title = stringResource(empty.title),
                    body = stringResource(empty.body),
                    actionLabel = onEmptyAction?.let { stringResource(R.string.home_dream_match_action) },
                    onAction = { onEmptyAction?.invoke() },
                    modifier = Modifier.testTag(empty.tag),
                )
            }
        else ->
            items(homes, key = { it.id }) { home ->
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
