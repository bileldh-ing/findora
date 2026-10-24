package com.findora.app.store

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.findora.app.R
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.component.FindoraLogoBadge
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.designsystem.theme.SyneFamily
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.compare.CompareTable
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.DeckEntry
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.MatchScoreCalculator
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.search.NaturalLanguageQueryParser
import com.findora.app.core.image.OfflineFallbackInterceptor
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.chat.ChatItem
import com.findora.app.feature.chat.ChatScreen
import com.findora.app.feature.chat.ChatUiState
import com.findora.app.feature.compare.CompareScreen
import com.findora.app.feature.compare.CompareUiState
import com.findora.app.feature.detail.DetailScreen
import com.findora.app.feature.detail.DetailUiState
import com.findora.app.feature.home.HomeScreen
import com.findora.app.feature.home.HomeUiState
import com.findora.app.feature.match.deck.DeckScreen
import com.findora.app.feature.match.deck.DeckUiState
import com.findora.app.feature.search.AskUiState
import com.findora.app.feature.search.SearchMode
import com.findora.app.feature.search.SearchScreen
import com.findora.app.feature.search.SearchUiState
import com.findora.app.feature.settings.SettingsScreen
import com.findora.app.feature.settings.SettingsUiState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Google Play store assets rendered from the real app: eight 1080 x 1920 phone screenshots of shipped screens (a 411 dp wide phone), the
 * 1024 x 500 feature graphic and the 512 x 512 icon (the adaptive launcher icon's own layers). Screens are composed
 * with the same stateless screen composables the app uses, fed with the bundled seed listings, and listing photos
 * come from the app's bundled offline photo pack through its real [OfflineFallbackInterceptor] (remote loads are
 * failed on purpose), so the images never depend on the network.
 *
 * Plain test runs render every asset and check its size and that every photo loaded. With -Pfindora.store=verify
 * (CI's "Screenshot tests" job) each image must match the one committed in fastlane/metadata/android/en-US/images;
 * with -Pfindora.store=record the committed images are rewritten (see docs/STORE_LISTING.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class StoreAssetsTest {
    @get:Rule
    val compose = createComposeRule()

    /** Image requests in flight, by request, with the data they load (for the failure message). */
    private val pending = ConcurrentHashMap<ImageRequest, String>()
    private val failedFallbacks = AtomicInteger()

    private val kerala: RegionPack by lazy { regions.first { it.id == "kerala" } }
    private val regions: List<RegionPack> by lazy {
        runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).regions() }
    }
    private val keralaHomes: List<Property> by lazy { FakePropertyRepository().matching("kerala", PropertyFilter()) }
    private val georgie by lazy { seedProperties.first { it.title == "Georgie House" } }

    /** A plausible quiz answer for Kochi: up to 30,000 INR, two bedrooms, quiet, remote work. */
    private val profile =
        PreferenceProfile(
            budget = BudgetRange(min = null, max = 30_000, currency = Currency.INR),
            minBedrooms = 2,
            lifestyle = setOf(LifestyleTag.Quiet, LifestyleTag.RemoteWorker),
        )
    private val now: Instant = Instant.parse("2026-10-23T09:00:00Z")

    private fun scores(homes: List<Property>): Map<String, Int> =
        homes.associate { it.id to MatchScoreCalculator.score(it, profile, now = now).score }

    @Before
    fun bundledPhotosOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val counter =
            object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    pending[request] = request.data.toString()
                }

                override fun onSuccess(
                    request: ImageRequest,
                    result: SuccessResult,
                ) {
                    pending.remove(request)
                }

                override fun onCancel(request: ImageRequest) {
                    // A card that recomposes (the deck, for example) cancels its first request and starts another.
                    pending.remove(request)
                }

                override fun onError(
                    request: ImageRequest,
                    result: ErrorResult,
                ) {
                    pending.remove(request)
                    failedFallbacks.incrementAndGet()
                }
            }
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(context)
                .crossfade(false)
                .eventListener(counter)
                .components {
                    // The app's interceptor retries a failed load with the listing's bundled photo...
                    add(OfflineFallbackInterceptor())
                    // ...and every remote load fails here, as it would offline.
                    add(
                        Interceptor { chain ->
                            val remote =
                                chain.request.data
                                    .toString()
                                    .startsWith("http")
                            if (remote) {
                                ErrorResult(
                                    null,
                                    chain.request,
                                    IllegalStateException("offline"),
                                )
                            } else {
                                chain.proceed()
                            }
                        },
                    )
                }.build(),
        )
    }

    @Test fun phone1Home() = phone("1_home") { Home() }

    @Test fun phone2Detail() = phone("2_detail") { Detail() }

    @Test fun phone3AskFindora() = phone("3_ask_findora") { Ask() }

    @Test fun phone4DreamMatch() = phone("4_dream_match") { DreamMatch() }

    @Test fun phone5Compare() = phone("5_compare_homes") { Compare() }

    @Test fun phone6Chat() = phone("6_chat") { Chat() }

    @Test fun phone7Settings() = phone("7_settings") { Settings() }

    @Test
    @Config(qualifiers = "+ar-ldrtl")
    fun phone8HomeArabicDark() = phone("8_home_arabic_dark", dark = true) { Home() }

    @Test
    @Config(qualifiers = "w1100dp-h700dp-mdpi")
    fun featureGraphic() =
        capture(
            path = "$IMAGES/featureGraphic.png",
            pixels = 1024 to 500,
        ) { FeatureGraphic() }

    @Test
    @Config(qualifiers = "w600dp-h700dp-mdpi")
    fun icon() =
        capture(
            path = "$IMAGES/icon.png",
            pixels = 512 to 512,
            keepAlpha = true,
        ) { Icon() }

    private fun phone(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) = capture(
        path = "$IMAGES/phoneScreenshots/$name.png",
        pixels = 1080 to 1920,
        dark = dark,
        content = content,
    )

    private fun capture(
        path: String,
        pixels: Pair<Int, Int>,
        dark: Boolean = false,
        keepAlpha: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            FindoraTheme(darkTheme = dark) {
                // The frame is sized in pixels so every asset has Play's exact dimensions whatever the density.
                val size = with(LocalDensity.current) { DpSize(pixels.first.toDp(), pixels.second.toDp()) }
                Box(Modifier.requiredSize(size).clipToBounds().testTag(FRAME)) { content() }
            }
        }
        awaitImages()
        val frame = compose.onNodeWithTag(FRAME).fetchSemanticsNode().size
        assertEquals(pixels, frame.width to frame.height)
        assertEquals("a bundled photo failed to load", 0, failedFallbacks.get())
        // Roborazzi captures the frame (ARGB); StoreImages re-encodes it the way Google Play wants it.
        val raw = File("build/tmp/store-assets", File(path).name)
        compose.onNodeWithTag(FRAME).captureRoboImage(
            raw.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        StoreImages.process(raw = raw, target = File(path), keepAlpha = keepAlpha, mode = StoreImages.mode())
    }

    /** Waits until every image request started by the composition has finished (decoding runs off the main thread). */
    private fun awaitImages() {
        compose.waitForIdle()
        var stableChecks = 0
        val deadline = System.currentTimeMillis() + IMAGE_TIMEOUT_MILLIS
        while (stableChecks < STABLE_CHECKS && System.currentTimeMillis() < deadline) {
            stableChecks = if (pending.isEmpty()) stableChecks + 1 else 0
            Thread.sleep(POLL_MILLIS)
            compose.waitForIdle()
        }
        assertTrue("image requests still running: ${pending.values}", pending.isEmpty())
    }

    @Composable
    private fun Home() {
        val listings =
            remember {
                val loaded =
                    LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
                flowOf(PagingData.from(keralaHomes, sourceLoadStates = loaded))
            }.collectAsLazyPagingItems()
        HomeScreen(
            state =
                HomeUiState(
                    region = kerala,
                    regions = listOf(kerala),
                    userInitials = "AL",
                    favoriteIds = setOf(keralaHomes[1].id),
                    unreadNotifications = 2,
                    hasMatchProfile = true,
                    matchScores = scores(keralaHomes),
                ),
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
                    match = MatchScoreCalculator.score(georgie, profile, now = now),
                ),
            onEvent = {},
            onBack = {},
            onOpenProperty = {},
            onScheduleViewing = {},
        )
    }

    @Composable
    private fun Ask() {
        val query = "2 bedroom apartment in La Marsa under 2000 TND with parking"
        SearchScreen(
            state =
                SearchUiState(
                    query = query,
                    mode = SearchMode.Ask,
                    ask =
                        AskUiState(
                            intent = NaturalLanguageQueryParser.parse(query, regions),
                            regionId = "tunis",
                            regionName = "Tunis",
                            regionCurrency = Currency.TND,
                            switchesRegion = true,
                        ),
                ),
            onEvent = {},
            onBack = {},
            autoFocus = false,
        )
    }

    @Composable
    private fun DreamMatch() {
        val ranked =
            keralaHomes
                .map {
                    DeckEntry(it, scores(listOf(it)).getValue(it.id))
                }.sortedByDescending { it.matchPercent }
        DeckScreen(
            state = DeckUiState(isLoading = false, cards = ranked, index = 2, rankedByMatch = true, savedCount = 1),
            onEvent = {},
            onBack = {},
            onOpenMatches = {},
            onOpenProperty = {},
            reduceMotion = true,
        )
    }

    @Composable
    private fun Compare() {
        val homes = keralaHomes.take(3)
        CompareScreen(
            state = CompareUiState(isLoading = false, table = CompareTable.build(homes, scores(homes))),
            onEvent = {},
            onBack = {},
            onOpenProperty = {},
        )
    }

    @Composable
    private fun Chat() {
        val zone = ZoneId.of("Asia/Kolkata")
        val sent = Instant.parse("2026-10-23T10:30:00Z")

        fun bubble(
            id: String,
            fromUser: Boolean,
            body: String,
            minutes: Long,
        ) = ChatItem.Bubble(
            ChatMessage(id, "c1", fromUser, body, sent.plusSeconds(minutes * SECONDS_PER_MINUTE), DeliveryStatus.Read),
        )
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
                            bubble("m1", fromUser = true, body = "Hi! Is Georgie House still available?", minutes = 0),
                            bubble(
                                "m2",
                                fromUser = false,
                                body = "Yes, it is available now. I can show it to you tomorrow at 10:00 or 15:30.",
                                minutes = 1,
                            ),
                            bubble("m3", fromUser = true, body = "Are pets allowed?", minutes = 2),
                        ),
                    agentTyping = true,
                    draft = "",
                    zone = zone,
                    today = sent.atZone(zone).toLocalDate(),
                ),
            onEvent = {},
            onBack = {},
            onOpenProperty = {},
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

    /** The Welcome hero photo with the logo badge, the FINDORA wordmark in Syne and the app's tagline. */
    @Composable
    private fun FeatureGraphic() {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.welcome_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.78f),
                                0.62f to Color.Black.copy(alpha = 0.35f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FindoraLogoBadge(size = 168.dp)
                    Spacer(Modifier.width(40.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "FINDORA",
                            color = Color.White,
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 72.sp,
                        )
                        Text(
                            text = "Where comfort meets convenience",
                            color = Color.White,
                            style = FindoraTheme.typography.title,
                            fontSize = 28.sp,
                        )
                    }
                }
            }
        }
    }

    /** The launcher icon's background and foreground layers, cropped to the 72 of 108 units a launcher shows. */
    @Composable
    private fun Icon() {
        Box(
            modifier = Modifier.fillMaxSize().background(colorResource(R.color.findora_icon_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.requiredSize(ICON_LAYER_SIZE.dp),
            )
        }
    }

    private companion object {
        const val FRAME = "storeFrame"
        const val IMAGES = "../fastlane/metadata/android/en-US/images"
        const val IMAGE_TIMEOUT_MILLIS = 20_000L
        const val STABLE_CHECKS = 5
        const val POLL_MILLIS = 100L
        const val SECONDS_PER_MINUTE = 60L

        /** 512 px shown out of 108 units: the 108-unit layer is drawn 512 * 108 / 72 = 768 px wide. */
        const val ICON_LAYER_SIZE = 768
    }
}

/** A 411 dp wide phone at 420 dpi (like a Pixel 6): the 1080 x 1920 px frame is 411 x 731 dp, with room to spare. */
private const val PHONE = "w411dp-h900dp-420dpi"
