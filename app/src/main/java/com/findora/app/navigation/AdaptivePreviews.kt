package com.findora.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.owner.DailyCount
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingStats
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.ui.adaptive.ListDetailLayout
import com.findora.app.core.ui.adaptive.LocalAdaptiveLayout
import com.findora.app.core.ui.adaptive.ProvideAdaptiveLayout
import com.findora.app.feature.owner.dashboard.OwnerDashboardScreen
import com.findora.app.feature.owner.dashboard.OwnerDashboardUiState
import com.findora.app.feature.owner.dashboard.OwnerListingItem
import java.time.Instant
import java.time.LocalDate

/** The tab chrome and the list-detail layout as each window class lays them out. */
@Composable
private fun ShellPreview() {
    FindoraTheme {
        ProvideAdaptiveLayout {
            val layout = LocalAdaptiveLayout.current
            MainScaffold(
                useRail = layout.useNavigationRail,
                selected = MainTab.Chat,
                onSelect = {},
                badges = mapOf(MainTab.Chat to 2),
            ) { padding ->
                ListDetailLayout(
                    twoPanes = layout.useTwoPanes,
                    list = { EmptyPane(PaneRoute.Kind.Conversation, padding) },
                    detail = { EmptyPane(PaneRoute.Kind.Listing, PaddingValues()) },
                )
            }
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showSystemUi = true)
@Composable
private fun ShellPhonePreview() = ShellPreview()

@Preview(name = "Foldable", device = Devices.FOLDABLE, showSystemUi = true)
@Composable
private fun ShellFoldablePreview() = ShellPreview()

@Preview(name = "Tablet", device = Devices.TABLET, showSystemUi = true)
@Composable
private fun ShellTabletPreview() = ShellPreview()

@Composable
private fun DashboardPreview() {
    FindoraTheme {
        ProvideAdaptiveLayout {
            OwnerDashboardScreen(
                state =
                    OwnerDashboardUiState(
                        loading = false,
                        items =
                            listOf(
                                OwnerListingItem(
                                    OwnerListing(
                                        id = "own-preview",
                                        ownerId = "preview",
                                        status = ListingStatus.Published,
                                        draft = ListingDraft(title = "Sea view flat in La Marsa"),
                                        createdAt = Instant.EPOCH,
                                        updatedAt = Instant.EPOCH,
                                        publishedAt = Instant.EPOCH,
                                    ),
                                    PreviewStats,
                                ),
                            ),
                        totals = PreviewStats,
                        dailyViews =
                            PreviewViews.mapIndexed {
                                day,
                                views,
                                ->
                                DailyCount(PreviewStart.plusDays(day.toLong()), views)
                            },
                    ),
                onEvent = {},
                onBack = {},
                onCreateListing = {},
                onEditListing = {},
                onOpenListing = {},
            )
        }
    }
}

private val PreviewStats = ListingStats(views = 42, saves = 7, enquiries = 3)
private val PreviewStart: LocalDate = LocalDate.of(2026, 10, 10)
private val PreviewViews = listOf(0, 3, 6, 2, 5, 1, 4, 0, 3, 6, 2, 5, 1, 4)

@Preview(name = "Dashboard phone", device = Devices.PHONE)
@Composable
private fun DashboardPhonePreview() = DashboardPreview()

@Preview(name = "Dashboard tablet", device = Devices.TABLET)
@Composable
private fun DashboardTabletPreview() = DashboardPreview()
