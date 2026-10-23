package com.findora.app.feature.locations.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.map.GridClusterer
import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.locations.LocationsScreen
import com.findora.app.feature.locations.MapMode
import com.findora.app.feature.locations.MapNotice
import com.findora.app.feature.locations.MapUiEvent
import com.findora.app.feature.locations.MapUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The Locations screen with the osmdroid map replaced by a fake: carousel, list alternative and controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val regions: List<RegionPack> by lazy {
        runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).regions() }
    }
    private val homes: List<Property> by lazy {
        seedProperties.filter { it.regionId == "kerala" }.sortedBy { it.location.longitude }
    }
    private val events = mutableListOf<MapUiEvent>()
    private var locateTaps = 0

    private fun state(listings: List<Property> = homes) =
        MapUiState(
            isLoading = false,
            region = regions.first { it.id == "kerala" },
            regions = regions,
            listings = listings,
            clusters = GridClusterer.cluster(listings, 12.0, Property::id, Property::location),
            canLocate = true,
        )

    private fun show(
        initial: MapUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): (MapUiState) -> Unit {
        var current by mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                FindoraTheme {
                    LocationsScreen(
                        state = current,
                        onEvent = { events += it },
                        onMyLocation = { locateTaps++ },
                        map = { modifier -> Box(modifier.testTag("fakeMap")) },
                    )
                }
            }
        }
        return { current = it }
    }

    @Test
    fun mapModeShowsTheMapAttributionRegionsAndACarouselCardThatOpensTheListing() {
        show(state())
        compose.onNodeWithTag("fakeMap").assertIsDisplayed()
        compose.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        compose.onNodeWithTag("region_kerala").assertIsSelected()
        compose.onNodeWithTag("region_paris").performClick()
        compose.onNodeWithTag("mapCard_${homes.first().id}").assertIsDisplayed().performClick()
        assertEquals(listOf(MapUiEvent.SelectRegion("paris"), MapUiEvent.OpenListing(homes.first().id)), events)
    }

    @Test
    fun aMarkerSelectionScrollsTheCarouselToItsCardAndHighlightsIt() {
        val update = show(state())
        val target = homes[6]
        compose.onNodeWithTag("mapCard_${target.id}").assertDoesNotExist()
        update(state().copy(selectedId = target.id))
        compose.waitForIdle()
        compose.onNodeWithTag("mapCard_${target.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("mapCard_${homes.first().id}").assertDoesNotExist()
    }

    @Test
    fun listViewIsTheAccessibleAlternativeWithEveryListing() {
        val update = show(state())
        compose.onNodeWithContentDescription("List view").performClick()
        assertEquals(MapUiEvent.ToggleMode, events.single())
        update(state().copy(mode = MapMode.List))
        compose.onNodeWithTag("fakeMap").assertDoesNotExist()
        compose.onNodeWithTag("mapCarousel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Map view").assertIsDisplayed()
        val last = homes.last()
        compose.onNodeWithTag("mapList").performScrollToNode(hasTestTag("mapCard_${last.id}"))
        compose
            .onNodeWithTag("mapCard_${last.id}")
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .performClick()
        assertEquals(MapUiEvent.OpenListing(last.id), events.last())
    }

    @Test
    fun searchThisAreaAppearsAfterPanningAndAnActiveAreaCanBeCleared() {
        val update = show(state())
        compose.onNodeWithText("Search this area").assertDoesNotExist()
        update(state().copy(showSearchThisArea = true))
        compose.onNodeWithText("Search this area").assertIsDisplayed().performClick()
        assertEquals(MapUiEvent.SearchThisArea, events.last())
        val area = GeoBounds(9.9, 76.2, 10.0, 76.3)
        update(state(homes.filter { it.location in area }).copy(searchArea = area))
        compose.onNodeWithTag("clearArea").assertIsDisplayed().performClick()
        assertEquals(MapUiEvent.ClearSearchArea, events.last())
    }

    @Test
    fun offlineTilesShowABannerWhileTheCarouselStillWorks() {
        show(state().copy(tilesUnavailable = true))
        compose.onNodeWithTag("mapOffline").assertIsDisplayed()
        compose.onNodeWithTag("mapCard_${homes.first().id}").assertIsDisplayed()
        compose.onNodeWithText("List view").performClick()
        assertEquals(MapUiEvent.ToggleMode, events.single())
    }

    @Test
    fun myLocationButtonAndItsNoticesFollowTheState() {
        val update = show(state())
        compose.onNodeWithTag("myLocation").performClick()
        assertEquals(1, locateTaps)
        update(state().copy(notice = MapNotice.LocationDenied))
        compose.onNodeWithText("Location permission denied. You can still browse by region.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(MapUiEvent.DismissNotice, events.last())
        update(state().copy(canLocate = false))
        compose.onNodeWithTag("myLocation").assertDoesNotExist()
    }

    @Test
    fun noMatchesOffersToClearTheSharedFilter() {
        show(state(emptyList()).copy(filter = PropertyFilter(type = PropertyType.Room, minBedrooms = 5)))
        compose.onNodeWithText("No homes match your filters here").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        assertEquals(MapUiEvent.ClearFilter, events.single())
    }

    @Test
    fun theCarouselStartsFromTheRightInRightToLeftLayouts() {
        show(state(), LayoutDirection.Rtl)
        val rootWidth =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .size.width
        val first = compose.onNodeWithTag("mapCard_${homes[0].id}").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("mapCard_${homes[1].id}").fetchSemanticsNode().boundsInRoot
        assertTrue("first card hugs the right edge in RTL", first.right > rootWidth * 0.9f)
        assertTrue("the next card peeks in from the left", second.right <= first.left)
    }
}
