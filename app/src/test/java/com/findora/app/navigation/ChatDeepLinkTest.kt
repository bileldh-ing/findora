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
class ChatDeepLinkTest {
    @get:Rule
    val compose = createComposeRule()

    private val id = "4f1c2a9e-7b3d-4c1e-9a0b-2d6f8e5c1a7b"

    @Test
    fun linksRoundTripAndMalformedOnesAreRejected() {
        assertEquals("findora://chat/$id", ChatDeepLink.uriFor(id))
        assertEquals(id, ChatDeepLink.parse("findora://chat/$id"))
        assertEquals(id, ChatDeepLink.parse("findora://chat/$id/?from=notification#latest"))
        assertNull(ChatDeepLink.parse("findora://chat/"))
        assertNull(ChatDeepLink.parse("findora://chat/../etc"))
        assertNull(ChatDeepLink.parse("findora://property/$id"))
        assertNull(ChatDeepLink.parse("https://findora.app/chat/$id"))
        // Property links are not mistaken for chat links either.
        assertNull(PropertyDeepLink.parse("findora://chat/$id"))
    }

    @Test
    fun theGraphRoutesAChatLinkToTheConversation() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = FindoraRoute.Main) {
                composable<FindoraRoute.Main> { Text("main") }
                composable<FindoraRoute.PropertyDetail>(deepLinks = propertyDetailDeepLinks) { Text("detail") }
                composable<FindoraRoute.Chat>(deepLinks = chatDeepLinks) { entry ->
                    Text("chat " + entry.toRoute<FindoraRoute.Chat>().conversationId)
                }
            }
        }
        compose.runOnIdle {
            val link = Intent(Intent.ACTION_VIEW, Uri.parse(ChatDeepLink.uriFor(id)))
            assertTrue(navController.handleDeepLink(link))
        }
        compose.runOnIdle {
            val entry = requireNotNull(navController.currentBackStackEntry)
            assertEquals(id, entry.toRoute<FindoraRoute.Chat>().conversationId)
        }
        compose.onNodeWithText("chat $id").assertExists()
    }
}
