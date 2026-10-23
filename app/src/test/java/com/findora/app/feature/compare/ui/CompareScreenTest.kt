package com.findora.app.feature.compare.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.compare.CompareMetric
import com.findora.app.core.domain.compare.CompareTable
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.compare.CompareScreen
import com.findora.app.feature.compare.CompareUiEvent
import com.findora.app.feature.compare.CompareUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class CompareScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = seedProperties.first()
    private val cheap =
        home("c1", "Cheap Flat", rent = 10_000, area = 50, rating = 4.0, amenities = listOf(Amenity.Wifi))
    private val big = home("c2", "Big House", rent = 30_000, area = 200, rating = 4.9, amenities = listOf(Amenity.Gym))
    private val events = mutableListOf<CompareUiEvent>()
    private val opened = mutableListOf<String>()

    private fun home(
        id: String,
        title: String,
        rent: Long,
        area: Int,
        rating: Double,
        amenities: List<Amenity>,
    ): Property =
        base.copy(
            id = id,
            title = title,
            rent = Money(rent, base.rent.currency),
            areaSqm = area,
            rating = rating,
            amenities = amenities,
        )

    private fun show(
        state: CompareUiState,
        direction: LayoutDirection = LayoutDirection.Ltr,
        darkTheme: Boolean = false,
    ) {
        compose.setContent {
            FindoraTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    CompareScreen(
                        state = state,
                        onEvent = { events += it },
                        onBack = {},
                        onOpenProperty = { opened += it },
                    )
                }
            }
        }
    }

    private fun loaded(
        vararg homes: Property,
        scores: Map<String, Int>? = null,
    ) = CompareUiState(isLoading = false, table = CompareTable.build(homes.toList(), scores))

    @Test
    fun tableShowsEveryRowAndHighlightsTheBestValues() {
        show(loaded(cheap, big, scores = mapOf("c1" to 72, "c2" to 88)))
        compose.onNodeWithText("Cheap Flat").assertIsDisplayed()
        compose.onNodeWithText("Big House").assertIsDisplayed()
        listOf("Price", "Area", "Price per m²", "Bedrooms", "Bathrooms", "Amenities", "Rating", "Match").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("best_${CompareMetric.Price.name}_0").assertExists() // cheaper
        compose.onNodeWithTag("best_${CompareMetric.Area.name}_1").assertExists() // bigger
        compose.onNodeWithTag("best_${CompareMetric.PricePerSqm.name}_1").assertExists() // 150 vs 200 per m²
        compose.onNodeWithTag("best_${CompareMetric.Rating.name}_1").assertExists()
        compose.onNodeWithTag("best_${CompareMetric.Match.name}_1").assertExists()
        compose.onNodeWithTag("cell_${CompareMetric.Price.name}_1").assertExists()
        // Amenity checklist: one tick per home.
        compose.onNodeWithText("Amenity checklist").performScrollTo().assertIsDisplayed()
        val wifi = compose.onNodeWithTag("amenity_${Amenity.Wifi.id}_0").fetchSemanticsNode()
        assertTrue(wifi.config.toString().contains("available at Cheap Flat"))
    }

    @Test
    fun removeReorderAndOpenSendEvents() {
        show(loaded(cheap, big))
        compose.onNodeWithTag("compareRemove_0").performClick()
        compose.onNodeWithTag("compareLater_0").performClick()
        compose.onNodeWithTag("compareEarlier_1").performClick()
        val column = compose.onNodeWithTag("compareColumn_1").fetchSemanticsNode()
        val actions = column.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move earlier", "Remove"), actions.map { it.label })
        compose.runOnIdle { actions.first().action() }
        assertEquals(
            listOf(
                CompareUiEvent.Remove("c1"),
                CompareUiEvent.Move(0, 1),
                CompareUiEvent.Move(1, 0),
                CompareUiEvent.Move(1, 0),
            ),
            events,
        )
        compose.onNodeWithTag("compareClear").performClick()
        assertEquals(CompareUiEvent.Clear, events.last())
    }

    @Test
    fun longPressDragMovesAColumnAndMirrorsInRightToLeft() {
        show(loaded(cheap, big), direction = LayoutDirection.Rtl, darkTheme = true)
        // In RTL the first column sits on the right: dragging it left moves it later.
        compose.onNodeWithTag("compareColumn_0").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(-width * 1.1f, 0f))
            up()
        }
        compose.waitForIdle()
        assertTrue(events.contains(CompareUiEvent.Move(0, 1)))
    }

    @Test
    fun emptyAndSingleStatesExplainWhatToDo() {
        var state by mutableStateOf(CompareUiState(isLoading = false))
        compose.setContent {
            FindoraTheme {
                CompareScreen(state = state, onEvent = { events += it }, onBack = {}, onOpenProperty = {})
            }
        }
        compose.onNodeWithTag("compareEmpty").assertIsDisplayed()
        compose.onNodeWithText("Nothing to compare yet").assertIsDisplayed()
        state = loaded(cheap)
        compose.onNodeWithTag("compareAddMore").assertIsDisplayed()
    }
}
