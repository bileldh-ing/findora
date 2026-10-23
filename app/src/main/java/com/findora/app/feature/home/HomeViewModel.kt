package com.findora.app.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.findora.app.core.domain.match.BestMatchRanking
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.model.HomeCategory
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the last network refresh, shown as a banner (cache present) or a full error (cache empty). */
sealed interface RefreshStatus {
    data object Idle : RefreshStatus

    data object Refreshing : RefreshStatus

    data class Failed(
        val cachedCount: Int,
    ) : RefreshStatus
}

/** Never mutated after creation (read-only collections), so Compose can skip Home children on equal state. */
@Immutable
data class HomeUiState(
    val region: RegionPack? = null,
    val regions: List<RegionPack> = emptyList(),
    val userInitials: String? = null,
    val filter: PropertyFilter = PropertyFilter(),
    val favoriteIds: Set<String> = emptySet(),
    val bounds: ListingBounds? = null,
    /** Unread entries of the notifications center: the blue dot on the bell. */
    val unreadNotifications: Int = 0,
    val refresh: RefreshStatus = RefreshStatus.Idle,
    /** Null until known; false shows the "Find my best match" banner, true offers the Best match sort. */
    val hasMatchProfile: Boolean? = null,
    /** Match percentage per listing id (listings missing a must-have have none), for the card badges. */
    val matchScores: Map<String, Int> = emptyMap(),
) {
    val category: HomeCategory get() = HomeCategory.of(filter.type)

    /** True when anything narrows the feed (category, search text or a sheet refinement). */
    val isFiltered: Boolean get() = filter != PropertyFilter()

    /** "Kochi, Kerala": the region's first neighbourhood and its name, as in the reference header. */
    val locationLabel: String
        get() =
            region
                ?.let { pack ->
                    listOfNotNull(pack.localities.firstOrNull()?.name, pack.name).distinct().joinToString(", ")
                }.orEmpty()
}

sealed interface HomeUiEvent {
    data class SelectCategory(
        val category: HomeCategory,
    ) : HomeUiEvent

    data class ToggleFavorite(
        val propertyId: String,
    ) : HomeUiEvent

    data class ApplyFilter(
        val filter: PropertyFilter,
    ) : HomeUiEvent

    data class SelectRegion(
        val regionId: String,
    ) : HomeUiEvent

    data object ClearRefinements : HomeUiEvent

    data object ClearQuery : HomeUiEvent

    /** Back to the default feed: every category, refinement and search text cleared. */
    data object ResetFilter : HomeUiEvent

    data object Refresh : HomeUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val properties: PropertyRepository,
        private val favorites: FavoritesRepository,
        private val filters: HomeFilterRepository,
        private val auth: AuthRepository,
        private val currentRegion: CurrentRegion,
        private val notifications: NotificationRepository,
        observeMatchContext: ObserveMatchContext,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val refreshStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
        private val regionList = MutableStateFlow<List<RegionPack>>(emptyList())

        private val regionId: Flow<String> = currentRegion.region.map { it.id }.distinctUntilChanged()

        /** The user's match context (null without a profile), shared by the feed and the badges. */
        private val matchContext: SharedFlow<MatchContext?> =
            observeMatchContext().shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 1)

        /**
         * The paged feed for the current region and filter; survives configuration changes. With a profile and
         * the Best match sort the region's listings are ranked by match score in memory (a region holds a few
         * dozen homes); every other sort pages straight from Room.
         */
        val listings: Flow<PagingData<Property>> =
            combine(regionId, filters.filter, matchContext.map { it != null }.distinctUntilChanged()) {
                region,
                filter,
                hasProfile,
                ->
                FeedQuery(region, filter, ranked = hasProfile && filter.sort == SortOrder.BestMatch)
            }.distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.ranked) {
                        bestMatches(query.regionId, query.filter)
                    } else {
                        properties.pagedProperties(query.regionId, query.filter)
                    }
                }.cachedIn(viewModelScope)

        private fun bestMatches(
            regionId: String,
            filter: PropertyFilter,
        ): Flow<PagingData<Property>> =
            combine(
                properties.observeProperties(regionId, PropertyFilter(type = filter.type)),
                matchContext.filterNotNull(),
            ) { homes, context -> PagingData.from(BestMatchRanking.rank(homes, filter, context)) }

        private val matchParts: Flow<MatchParts> =
            combine(regionId.flatMapLatest { properties.observeProperties(it) }, matchContext) { homes, context ->
                MatchParts(
                    hasProfile = context != null,
                    scores = context?.let { BestMatchRanking.scores(homes, it) }.orEmpty(),
                )
            }

        private val header =
            combine(
                currentRegion.region,
                auth.observeCurrentUser(),
                userId.flatMapLatest { favorites.observeFavoriteIds(it) },
                userId.flatMapLatest { notifications.observeUnreadCount(it) },
            ) { region, account, favoriteIds, unread -> HeaderParts(region, account?.initials, favoriteIds, unread) }

        private val feed =
            combine(
                filters.filter,
                currentRegion.region.flatMapLatest { properties.observeBounds(it.id) },
                refreshStatus,
                regionList,
            ) { filter, bounds, refresh, regions -> FeedParts(filter, bounds, refresh, regions) }

        val state: StateFlow<HomeUiState> =
            combine(header, feed, matchParts) { parts, feedParts, match ->
                HomeUiState(
                    region = parts.region,
                    regions = feedParts.regions,
                    userInitials = parts.initials,
                    filter = feedParts.filter,
                    favoriteIds = parts.favoriteIds,
                    bounds = feedParts.bounds,
                    unreadNotifications = parts.unread,
                    refresh = feedParts.refresh,
                    hasMatchProfile = match.hasProfile,
                    matchScores = match.scores,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

        init {
            viewModelScope.launch { userId.collect(filters::claim) }
            viewModelScope.launch { regionList.value = currentRegion.all() }
            viewModelScope.launch {
                currentRegion.region
                    .map { it.id }
                    .distinctUntilChanged()
                    .collect { refresh(it, force = false) }
            }
        }

        fun onEvent(event: HomeUiEvent) {
            when (event) {
                is HomeUiEvent.SelectCategory -> filters.update { it.copy(type = event.category.type) }
                is HomeUiEvent.ToggleFavorite ->
                    viewModelScope.launch { favorites.toggle(userId.first(), event.propertyId) }
                is HomeUiEvent.ApplyFilter -> filters.update { event.filter }
                is HomeUiEvent.SelectRegion -> viewModelScope.launch { currentRegion.select(event.regionId) }
                HomeUiEvent.ClearRefinements -> filters.update { it.withoutRefinements() }
                HomeUiEvent.ClearQuery -> filters.update { it.copy(query = "") }
                HomeUiEvent.ResetFilter -> filters.reset()
                HomeUiEvent.Refresh ->
                    viewModelScope.launch { refresh(currentRegion.region.first().id, force = true) }
            }
        }

        private suspend fun refresh(
            regionId: String,
            force: Boolean,
        ) {
            if (refreshStatus.value == RefreshStatus.Refreshing) return
            refreshStatus.update { RefreshStatus.Refreshing }
            val result = properties.refresh(regionId, force)
            refreshStatus.update {
                when (result) {
                    is RefreshResult.Failed -> RefreshStatus.Failed(result.cachedCount)
                    else -> RefreshStatus.Idle
                }
            }
        }

        private data class FeedQuery(
            val regionId: String,
            val filter: PropertyFilter,
            val ranked: Boolean,
        )

        private data class FeedParts(
            val filter: PropertyFilter,
            val bounds: ListingBounds?,
            val refresh: RefreshStatus,
            val regions: List<RegionPack>,
        )

        private data class MatchParts(
            val hasProfile: Boolean,
            val scores: Map<String, Int>,
        )

        private data class HeaderParts(
            val region: RegionPack,
            val initials: String?,
            val favoriteIds: Set<String>,
            val unread: Int,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
