package com.findora.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.findora.app.feature.savedsearch.SavedSearchLinkViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchDeepLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun linksRoundTripAndMalformedOnesAreRejected() {
        assertEquals("findora://search/42", SearchDeepLink.uriFor(42))
        assertEquals(42L, SearchDeepLink.parse("findora://search/42"))
        assertEquals(42L, SearchDeepLink.parse("findora://search/42/?from=notification"))
        assertNull(SearchDeepLink.parse("findora://search/"))
        assertNull(SearchDeepLink.parse("findora://search/0"))
        assertNull(SearchDeepLink.parse("findora://search/-3"))
        assertNull(SearchDeepLink.parse("findora://search/abc"))
        assertNull(SearchDeepLink.parse("findora://property/42"))
        assertNull(PropertyDeepLink.parse("findora://search/42"))
        assertNull(ChatDeepLink.parse("findora://search/42"))
    }

    @Test
    fun theGraphRoutesASearchLinkToTheSavedSearchResults() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = FindoraRoute.Main) {
                composable<FindoraRoute.Main> { Text("main") }
                composable<FindoraRoute.PropertyDetail>(deepLinks = propertyDetailDeepLinks) { Text("detail") }
                composable<FindoraRoute.SavedSearchResults>(deepLinks = savedSearchDeepLinks) { entry ->
                    val handleId = entry.savedStateHandle.get<Long>(SavedSearchLinkViewModel.SEARCH_ID_KEY)
                    Text("search ${entry.toRoute<FindoraRoute.SavedSearchResults>().searchId} $handleId")
                }
            }
        }
        compose.runOnIdle {
            assertTrue(navController.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(SearchDeepLink.uriFor(7)))))
        }
        compose.runOnIdle {
            val entry = requireNotNull(navController.currentBackStackEntry)
            assertEquals(7L, entry.toRoute<FindoraRoute.SavedSearchResults>().searchId)
        }
        // The view model reads the id under the same key the graph stores it.
        compose.onNodeWithText("search 7 7").assertExists()
    }
}
