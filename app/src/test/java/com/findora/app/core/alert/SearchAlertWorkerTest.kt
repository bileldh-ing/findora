package com.findora.app.core.alert

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.alert.CheckSearchAlerts
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.Session
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.notification.NotificationTarget
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.locale.LocalizedContext
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeLanguageController
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.FakePriceWatchRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeReferenceDataRepository
import com.findora.app.core.testing.FakeSavedSearchRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.core.testing.testRates
import com.findora.app.navigation.PropertyDeepLink
import com.findora.app.navigation.SearchDeepLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SearchAlertWorkerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-10-24T09:00:00Z"))
    private val auth = FakeAuthRepository { clock.now() }
    private val settings = FakeSettingsRepository()
    private val savedSearches = FakeSavedSearchRepository { clock.now() }
    private val prices = FakePriceWatchRepository()
    private val inApp = FakeNotificationRepository { clock.now() }
    private val localized = LocalizedContext(context, FakeLanguageController())
    private val notifier = AndroidSearchAlertNotifier(context)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val houses = PropertyFilter(type = PropertyType.House)
    private val newHouse: Property =
        kerala.first { it.type == PropertyType.House }.let {
            it.copy(id = "${it.id}-n21", title = "Riverside Villa · 3B", createdAt = clock.now())
        }
    private var listings: List<Property> = kerala
    private var favorites = FakeFavoritesRepository()
    private val properties get() = FakePropertyRepository(listings)
    private val userId get() = auth.account.id

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        auth.restoredSession = Session(userId, false, clock.now(), clock.now().plusSeconds(3_600))
    }

    private fun worker(onRefresh: Boolean = false): SearchAlertWorker {
        val repository = properties
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    SearchAlertWorker(
                        appContext,
                        workerParameters,
                        auth,
                        CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined)),
                        repository,
                        CheckSearchAlerts(savedSearches, prices, repository, favorites, clock),
                        inApp,
                        notifier,
                        settings,
                        localized,
                        ObserveDisplayPreferences(settings, FakeReferenceDataRepository()),
                    )
            }
        return TestListenableWorkerBuilder<SearchAlertWorker>(context)
            .setInputData(workDataOf(SearchAlertWorker.KEY_ON_REFRESH to onRefresh))
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun saveHouses(frequency: AlertFrequency = AlertFrequency.Instant): Long =
        savedSearches.create(
            userId,
            NewSavedSearch(
                "Kerala houses",
                "",
                "kerala",
                houses,
                kerala.filter(houses::accepts).map { it.id }.toSet(),
                frequency = frequency,
            ),
        )

    @Test
    fun aNewMatchPostsOneAlertThatOpensTheSearchAndListsItInTheCenter() =
        runTest {
            val id = saveHouses()
            listings = kerala + newHouse
            assertEquals(ListenableWorker.Result.success(), worker(onRefresh = true).doWork())

            val posted = notifications.allNotifications.single()
            assertEquals(AndroidSearchAlertNotifier.CHANNEL_ID, posted.channelId)
            assertEquals(
                "1 new home for “Kerala houses”",
                posted.extras.getString(NotificationCompat.EXTRA_TITLE),
            )
            assertEquals(
                "Newest: Riverside Villa · 3B",
                posted.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
            )
            val tap = shadowOf(posted.contentIntent).savedIntent
            assertEquals(Intent.ACTION_VIEW, tap.action)
            assertEquals(SearchDeepLink.uriFor(id), tap.dataString)
            val channel =
                context.getSystemService(NotificationManager::class.java).getNotificationChannel(
                    AndroidSearchAlertNotifier.CHANNEL_ID,
                )
            assertEquals("Search alerts", channel.name)

            val entry = inApp.observeNotifications(userId).first().single()
            assertEquals(NotificationKind.NewMatches, entry.kind)
            assertEquals("Kerala houses", entry.subject)
            assertEquals(1, entry.count)
            assertEquals(NotificationTarget.SavedSearch(id), entry.target)
        }

    @Test
    fun runningAgainNeverRepeatsAnAlert() =
        runTest {
            saveHouses()
            listings = kerala + newHouse
            worker(onRefresh = true).doWork()
            worker(onRefresh = true).doWork()
            worker(onRefresh = false).doWork()
            assertEquals(1, notifications.allNotifications.size)
            assertEquals(1, inApp.observeNotifications(userId).first().size)
        }

    @Test
    fun turnedOffSearchAlertsPostNothingButTheCenterStillListsThem() =
        runTest {
            settings.setNotificationEnabled(NotificationToggle.SearchAlerts, false)
            saveHouses()
            listings = kerala + newHouse
            worker(onRefresh = true).doWork()
            assertTrue(notifications.allNotifications.isEmpty())
            assertEquals(1, inApp.observeNotifications(userId).first().size)
        }

    @Test
    fun turnedOffInAppNotificationsKeepTheCenterEmpty() =
        runTest {
            settings.setNotificationEnabled(NotificationToggle.InApp, false)
            saveHouses()
            listings = kerala + newHouse
            worker(onRefresh = true).doWork()
            assertEquals(1, notifications.allNotifications.size)
            assertTrue(inApp.observeNotifications(userId).first().isEmpty())
        }

    @Test
    fun aDeniedPermissionPostsNothing() =
        runTest {
            shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            saveHouses()
            listings = kerala + newHouse
            worker(onRefresh = true).doWork()
            assertTrue(notifications.allNotifications.isEmpty())
        }

    @Test
    fun nothingRunsWithoutASignedInUser() =
        runTest {
            saveHouses()
            auth.restoredSession = null
            listings = kerala + newHouse
            assertEquals(ListenableWorker.Result.success(), worker(onRefresh = true).doWork())
            assertTrue(notifications.allNotifications.isEmpty())
            assertTrue(inApp.observeNotifications(userId).first().isEmpty())
        }

    @Test
    fun aRefreshRunLeavesDailySearchesForThePeriodicCheck() =
        runTest {
            saveHouses(AlertFrequency.Daily)
            listings = kerala + newHouse
            worker(onRefresh = true).doWork()
            assertTrue(notifications.allNotifications.isEmpty())
        }

    @Test
    fun aCheaperFavouritePostsAPriceDropThatOpensTheListing() =
        runTest {
            val home = kerala.first()
            favorites = FakeFavoritesRepository(listings).apply { toggle(userId, home.id) }
            worker().doWork()
            assertTrue(notifications.allNotifications.isEmpty())

            val cheaper = home.copy(rent = Money(home.rent.amount - 500, home.rent.currency))
            listings = listOf(cheaper) + kerala.drop(1)
            favorites = FakeFavoritesRepository(listings).apply { toggle(userId, home.id) }
            worker().doWork()
            val posted = notifications.allNotifications.single()
            assertEquals("Price drop: ${home.title}", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
            assertEquals(PropertyDeepLink.uriFor(home.id), shadowOf(posted.contentIntent).savedIntent.dataString)
            val entry = inApp.observeNotifications(userId).first().single()
            assertEquals(NotificationKind.PriceDrop, entry.kind)
            val now = PriceFormatter.format(cheaper.rent, Locale.US)
            val was = PriceFormatter.format(home.rent, Locale.US)
            assertEquals("Now $now a month (was $was)", entry.preview)
        }

    @Test
    fun aPriceDropIsShownInTheCurrencyChosenInSettings() =
        runTest {
            settings.setCurrencyOverride(Currency.USD)
            val home = kerala.first()
            favorites = FakeFavoritesRepository(listings).apply { toggle(userId, home.id) }
            worker().doWork()

            val cheaper = home.copy(rent = Money(home.rent.amount - 900, home.rent.currency))
            listings = listOf(cheaper) + kerala.drop(1)
            favorites = FakeFavoritesRepository(listings).apply { toggle(userId, home.id) }
            worker().doWork()
            val display = DisplayPreferences(Currency.USD, AreaUnit.Metric, testRates)
            val now = PriceFormatter.formatDisplay(display.display(cheaper.rent), Locale.US)
            val was = PriceFormatter.formatDisplay(display.display(home.rent), Locale.US)
            val entry = inApp.observeNotifications(userId).first().single()
            assertEquals("Now $now a month (was $was)", entry.preview)
            assertTrue(entry.preview, entry.preview.startsWith("Now \u2248\u00A0$"))
        }
}
