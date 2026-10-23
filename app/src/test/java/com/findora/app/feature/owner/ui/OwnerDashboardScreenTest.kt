package com.findora.app.feature.owner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.owner.DailyCount
import com.findora.app.core.domain.owner.ListingStats
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.testing.completeDraft
import com.findora.app.feature.owner.dashboard.OwnerDashboardEvent
import com.findora.app.feature.owner.dashboard.OwnerDashboardScreen
import com.findora.app.feature.owner.dashboard.OwnerDashboardUiState
import com.findora.app.feature.owner.dashboard.OwnerListingItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class OwnerDashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<OwnerDashboardEvent>()
    private var created = 0
    private val edited = mutableListOf<String>()

    private fun item(
        id: String,
        status: ListingStatus,
        stats: ListingStats = ListingStats(),
    ) = OwnerListingItem(
        OwnerListing(id, "owner-1", status, completeDraft(), Instant.EPOCH, Instant.EPOCH, null),
        stats,
    )

    private fun show(state: OwnerDashboardUiState) {
        compose.setContent {
            FindoraTheme {
                OwnerDashboardScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    onCreateListing = { created++ },
                    onEditListing = { edited += it },
                    onOpenListing = {},
                )
            }
        }
    }

    @Test
    fun anEmptyDashboardInvitesToListAProperty() {
        show(OwnerDashboardUiState(loading = false))
        compose.onNodeWithTag("ownerEmpty").assertIsDisplayed()
        compose.onNodeWithTag("ownerCreateListing").performClick()
        assertEquals(1, created)
    }

    @Test
    fun listingsShowStatusStatsAndTheActionsTheirStatusAllows() {
        val days = (0 until 14).map { DailyCount(LocalDate.of(2026, 10, 10).plusDays(it.toLong()), it % 3) }
        show(
            OwnerDashboardUiState(
                loading = false,
                items =
                    listOf(
                        item("own-1", ListingStatus.Published, ListingStats(5, 2, 1)),
                        item("own-2", ListingStatus.Paused),
                    ),
                totals = ListingStats(5, 2, 1),
                dailyViews = days,
            ),
        )
        compose.onNodeWithTag("viewsChart").assertExists()
        compose.onNodeWithTag("totalViews", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("ownerStatus_own-1").assertTextEquals("Published")
        compose.onNodeWithTag("ownerStats_own-1").assertTextEquals("5 views · 2 saves · 1 enquiry")
        compose.onNodeWithTag("ownerPause_own-1").performClick()
        compose.onNodeWithTag("ownerEdit_own-1").performClick()
        compose.onNodeWithTag("ownerDashboardList").performScrollToNode(hasTestTag("ownerResume_own-2"))
        compose.onNodeWithTag("ownerResume_own-2").performClick()
        compose.onNodeWithTag("ownerDelete_own-2").performClick()
        compose.onNodeWithTag("ownerPause_own-2").assertDoesNotExist()
        assertEquals(
            listOf(
                OwnerDashboardEvent.RequestPause("own-1"),
                OwnerDashboardEvent.Resume("own-2"),
                OwnerDashboardEvent.RequestDelete("own-2"),
            ),
            events,
        )
        assertEquals(listOf("own-1"), edited)
    }
}
