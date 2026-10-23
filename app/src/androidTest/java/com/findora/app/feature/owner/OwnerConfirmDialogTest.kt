package com.findora.app.feature.owner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.owner.dashboard.OwnerDashboardEvent
import com.findora.app.feature.owner.dashboard.OwnerDashboardScreen
import com.findora.app.feature.owner.dashboard.OwnerDashboardUiState
import com.findora.app.feature.owner.dashboard.PendingAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pause / unpublish / delete confirmation in a real window (AlertDialog never idles under Robolectric). */
@RunWith(AndroidJUnit4::class)
class OwnerConfirmDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        kind: PendingAction.Kind,
        events: MutableList<OwnerDashboardEvent>,
    ) {
        compose.setContent {
            FindoraTheme {
                OwnerDashboardScreen(
                    state =
                        OwnerDashboardUiState(
                            loading = false,
                            pending = PendingAction(kind, "own-1", "Sea view flat"),
                        ),
                    onEvent = { events += it },
                    onBack = {},
                    onCreateListing = {},
                    onEditListing = {},
                    onOpenListing = {},
                )
            }
        }
    }

    @Test
    fun deletingAsksFirstAndConfirms() {
        val events = mutableListOf<OwnerDashboardEvent>()
        show(PendingAction.Kind.Delete, events)
        compose.onNodeWithText("Delete “Sea view flat”?").assertIsDisplayed()
        compose.onNodeWithTag("ownerConfirm").performClick()
        assertEquals(listOf<OwnerDashboardEvent>(OwnerDashboardEvent.Confirm), events)
    }

    @Test
    fun pausingCanBeCancelled() {
        val events = mutableListOf<OwnerDashboardEvent>()
        show(PendingAction.Kind.Pause, events)
        compose.onNodeWithText("Pause “Sea view flat”?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<OwnerDashboardEvent>(OwnerDashboardEvent.Dismiss), events)
    }
}
