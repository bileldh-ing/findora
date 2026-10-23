package com.findora.app.core.data.export

import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeChatRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSearchHistoryRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JsonUserDataExporterTest {
    private val now = Instant.parse("2026-10-23T08:00:00Z")
    private val auth = FakeAuthRepository()
    private val favorites = FakeFavoritesRepository()
    private val viewings = FakeViewingRepository { now }
    private val chats = FakeChatRepository { now }
    private val history = FakeSearchHistoryRepository()
    private val settings =
        FakeSettingsRepository(AppSettings(currencyOverride = Currency.EUR, areaUnit = AreaUnit.Imperial))
    private val georgie = seedProperties.first { it.title == "Georgie House" }

    private val exporter =
        JsonUserDataExporter(auth, settings, favorites, viewings, chats, FakePropertyRepository(), history) { now }

    @Test
    fun exportsTheSignedInUsersProfileFavouritesViewingsChatsAndSearches() =
        runTest {
            auth.signIn()
            val user = auth.account.id
            favorites.toggle(user, georgie.id)
            viewings.add(Viewing(0, user, georgie.id, now.plusSeconds(86_400), 30, "Bring the lease", now))
            val thread = chats.startConversation(user, georgie)
            chats.sendMessage(user, thread.id, "Is it still available?")
            history.record(user, "2 bhk kochi")

            val root = Json.parseToJsonElement(exporter.export(user)).jsonObject

            assertEquals("findora-user-data/1", root.getValue("format").jsonPrimitive.content)
            assertEquals(now.toString(), root.getValue("exportedAt").jsonPrimitive.content)
            val profile = root.getValue("profile").jsonObject
            assertEquals("ada@findora.app", profile.getValue("email").jsonPrimitive.content)
            assertFalse(profile.getValue("guest").jsonPrimitive.boolean)
            val prefs = root.getValue("preferences").jsonObject
            assertEquals("EUR", prefs.getValue("displayCurrency").jsonPrimitive.content)
            assertEquals("Imperial", prefs.getValue("areaUnit").jsonPrimitive.content)
            val favourite =
                root
                    .getValue("favourites")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(georgie.id, favourite.getValue("propertyId").jsonPrimitive.content)
            val viewing =
                root
                    .getValue("viewings")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(georgie.title, viewing.getValue("propertyTitle").jsonPrimitive.content)
            assertEquals("Bring the lease", viewing.getValue("note").jsonPrimitive.content)
            val conversation =
                root
                    .getValue("conversations")
                    .jsonArray
                    .single()
                    .jsonObject
            val message =
                conversation
                    .getValue("messages")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("you", message.getValue("from").jsonPrimitive.content)
            assertEquals("Is it still available?", message.getValue("body").jsonPrimitive.content)
            assertEquals(
                "2 bhk kochi",
                root
                    .getValue("recentSearches")
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun aGuestExportHoldsOnlyTheGuestsDataAndNoAccountDetails() =
        runTest {
            auth.signIn()
            favorites.toggle(auth.account.id, georgie.id)
            val guest = AuthRepository.GUEST_USER_ID

            val text = exporter.export(guest)
            val root = Json.parseToJsonElement(text).jsonObject

            assertTrue(
                root
                    .getValue("profile")
                    .jsonObject
                    .getValue("guest")
                    .jsonPrimitive.boolean,
            )
            assertFalse(text.contains("ada@findora.app"))
            assertTrue(root.getValue("favourites").jsonArray.isEmpty())
            assertTrue(root.getValue("conversations").jsonArray.isEmpty())
        }
}
