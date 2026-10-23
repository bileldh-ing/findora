package com.findora.app.core.reminder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.findora.app.core.domain.model.Viewing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class WorkManagerViewingReminderSchedulerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-26T09:00:00Z")
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerViewingReminderScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerViewingReminderScheduler({ workManager }, { now })
    }

    private fun viewing(
        id: Long,
        startsIn: Duration,
    ) = Viewing(id, "ada", "p1", now.plus(startsIn), 30, "", now)

    private fun infos(id: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WorkManagerViewingReminderScheduler.uniqueName(id)).get()

    @Test
    fun schedulesOneUniqueReminderPerViewingAndCancelsIt() {
        assertTrue(scheduler.schedule(viewing(7, Duration.ofHours(5))))
        // Re-scheduling replaces the request instead of adding a second one.
        assertTrue(scheduler.schedule(viewing(7, Duration.ofHours(6))))
        val enqueued = infos(7).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, enqueued.size)
        assertTrue(WorkManagerViewingReminderScheduler.TAG in enqueued.single().tags)

        scheduler.cancel(7)
        assertTrue(infos(7).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun noReminderWhenTheViewingStartsWithinTheHour() {
        assertFalse(scheduler.schedule(viewing(8, Duration.ofMinutes(40))))
        assertTrue(infos(8).isEmpty())
    }

    @Test
    fun theReminderIsDueOneHourBeforeTheStart() {
        val start = now.plus(Duration.ofHours(3))
        assertEquals(Duration.ofHours(2), WorkManagerViewingReminderScheduler.reminderDelay(start, now))
        assertEquals(
            Duration.ZERO,
            WorkManagerViewingReminderScheduler.reminderDelay(now.plus(Duration.ofHours(1)), now),
        )
        assertNull(WorkManagerViewingReminderScheduler.reminderDelay(now.plus(Duration.ofMinutes(59)), now))
    }
}
