package com.findora.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.findora.app.core.domain.model.HomeCategory
import com.findora.app.core.domain.model.InboxMessage
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.MessageRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

data class HomeUiState(
    val region: RegionPack? = null,
    val regions: List<RegionPack> = emptyList(),
    val userInitials: String? = null,
    val filter: PropertyFilter = PropertyFilter(),
    val favoriteIds: Set<String> = emptySet(),
    val bounds: ListingBounds? = null,
    val unread: List<InboxMessage> = emptyList(),
    val refresh: RefreshStatus = RefreshStatus.Idle,
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

    /** A notification swiped away in the sheet is marked read. */
    data class DismissNotification(
        val messageId: String,
    ) : HomeUiEvent

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
        private val messages: MessageRepository,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val refreshStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
        private val regionList = MutableStateFlow<List<RegionPack>>(emptyList())

        /** The paged feed for the current region and filter; survives configuration changes. */
        val listings: Flow<PagingData<Property>> =
            combine(currentRegion.region.map { it.id }.distinctUntilChanged(), filters.filter) { regionId, filter ->
                regionId to filter
            }.distinctUntilChanged()
                .flatMapLatest { (regionId, filter) -> properties.pagedProperties(regionId, filter) }
                .cachedIn(viewModelScope)

        private val header =
            combine(
                currentRegion.region,
                auth.observeCurrentUser(),
                userId.flatMapLatest { favorites.observeFavoriteIds(it) },
                userId.flatMapLatest { messages.observeUnread(it) },
            ) { region, account, favoriteIds, unread -> HeaderParts(region, account?.initials, favoriteIds, unread) }

        val state: StateFlow<HomeUiState> =
            combine(
                header,
                filters.filter,
                currentRegion.region.flatMapLatest { properties.observeBounds(it.id) },
                refreshStatus,
                regionList,
            ) { parts, filter, bounds, refresh, regions ->
                HomeUiState(
                    region = parts.region,
                    regions = regions,
                    userInitials = parts.initials,
                    filter = filter,
                    favoriteIds = parts.favoriteIds,
                    bounds = bounds,
                    unread = parts.unread,
                    refresh = refresh,
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
                is HomeUiEvent.DismissNotification ->
                    viewModelScope.launch { messages.markRead(userId.first(), event.messageId) }
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

        private data class HeaderParts(
            val region: RegionPack,
            val initials: String?,
            val favoriteIds: Set<String>,
            val unread: List<InboxMessage>,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
