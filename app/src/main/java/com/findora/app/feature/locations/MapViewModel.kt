package com.findora.app.feature.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.location.DeviceLocationSource
import com.findora.app.core.domain.map.GridClusterer
import com.findora.app.core.domain.map.MapAreas
import com.findora.app.core.domain.map.MapCluster
import com.findora.app.core.domain.map.MapViewport
import com.findora.app.core.domain.match.BestMatchRanking
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Map or the accessible list of the same listings. */
enum class MapMode { Map, List }

/** A one-off camera instruction for the map; a new [id] means "apply me", so equal moves can repeat. */
sealed interface CameraRequest {
    val id: Long

    data class Center(
        override val id: Long,
        val point: GeoPoint,
        /** Null keeps the current zoom (a pan to the selected listing). */
        val zoom: Double?,
    ) : CameraRequest

    data class Fit(
        override val id: Long,
        val bounds: GeoBounds,
    ) : CameraRequest
}

/** Short notices shown over the map. */
enum class MapNotice { LocationDenied, LocationUnavailable }

data class MapUiState(
    val isLoading: Boolean = true,
    val region: RegionPack? = null,
    val regions: List<RegionPack> = emptyList(),
    val filter: PropertyFilter = PropertyFilter(),
    /** Listings accepted by the shared filter (and the searched area), west to east: the carousel and list. */
    val listings: List<Property> = emptyList(),
    /** [listings] grouped for the current zoom: what the map draws. */
    val clusters: List<MapCluster<Property>> = emptyList(),
    val selectedId: String? = null,
    /** The box chosen with "Search this area"; null shows the whole region. */
    val searchArea: GeoBounds? = null,
    val showSearchThisArea: Boolean = false,
    val mode: MapMode = MapMode.Map,
    val camera: CameraRequest? = null,
    /** True once map tiles failed to load (offline): a banner explains the blank map; cards still work. */
    val tilesUnavailable: Boolean = false,
    val canLocate: Boolean = false,
    val myLocation: GeoPoint? = null,
    val notice: MapNotice? = null,
    /** Match percentage per listing id when the user has a preference profile. */
    val matchScores: Map<String, Int> = emptyMap(),
) {
    val selectedIndex: Int get() = listings.indexOfFirst { it.id == selectedId }

    val isFiltered: Boolean get() = filter != PropertyFilter()
}

sealed interface MapUiEvent {
    data class SelectRegion(
        val regionId: String,
    ) : MapUiEvent

    /** A price pill was tapped: highlight it and scroll the carousel (the map stays where it is). */
    data class SelectMarker(
        val propertyId: String,
    ) : MapUiEvent

    /** The carousel settled on a card: highlight its marker and pan the map to it. */
    data class SelectCard(
        val propertyId: String,
    ) : MapUiEvent

    /** A cluster bubble was tapped: zoom to fit its members. */
    data class SelectCluster(
        val key: String,
    ) : MapUiEvent

    data class CameraMoved(
        val viewport: MapViewport,
        val byUser: Boolean,
    ) : MapUiEvent

    data object SearchThisArea : MapUiEvent

    data object ClearSearchArea : MapUiEvent

    data object ClearFilter : MapUiEvent

    data object ToggleMode : MapUiEvent

    /** Location permission is granted: centre on the last known fix. */
    data object LocateMe : MapUiEvent

    data object LocationPermissionDenied : MapUiEvent

    data object DismissNotice : MapUiEvent

    data class TilesStatus(
        val available: Boolean,
    ) : MapUiEvent

    data class OpenListing(
        val propertyId: String,
    ) : MapUiEvent
}

sealed interface MapUiEffect {
    data class OpenProperty(
        val propertyId: String,
    ) : MapUiEffect
}

/**
 * The Locations tab: the current region's cached listings (Room, so it works offline) narrowed by the filter
 * shared with Home, clustered for the map's zoom, with a selection kept in sync between the price pills and
 * the card carousel, and an optional "Search this area" box.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        properties: PropertyRepository,
        private val filters: HomeFilterRepository,
        private val currentRegion: CurrentRegion,
        private val location: DeviceLocationSource,
        observeMatchContext: ObserveMatchContext,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState(canLocate = location.isSupported))
        private val regionList = MutableStateFlow<List<RegionPack>>(emptyList())
        private var nextCameraId = 1L

        private val regionListings: Flow<RegionListings> =
            currentRegion.region.distinctUntilChanged().flatMapLatest { region ->
                properties.observeProperties(region.id).map { RegionListings(region, it) }
            }

        private val scores: Flow<Map<String, Int>> =
            combine(regionListings, observeMatchContext()) { listings, context ->
                context?.let { BestMatchRanking.scores(listings.homes, it) }.orEmpty()
            }

        private val effectChannel = Channel<MapUiEffect>(Channel.BUFFERED)
        val effects: Flow<MapUiEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<MapUiState> =
            combine(regionListings, filters.filter, local, regionList, scores) { data, filter, ui, regions, match ->
                // Listings outside the region pack (a stale area) never show; the area applies within it.
                val area = ui.searchArea.takeIf { ui.areaRegionId == data.region.id }
                val visible = MapAreas.visibleListings(data.homes, filter, area)
                val zoom = ui.viewport?.zoom ?: data.region.defaultZoom
                MapUiState(
                    isLoading = false,
                    region = data.region,
                    regions = regions,
                    filter = filter,
                    listings = visible,
                    clusters = GridClusterer.cluster(visible, zoom, Property::id, Property::location),
                    selectedId = ui.selectedId?.takeIf { id -> visible.any { it.id == id } },
                    searchArea = area,
                    showSearchThisArea = ui.showSearchThisArea,
                    mode = ui.mode,
                    camera = ui.camera,
                    tilesUnavailable = ui.tilesUnavailable,
                    canLocate = ui.canLocate,
                    myLocation = ui.myLocation,
                    notice = ui.notice,
                    matchScores = match,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MapUiState())

        init {
            viewModelScope.launch { regionList.value = currentRegion.all() }
            viewModelScope.launch {
                currentRegion.region
                    .map { it.id to it }
                    .distinctUntilChanged { old, new -> old.first == new.first }
                    .collect { (_, region) -> recenter(region) }
            }
        }

        fun onEvent(event: MapUiEvent) {
            when (event) {
                is MapUiEvent.SelectRegion -> viewModelScope.launch { currentRegion.select(event.regionId) }
                is MapUiEvent.SelectMarker -> local.update { it.copy(selectedId = event.propertyId) }
                is MapUiEvent.SelectCard -> selectCard(event.propertyId)
                is MapUiEvent.SelectCluster -> selectCluster(event.key)
                is MapUiEvent.CameraMoved -> cameraMoved(event.viewport, event.byUser)
                MapUiEvent.SearchThisArea -> searchThisArea()
                MapUiEvent.ClearSearchArea ->
                    local.update { it.copy(searchArea = null, areaRegionId = null, baseline = it.viewport) }
                else -> onControlEvent(event)
            }
        }

        /** Events from the controls around the map: mode, filter, location, notices and opening a listing. */
        private fun onControlEvent(event: MapUiEvent) {
            when (event) {
                MapUiEvent.ClearFilter -> filters.reset()
                MapUiEvent.ToggleMode ->
                    local.update { it.copy(mode = if (it.mode == MapMode.Map) MapMode.List else MapMode.Map) }
                MapUiEvent.LocateMe -> locateMe()
                MapUiEvent.LocationPermissionDenied -> local.update { it.copy(notice = MapNotice.LocationDenied) }
                MapUiEvent.DismissNotice -> local.update { it.copy(notice = null) }
                is MapUiEvent.TilesStatus -> local.update { it.copy(tilesUnavailable = !event.available) }
                is MapUiEvent.OpenListing ->
                    viewModelScope.launch { effectChannel.send(MapUiEffect.OpenProperty(event.propertyId)) }
                else -> Unit
            }
        }

        private fun recenter(region: RegionPack) =
            local.update {
                it.copy(
                    selectedId = null,
                    searchArea = null,
                    areaRegionId = null,
                    showSearchThisArea = false,
                    viewport = null,
                    baseline = null,
                    camera = CameraRequest.Center(nextCameraId++, region.center, region.defaultZoom),
                )
            }

        private fun selectCard(propertyId: String) {
            val home = state.value.listings.firstOrNull { it.id == propertyId } ?: return
            local.update {
                if (it.selectedId == propertyId) {
                    it
                } else {
                    it.copy(selectedId = propertyId, camera = CameraRequest.Center(nextCameraId++, home.location, null))
                }
            }
        }

        private fun selectCluster(key: String) {
            val cluster = state.value.clusters.firstOrNull { it.key == key } ?: return
            if (cluster.isSingle) {
                local.update { it.copy(selectedId = cluster.key) }
            } else {
                local.update { it.copy(camera = CameraRequest.Fit(nextCameraId++, cluster.bounds)) }
            }
        }

        private fun cameraMoved(
            viewport: MapViewport,
            byUser: Boolean,
        ) = local.update {
            val baseline = it.baseline
            when {
                // The first frame after a recentre becomes the reference the user pans away from.
                baseline == null -> it.copy(viewport = viewport, baseline = viewport)
                byUser && viewport.movedFrom(baseline) -> it.copy(viewport = viewport, showSearchThisArea = true)
                else -> it.copy(viewport = viewport)
            }
        }

        private fun searchThisArea() {
            val regionId = state.value.region?.id ?: return
            local.update {
                val viewport = it.viewport ?: return@update it
                it.copy(
                    searchArea = viewport.bounds,
                    areaRegionId = regionId,
                    baseline = viewport,
                    showSearchThisArea = false,
                )
            }
        }

        private fun locateMe() {
            viewModelScope.launch {
                val fix = location.lastKnownLocation()
                local.update {
                    if (fix == null) {
                        it.copy(notice = MapNotice.LocationUnavailable)
                    } else {
                        it.copy(
                            myLocation = fix,
                            notice = null,
                            camera = CameraRequest.Center(nextCameraId++, fix, MY_LOCATION_ZOOM),
                        )
                    }
                }
            }
        }

        private data class RegionListings(
            val region: RegionPack,
            val homes: List<Property>,
        )

        private data class LocalState(
            val selectedId: String? = null,
            val searchArea: GeoBounds? = null,
            val areaRegionId: String? = null,
            val showSearchThisArea: Boolean = false,
            val viewport: MapViewport? = null,
            val baseline: MapViewport? = null,
            val mode: MapMode = MapMode.Map,
            val camera: CameraRequest? = null,
            val tilesUnavailable: Boolean = false,
            val canLocate: Boolean = false,
            val myLocation: GeoPoint? = null,
            val notice: MapNotice? = null,
        )

        companion object {
            const val MY_LOCATION_ZOOM = 14.0
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
