package com.findora.app.screenshot

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.auth.welcome.WelcomeScreen
import com.findora.app.feature.auth.welcome.WelcomeUiState
import com.findora.app.feature.chat.ChatItem
import com.findora.app.feature.chat.ChatScreen
import com.findora.app.feature.chat.ChatUiState
import com.findora.app.feature.detail.DetailScreen
import com.findora.app.feature.detail.DetailUiState
import com.findora.app.feature.home.HomeScreen
import com.findora.app.feature.home.HomeUiState
import com.findora.app.feature.settings.SettingsScreen
import com.findora.app.feature.settings.SettingsUiState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Golden screenshots (Roborazzi, rendered on the JVM by Robolectric's native graphics) of the key screens in
 * light/dark and English LTR / Arabic RTL. Plain test runs only render them; CI's "Screenshot tests" job compares
 * them with the committed goldens in app/src/test/screenshots (-Proborazzi.test.verify=true) and fails on any
 * difference. Re-record after an intended UI change with -Proborazzi.test.record=true (see docs/TESTING.md).
 *
 * Listing photos are remote, so image requests fail fast here and every card shows its placeholder: the goldens
 * never depend on the network.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class KeyScreensScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val kerala: RegionPack by lazy {
        runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).region("kerala")!! }
    }

    @Before
    fun offlineImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(context)
                .components {
                    add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("offline")) })
                }.build(),
        )
    }

    @Test fun welcome_light_ltr() = capture("welcome_light_ltr", dark = false) { Welcome() }

    @Test fun welcome_dark_ltr() = capture("welcome_dark_ltr", dark = true) { Welcome() }

    @Test
    @Config(qualifiers = RTL)
    fun welcome_light_rtl() = capture("welcome_light_rtl", dark = false) { Welcome() }

    @Test
    @Config(qualifiers = RTL)
    fun welcome_dark_rtl() = capture("welcome_dark_rtl", dark = true) { Welcome() }

    @Test fun home_light_ltr() = capture("home_light_ltr", dark = false) { Home() }

    @Test fun home_dark_ltr() = capture("home_dark_ltr", dark = true) { Home() }

    @Test
    @Config(qualifiers = RTL)
    fun home_light_rtl() = capture("home_light_rtl", dark = false) { Home() }

    @Test
    @Config(qualifiers = RTL)
    fun home_dark_rtl() = capture("home_dark_rtl", dark = true) { Home() }

    @Test fun detail_light_ltr() = capture("detail_light_ltr", dark = false) { Detail() }

    @Test fun detail_dark_ltr() = capture("detail_dark_ltr", dark = true) { Detail() }

    @Test
    @Config(qualifiers = RTL)
    fun detail_light_rtl() = capture("detail_light_rtl", dark = false) { Detail() }

    @Test
    @Config(qualifiers = RTL)
    fun detail_dark_rtl() = capture("detail_dark_rtl", dark = true) { Detail() }

    @Test fun settings_light_ltr() = capture("settings_light_ltr", dark = false) { Settings() }

    @Test fun settings_dark_ltr() = capture("settings_dark_ltr", dark = true) { Settings() }

    @Test
    @Config(qualifiers = RTL)
    fun settings_light_rtl() = capture("settings_light_rtl", dark = false) { Settings() }

    @Test
    @Config(qualifiers = RTL)
    fun settings_dark_rtl() = capture("settings_dark_rtl", dark = true) { Settings() }

    @Test fun chat_light_ltr() = capture("chat_light_ltr", dark = false) { Chat() }

    @Test fun chat_dark_ltr() = capture("chat_dark_ltr", dark = true) { Chat() }

    @Test
    @Config(qualifiers = RTL)
    fun chat_light_rtl() = capture("chat_light_rtl", dark = false) { Chat() }

    @Test
    @Config(qualifiers = RTL)
    fun chat_dark_rtl() = capture("chat_dark_rtl", dark = true) { Chat() }

    private fun capture(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { FindoraTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Welcome() {
        WelcomeScreen(
            state = WelcomeUiState(),
            onLogin = {},
            onSignUp = {},
            onTryDemo = {},
            onContinueAsGuest = {},
            reduceMotion = true,
        )
    }

    @Composable
    private fun Home() {
        val listings =
            remember {
                val homes = FakePropertyRepository().matching("kerala", PropertyFilter())
                val loaded =
                    LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
                flowOf(PagingData.from(homes, sourceLoadStates = loaded))
            }.collectAsLazyPagingItems()
        HomeScreen(
            state = HomeUiState(region = kerala, regions = listOf(kerala), userInitials = "AL"),
            listings = listings,
            onEvent = {},
            onOpenSearch = {},
            onOpenProfile = {},
            onOpenProperty = {},
            onOpenMatchQuiz = {},
            reduceMotion = true,
            onOpenNotifications = {},
        )
    }

    @Composable
    private fun Detail() {
        val similar = seedProperties.filter { it.type == georgie.type && it.id != georgie.id }.take(3)
        DetailScreen(
            state =
                DetailUiState.Content(
                    georgie,
                    isFavorite = true,
                    similar = similar,
                    similarFavoriteIds = emptySet(),
                ),
            onEvent = {},
            onBack = {},
            onOpenProperty = {},
            onScheduleViewing = {},
        )
    }

    @Composable
    private fun Settings() {
        SettingsScreen(
            state = SettingsUiState(ratesAsOf = LocalDate.of(2026, 10, 23)),
            onEvent = {},
            onCreateAccount = {},
            onOpenViewings = {},
            onOpenMatchPreferences = {},
            onOpenAbout = {},
            onOpenSystemNotificationSettings = {},
            onOpenDesignSystem = null,
        )
    }

    @Composable
    private fun Chat() {
        val zone = ZoneId.of("UTC")
        val sent = Instant.ofEpochSecond(CHAT_EPOCH_SECONDS)

        fun bubble(
            id: String,
            fromUser: Boolean,
            body: String,
        ) = ChatItem.Bubble(ChatMessage(id, "c1", fromUser, body, sent, DeliveryStatus.Read))
        ChatScreen(
            state =
                ChatUiState.Content(
                    conversationId = "c1",
                    agent = ChatAgent(georgie.agent.id, georgie.agent.name, georgie.agent.avatar),
                    propertyId = georgie.id,
                    propertyTitle = georgie.title,
                    property = georgie,
                    items =
                        listOf(
                            ChatItem.DaySeparator(sent.atZone(zone).toLocalDate()),
                            bubble("m1", fromUser = true, body = "Hi! Is Georgie House still available?"),
                            bubble("m2", fromUser = false, body = "Yes, it is. Would you like to book a viewing?"),
                        ),
                    agentTyping = false,
                    draft = "",
                    zone = zone,
                    today = sent.atZone(zone).toLocalDate(),
                ),
            onEvent = {},
            onBack = {},
            onOpenProperty = {},
        )
    }

    private companion object {
        /** Arabic strings with right-to-left layout. */
        const val RTL = "+ar-ldrtl"

        /** A fixed send time so the day separator and timestamps never change between runs. */
        const val CHAT_EPOCH_SECONDS = 1_792_000_000L
    }
}
