package com.findora.app.core.reminder

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
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.seedProperties
import com.findora.app.navigation.PropertyDeepLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ViewingReminderWorkerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-26T09:00:00Z")
    private val viewings = FakeViewingRepository { now }
    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val notifier = AndroidViewingReminderNotifier(context)
    private val inApp = FakeNotificationRepository { now }
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun grantNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun worker(viewingId: Long?): ViewingReminderWorker {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    ViewingReminderWorker(
                        appContext,
                        workerParameters,
                        viewings,
                        FakePropertyRepository(),
                        notifier,
                        inApp,
                        { now },
                        ZoneId.of("Africa/Tunis"),
                    )
            }
        val input = viewingId?.let { workDataOf(ViewingReminderWorker.KEY_VIEWING_ID to it) } ?: workDataOf()
        return TestListenableWorkerBuilder<ViewingReminderWorker>(context)
            .setInputData(input)
            .setWorkerFactory(factory)
            .build()
    }

    private fun bookIn(duration: Duration): Viewing =
        viewings.add(Viewing(0, "ada", georgie.id, now.plus(duration), 30, "", now))

    @Test
    fun postsAReminderThatOpensTheListing() =
        runTest {
            val viewing = bookIn(Duration.ofHours(1))
            assertEquals(ListenableWorker.Result.success(), worker(viewing.id).doWork())

            val posted = notifications.allNotifications.single()
            assertEquals(AndroidViewingReminderNotifier.CHANNEL_ID, posted.channelId)
            assertEquals("Viewing in one hour", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
            assertTrue(
                posted.extras
                    .getCharSequence(NotificationCompat.EXTRA_TEXT)
                    .toString()
                    .startsWith(georgie.title),
            )
            val channel =
                context
                    .getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(AndroidViewingReminderNotifier.CHANNEL_ID)
            assertEquals("Viewing reminders", channel.name)
            val tap = shadowOf(posted.contentIntent).savedIntent
            assertEquals(Intent.ACTION_VIEW, tap.action)
            assertEquals(PropertyDeepLink.uriFor(georgie.id), tap.dataString)
            // The notifications center gets the same reminder, owned by the viewing's user.
            val entry = inApp.observeNotifications("ada").first().single()
            assertEquals(NotificationKind.ViewingReminder, entry.kind)
            assertEquals(georgie.title, entry.subject)
            assertEquals(viewing.startAt, entry.eventAt)
        }

    @Test
    fun aCancelledViewingPostsNothing() =
        runTest {
            val viewing = bookIn(Duration.ofHours(1))
            viewings.cancel("ada", viewing.id)
            assertEquals(ListenableWorker.Result.success(), worker(viewing.id).doWork())
            assertTrue(notifications.allNotifications.isEmpty())
            assertTrue(inApp.observeNotifications("ada").first().isEmpty())
        }

    @Test
    fun aViewingThatAlreadyStartedPostsNothing() =
        runTest {
            val viewing = bookIn(Duration.ofMinutes(-5))
            assertEquals(ListenableWorker.Result.success(), worker(viewing.id).doWork())
            assertTrue(notifications.allNotifications.isEmpty())
        }

    @Test
    fun missingInputFails() =
        runTest {
            assertEquals(ListenableWorker.Result.failure(), worker(null).doWork())
        }

    @Test
    fun aDeniedPermissionPostsNothing() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val posted = notifier.post(ViewingReminder(1, georgie.id, georgie.title, "10:00"))
        assertFalse(posted)
        assertTrue(notifications.allNotifications.isEmpty())
    }
}
