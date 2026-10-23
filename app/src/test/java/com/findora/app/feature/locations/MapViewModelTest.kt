package com.findora.app.feature.locations

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.location.DeviceLocationSource
import com.findora.app.core.domain.map.MapViewport
import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.observeMatchContext
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val properties = FakePropertyRepository()
    private val filters = InMemoryHomeFilterRepository()
    private val settings = FakeSettingsRepository()
    private val auth = FakeAuthRepository()
    private val location = FakeLocationSource()

    private fun viewModel() =
        MapViewModel(
            properties = properties,
            filters = filters,
            currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
            location = location,
            observeMatchContext =
                observeMatchContext(
                    auth = auth,
                    profiles = FakeMatchProfileRepository(),
                    properties = properties,
                    settings = settings,
                    dispatcher = main.dispatcher,
                ),
        )

    private suspend fun ReceiveTurbine<MapUiState>.awaitUntil(predicate: (MapUiState) -> Boolean): MapUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend fun ReceiveTurbine<MapUiState>.awaitLoaded(regionId: String = "kerala"): MapUiState =
        awaitUntil { state ->
            val region = state.region
            !state.isLoading &&
                region?.id == regionId &&
                state.regions.isNotEmpty() &&
                (state.camera as? CameraRequest.Center)?.point == region.center &&
                state.listings.isNotEmpty() &&
                state.listings.all { it.regionId == regionId }
        }

    private fun regionHomes(regionId: String) = seedProperties.filter { it.regionId == regionId }

    private fun viewport(
        bounds: GeoBounds,
        zoom: Double,
    ) = MapViewport(
        bounds = bounds,
        center = GeoPoint((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2),
        zoom = zoom,
    )

    @Test
    fun opensOnTheCurrentRegionWithItsListingsClusteredAndTheCameraOnItsCentre() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val state = awaitLoaded()
                assertEquals(4, state.regions.size)
                assertEquals(regionHomes("kerala").map { it.id }.toSet(), state.listings.map { it.id }.toSet())
                assertEquals(state.listings.size, state.clusters.sumOf { it.size })
                val camera = state.camera as CameraRequest.Center
                assertEquals(state.region!!.center, camera.point)
                assertEquals(state.region!!.defaultZoom, camera.zoom)
                assertTrue(state.canLocate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun switchingRegionRecentresClearsTheSelectionAndAreaAndIsSharedWithHome() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val kerala = awaitLoaded()
                val first = kerala.listings.first().id
                vm.onEvent(MapUiEvent.SelectMarker(first))
                assertEquals(first, awaitUntil { it.selectedId != null }.selectedId)

                vm.onEvent(MapUiEvent.SelectRegion("paris"))
                val paris = awaitLoaded("paris")
                assertEquals("paris", settings.settings.value.regionId)
                assertNull(paris.selectedId)
                assertNull(paris.searchArea)
                assertEquals(regionHomes("paris").size, paris.listings.size)
                val camera = paris.camera as CameraRequest.Center
                assertEquals(paris.region!!.center, camera.point)
                assertTrue(camera.id > (kerala.camera as CameraRequest.Center).id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun tappingAMarkerSelectsWithoutMovingTheCameraWhileSwipingACardPansToIt() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val loaded = awaitLoaded()
                val cameraBefore = loaded.camera
                val tapped = loaded.listings[2]
                vm.onEvent(MapUiEvent.SelectMarker(tapped.id))
                val afterTap = awaitUntil { it.selectedId == tapped.id }
                assertEquals(2, afterTap.selectedIndex)
                assertEquals(cameraBefore, afterTap.camera)

                val swiped = loaded.listings[3]
                vm.onEvent(MapUiEvent.SelectCard(swiped.id))
                val afterSwipe = awaitUntil { it.selectedId == swiped.id }
                val pan = afterSwipe.camera as CameraRequest.Center
                assertEquals(swiped.location, pan.point)
                assertNull("a pan keeps the zoom", pan.zoom)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun tappingAClusterZoomsToFitItsMembers() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                awaitLoaded()
                // Fully zoomed out, the whole region collapses into a few bubbles.
                vm.onEvent(
                    MapUiEvent.CameraMoved(viewport(GeoBounds(0.0, 60.0, 20.0, 90.0), zoom = 4.0), byUser = false),
                )
                val zoomedOut = awaitUntil { state -> state.clusters.any { !it.isSingle } }
                val bubble = zoomedOut.clusters.first { !it.isSingle }
                vm.onEvent(MapUiEvent.SelectCluster(bubble.key))
                val fit = awaitUntil { it.camera is CameraRequest.Fit }.camera as CameraRequest.Fit
                assertEquals(bubble.bounds, fit.bounds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun panningByHandOffersSearchThisAreaWhichFiltersByTheVisibleBounds() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val loaded = awaitLoaded()
                val center = loaded.region!!.center
                val start =
                    GeoBounds(
                        center.latitude - 0.1,
                        center.longitude - 0.1,
                        center.latitude + 0.1,
                        center.longitude + 0.1,
                    )
                vm.onEvent(MapUiEvent.CameraMoved(viewport(start, 12.0), byUser = false))
                // A programmatic move (a carousel pan) never offers the button.
                val shifted = GeoBounds(start.south + 0.05, start.west, start.north + 0.05, start.east)
                vm.onEvent(MapUiEvent.CameraMoved(viewport(shifted, 12.0), byUser = false))
                assertFalse(awaitUntil { it.clusters.isNotEmpty() }.showSearchThisArea)

                // The user drags the map north: the button appears.
                vm.onEvent(MapUiEvent.CameraMoved(viewport(shifted, 12.0), byUser = true))
                awaitUntil { it.showSearchThisArea }

                vm.onEvent(MapUiEvent.SearchThisArea)
                val searched = awaitUntil { it.searchArea != null }
                assertEquals(shifted, searched.searchArea)
                assertFalse(searched.showSearchThisArea)
                val expected = regionHomes("kerala").filter { it.location in shifted }.map { it.id }.toSet()
                assertEquals(expected, searched.listings.map { it.id }.toSet())
                assertTrue(searched.listings.size < loaded.listings.size)

                vm.onEvent(MapUiEvent.ClearSearchArea)
                assertEquals(loaded.listings.size, awaitUntil { it.searchArea == null }.listings.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theSharedHomeFilterNarrowsTheMapAndClearFilterResetsIt() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val loaded = awaitLoaded()
                val first = loaded.listings.first { it.type == PropertyType.House }
                vm.onEvent(MapUiEvent.SelectMarker(first.id))
                awaitUntil { it.selectedId == first.id }

                filters.update { it.copy(type = PropertyType.Apartment, minBedrooms = 2) }
                val filtered = awaitUntil { it.isFiltered }
                assertTrue(filtered.listings.isNotEmpty())
                assertTrue(filtered.listings.all { it.type == PropertyType.Apartment && it.bedrooms >= 2 })
                assertEquals(filtered.listings.size, filtered.clusters.sumOf { it.size })
                assertNull("a filtered-out selection is dropped", filtered.selectedId)

                vm.onEvent(MapUiEvent.ClearFilter)
                assertEquals(loaded.listings.size, awaitUntil { !it.isFiltered }.listings.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun myLocationCentresOnTheLastFixOrExplainsWhyItCannot() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                awaitLoaded()
                vm.onEvent(MapUiEvent.LocationPermissionDenied)
                assertEquals(MapNotice.LocationDenied, awaitUntil { it.notice != null }.notice)
                vm.onEvent(MapUiEvent.DismissNotice)
                awaitUntil { it.notice == null }

                location.fix = null
                vm.onEvent(MapUiEvent.LocateMe)
                assertEquals(MapNotice.LocationUnavailable, awaitUntil { it.notice != null }.notice)

                val here = GeoPoint(9.97, 76.28)
                location.fix = here
                vm.onEvent(MapUiEvent.LocateMe)
                val located = awaitUntil { it.myLocation != null }
                assertEquals(here, located.myLocation)
                assertNull(located.notice)
                assertEquals(
                    CameraRequest.Center(located.camera!!.id, here, MapViewModel.MY_LOCATION_ZOOM),
                    located.camera,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun withoutALocationProviderTheButtonIsHidden() =
        runTest(main.dispatcher) {
            location.isSupported = false
            val vm = viewModel()
            vm.state.test {
                assertFalse(awaitLoaded().canLocate)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun listToggleOfflineTilesAndOpeningAListing() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val loaded = awaitLoaded()
                vm.onEvent(MapUiEvent.ToggleMode)
                assertEquals(MapMode.List, awaitUntil { it.mode == MapMode.List }.mode)
                vm.onEvent(MapUiEvent.TilesStatus(available = false))
                val offline = awaitUntil { it.tilesUnavailable }
                // Offline only blanks the tiles: the listings still come from the local cache.
                assertEquals(loaded.listings.size, offline.listings.size)
                vm.onEvent(MapUiEvent.TilesStatus(available = true))
                awaitUntil { !it.tilesUnavailable }
                vm.onEvent(MapUiEvent.ToggleMode)
                awaitUntil { it.mode == MapMode.Map }
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test {
                val id = seedProperties.first().id
                vm.onEvent(MapUiEvent.OpenListing(id))
                assertEquals(MapUiEffect.OpenProperty(id), awaitItem())
            }
        }

    private class FakeLocationSource : DeviceLocationSource {
        override var isSupported: Boolean = true
        var fix: GeoPoint? = null

        override suspend fun lastKnownLocation(): GeoPoint? = fix
    }
}
