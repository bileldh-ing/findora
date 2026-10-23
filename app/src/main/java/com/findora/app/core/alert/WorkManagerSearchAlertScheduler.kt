package com.findora.app.core.alert

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.findora.app.core.domain.repository.ListingsRefreshListener
import com.findora.app.core.domain.repository.SearchAlertScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * One unique periodic check ([PERIODIC_NAME], every [PERIOD_HOURS] hours, only when the battery is not low; no
 * network constraint because listings come from the bundled fake backend) plus a one-off instant check queued
 * after each successful listings refresh ([REFRESH_NAME]; a pending one is kept, so a burst of refreshes runs it
 * once).
 */
@Singleton
class WorkManagerSearchAlertScheduler
    @Inject
    constructor(
        private val workManager: Provider<WorkManager>,
    ) : SearchAlertScheduler,
        ListingsRefreshListener {
        override fun ensureScheduled() {
            val request =
                PeriodicWorkRequestBuilder<SearchAlertWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()
            workManager.get().enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        override fun onRegionRefreshed(regionId: String) {
            val request =
                OneTimeWorkRequestBuilder<SearchAlertWorker>()
                    .setInputData(workDataOf(SearchAlertWorker.KEY_ON_REFRESH to true))
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()
            workManager.get().enqueueUniqueWork(REFRESH_NAME, ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            const val TAG = "search-alerts"
            const val PERIODIC_NAME = "search-alerts-periodic"
            const val REFRESH_NAME = "search-alerts-refresh"
            const val PERIOD_HOURS = 4L

            val constraints: Constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        }
    }
