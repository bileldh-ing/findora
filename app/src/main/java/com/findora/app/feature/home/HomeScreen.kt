package com.findora.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.findora.app.R
import com.findora.app.core.designsystem.component.SlidingChipRow
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.HomeCategory
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.InlineBanner
import com.findora.app.core.ui.PropertyCard
import com.findora.app.core.ui.PropertyCardSkeleton
import com.findora.app.core.ui.StateMessage
import kotlinx.coroutines.delay

private const val SKELETON_COUNT = 6
private const val STAGGER_MILLIS = 60L
private const val STAGGERED_ITEMS = 8
private const val ENTRANCE_MILLIS = 380
private val EntranceRise = 24.dp

/** Grid item index of the search pill; once it scrolls away the compact search bar pins to the top. */
private const val SEARCH_ITEM_INDEX = 1

/** Which bottom sheet Home is showing. */
enum class HomeSheet { None, Filters, Regions, Notifications }

@Composable
fun HomeRoute(
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listings = viewModel.listings.collectAsLazyPagingItems()
    HomeScreen(
        state = state,
        listings = listings,
        onEvent = viewModel::onEvent,
        onOpenSearch = onOpenSearch,
        onOpenProfile = onOpenProfile,
        onOpenProperty = onOpenProperty,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * The reference Home: location chip, bell and avatar, the "Discover / Your New House" title, search pill
 * with filter badge, sliding category chips and a two-column paged grid. The title fades and shrinks as the
 * grid scrolls and a compact search bar pins to the top once the pill is gone. Pull to refresh re-fetches
 * the region; cards rise in with a stagger on first load and animate when filters reorder them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    listings: LazyPagingItems<Property>,
    onEvent: (HomeUiEvent) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    var sheet by rememberSaveable { mutableStateOf(HomeSheet.None) }
    val gridState = rememberLazyGridState()
    var headerHeight by remember { mutableIntStateOf(1) }
    val collapse by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (gridState.firstVisibleItemScrollOffset.toFloat() / headerHeight).coerceIn(0f, 1f)
            }
        }
    }
    val showCompactBar by remember { derivedStateOf { gridState.firstVisibleItemIndex > SEARCH_ITEM_INDEX } }
    // Ids whose entrance already played, so scrolling back up never replays it.
    val revealed = remember { HashSet<String>() }
    val feed = feedContent(state, listings)
    val layoutDirection = LocalLayoutDirection.current
    val horizontal = FindoraTheme.spacing.screenHorizontal

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("home"),
    ) {
        PullToRefreshBox(
            isRefreshing = state.refresh == RefreshStatus.Refreshing && listings.itemCount > 0,
            onRefresh = { onEvent(HomeUiEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding =
                    PaddingValues(
                        start = horizontal + contentPadding.calculateStartPadding(layoutDirection),
                        end = horizontal + contentPadding.calculateEndPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding() + FindoraTheme.spacing.md,
                        bottom = contentPadding.calculateBottomPadding() + FindoraTheme.spacing.md,
                    ),
                horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
                modifier = Modifier.fillMaxSize().testTag("homeGrid"),
            ) {
                fullWidth("header") {
                    Column(
                        Modifier.onSizeChanged { headerHeight = it.height.coerceAtLeast(1) },
                    ) {
                        HomeTopBar(
                            locationLabel = state.locationLabel,
                            unreadCount = state.unread.size,
                            userInitials = state.userInitials,
                            onLocationClick = { sheet = HomeSheet.Regions },
                            onBellClick = { sheet = HomeSheet.Notifications },
                            onAvatarClick = onOpenProfile,
                        )
                        Spacer(Modifier.height(FindoraTheme.spacing.md))
                        HomeTitle(collapse = collapse)
                    }
                }
                fullWidth("search") {
                    SearchPill(
                        query = state.filter.query,
                        activeFilters = state.filter.activeCount,
                        onSearchClick = onOpenSearch,
                        onClearQuery = { onEvent(HomeUiEvent.ClearQuery) },
                        onFilterClick = { sheet = HomeSheet.Filters },
                        modifier = Modifier.padding(top = FindoraTheme.spacing.xs),
                    )
                }
                fullWidth("chips") {
                    SlidingChipRow(
                        labels = HomeCategory.entries.map { stringResource(it.labelRes()) },
                        selectedIndex = state.category.ordinal,
                        onSelect = { onEvent(HomeUiEvent.SelectCategory(HomeCategory.entries[it])) },
                        contentPadding = PaddingValues(horizontal = horizontal),
                        modifier = Modifier.bleed(horizontal),
                    )
                }
                if (feed == FeedContent.Offline) {
                    fullWidth("offline") {
                        InlineBanner(
                            text = stringResource(R.string.offline_banner),
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = { onEvent(HomeUiEvent.Refresh) },
                            modifier = Modifier.testTag("offlineBanner"),
                        )
                    }
                }
                fullWidth("section") {
                    SectionHeading(
                        text = stringResource(R.string.home_nearby),
                        modifier = Modifier.padding(top = FindoraTheme.spacing.xs),
                    )
                }
                feedItems(
                    feed = feed,
                    listings = listings,
                    state = state,
                    onEvent = onEvent,
                    onOpenProperty = onOpenProperty,
                    animateEntrance = { id -> !reduceMotion && id !in revealed },
                    onRevealed = { id -> revealed += id },
                )
            }
        }
        AnimatedVisibility(
            visible = showCompactBar,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .background(FindoraTheme.colors.background)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                CompactSearchBar(
                    query = state.filter.query,
                    activeFilters = state.filter.activeCount,
                    onSearchClick = onOpenSearch,
                    onClearQuery = { onEvent(HomeUiEvent.ClearQuery) },
                    onFilterClick = { sheet = HomeSheet.Filters },
                    modifier = Modifier.testTag("compactSearchBar"),
                )
            }
        }
    }

    HomeSheets(
        sheet = sheet,
        state = state,
        onEvent = onEvent,
        onDismiss = { sheet = HomeSheet.None },
    )
}

/** What the grid shows below the header. */
private enum class FeedContent { Loading, Error, Empty, Listings, Offline }

private fun feedContent(
    state: HomeUiState,
    listings: LazyPagingItems<Property>,
): FeedContent {
    val failed = state.refresh as? RefreshStatus.Failed
    val loading = listings.loadState.refresh is LoadState.Loading || state.refresh == RefreshStatus.Refreshing
    return when {
        listings.itemCount > 0 -> if (failed != null) FeedContent.Offline else FeedContent.Listings
        failed != null && failed.cachedCount == 0 -> FeedContent.Error
        loading -> FeedContent.Loading
        else -> FeedContent.Empty
    }
}

private fun LazyGridScope.feedItems(
    feed: FeedContent,
    listings: LazyPagingItems<Property>,
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    onOpenProperty: (String) -> Unit,
    animateEntrance: (String) -> Boolean,
    onRevealed: (String) -> Unit,
) {
    when (feed) {
        FeedContent.Loading ->
            items(SKELETON_COUNT, key = { "skeleton_$it" }) {
                PropertyCardSkeleton(Modifier.testTag("skeleton"))
            }
        FeedContent.Error ->
            fullWidth("error") {
                StateMessage(
                    icon = FindoraIcons.AlertCircle,
                    title = stringResource(R.string.error_title),
                    body = stringResource(R.string.error_body),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { onEvent(HomeUiEvent.Refresh) },
                    modifier = Modifier.testTag("errorState"),
                )
            }
        FeedContent.Empty ->
            fullWidth("empty") {
                StateMessage(
                    icon = FindoraIcons.Search,
                    title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.empty_body),
                    actionLabel = if (state.isFiltered) stringResource(R.string.action_clear_filters) else null,
                    onAction = { onEvent(HomeUiEvent.ResetFilter) },
                    modifier = Modifier.testTag("emptyState"),
                )
            }
        FeedContent.Listings, FeedContent.Offline ->
            items(
                count = listings.itemCount,
                key = listings.itemKey { it.id },
                contentType = listings.itemContentType { "property" },
            ) { index ->
                val property = listings[index]
                if (property == null) {
                    PropertyCardSkeleton(Modifier.animateItem())
                } else {
                    FeedCard(
                        property = property,
                        index = index,
                        isFavorite = property.id in state.favoriteIds,
                        animateEntrance = animateEntrance(property.id),
                        onReveal = { onRevealed(property.id) },
                        onToggleFavorite = { onEvent(HomeUiEvent.ToggleFavorite(property.id)) },
                        onClick = { onOpenProperty(property.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

/** A listing card with a fade-and-rise entrance, staggered by grid position for the first screenful. */
@Composable
private fun FeedCard(
    property: Property,
    index: Int,
    isFavorite: Boolean,
    animateEntrance: Boolean,
    onReveal: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = animateEntrance && index < STAGGERED_ITEMS
    val progress = remember(property.id) { Animatable(if (animate) 0f else 1f) }
    val currentOnReveal by rememberUpdatedState(onReveal)
    LaunchedEffect(property.id) {
        if (progress.value < 1f) {
            delay(index * STAGGER_MILLIS)
            progress.animateTo(1f, tween(ENTRANCE_MILLIS))
        }
        currentOnReveal()
    }
    val rise = with(LocalDensity.current) { EntranceRise.toPx() }
    PropertyCard(
        property = property,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        onClick = onClick,
        modifier =
            modifier.graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * rise
            },
    )
}

/** Lets a full-width row (the chips) scroll edge to edge through the grid's horizontal padding. */
private fun Modifier.bleed(horizontal: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = horizontal.roundToPx() * 2
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extra,
                    maxWidth = constraints.maxWidth + extra,
                ),
            )
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }.fillMaxWidth()

fun HomeCategory.labelRes(): Int =
    when (this) {
        HomeCategory.RentalHouse -> R.string.category_rental_house
        HomeCategory.Apartment -> R.string.category_apartment
        HomeCategory.Houses -> R.string.category_houses
        HomeCategory.Rooms -> R.string.category_rooms
    }
