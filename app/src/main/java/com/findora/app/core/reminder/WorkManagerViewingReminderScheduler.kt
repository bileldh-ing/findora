package com.findora.app.core.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** One unique OneTimeWorkRequest per viewing ("viewing-reminder-<id>"), due [LEAD] before it starts. */
@Singleton
class WorkManagerViewingReminderScheduler
    @Inject
    constructor(
        private val workManager: Provider<WorkManager>,
        private val clock: AppClock,
    ) : ViewingReminderScheduler {
        override fun schedule(viewing: Viewing): Boolean {
            val delay = reminderDelay(viewing.startAt, clock.now()) ?: return false
            val request =
                OneTimeWorkRequestBuilder<ViewingReminderWorker>()
                    .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ViewingReminderWorker.KEY_VIEWING_ID to viewing.id))
                    .addTag(TAG)
                    .build()
            workManager.get().enqueueUniqueWork(uniqueName(viewing.id), ExistingWorkPolicy.REPLACE, request)
            return true
        }

        override fun cancel(viewingId: Long) {
            workManager.get().cancelUniqueWork(uniqueName(viewingId))
        }

        companion object {
            val LEAD: Duration = Duration.ofHours(1)
            const val TAG = "viewing-reminder"

            fun uniqueName(viewingId: Long): String = "$TAG-$viewingId"

            /** Time until the reminder is due, or null when that moment has passed (booked under an hour ahead). */
            fun reminderDelay(
                startAt: Instant,
                now: Instant,
            ): Duration? = Duration.between(now, startAt.minus(LEAD)).takeIf { !it.isNegative }
        }
    }
