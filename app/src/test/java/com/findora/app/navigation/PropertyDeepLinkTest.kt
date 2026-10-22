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
class PropertyDeepLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun linksRoundTripAndMalformedOnesAreRejected() {
        assertEquals("findora://property/fd-kl-0001", PropertyDeepLink.uriFor("fd-kl-0001"))
        assertEquals("fd-kl-0001", PropertyDeepLink.parse("findora://property/fd-kl-0001"))
        assertEquals("fd-kl-0001", PropertyDeepLink.parse("findora://property/fd-kl-0001/?utm=share#top"))
        assertNull(PropertyDeepLink.parse("findora://property/"))
        assertNull(PropertyDeepLink.parse("findora://region/kerala"))
        assertNull(PropertyDeepLink.parse("https://findora.app/property/fd-kl-0001"))
        assertNull(PropertyDeepLink.parse("findora://property/../etc"))
    }

    @Test
    fun theGraphRoutesAPropertyLinkToTheDetailDestination() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = FindoraRoute.Main) {
                composable<FindoraRoute.Main> { Text("main") }
                composable<FindoraRoute.PropertyDetail>(deepLinks = propertyDetailDeepLinks) { entry ->
                    Text(entry.toRoute<FindoraRoute.PropertyDetail>().propertyId)
                }
            }
        }
        compose.runOnIdle {
            val link = Intent(Intent.ACTION_VIEW, Uri.parse(PropertyDeepLink.uriFor("fd-tn-0003")))
            assertTrue(navController.handleDeepLink(link))
        }
        compose.runOnIdle {
            val entry = requireNotNull(navController.currentBackStackEntry)
            assertEquals("fd-tn-0003", entry.toRoute<FindoraRoute.PropertyDetail>().propertyId)
            // Back from a link lands on the app, not outside it.
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.route
                    .orEmpty()
                    .contains("Main"),
            )
        }
        compose.onNodeWithText("fd-tn-0003").assertExists()
    }
}
