package com.findora.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.findora.app.core.domain.repository.HomeWidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.findora.app.core.di.ApplicationScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Redraws every placed "Findora picks" widget (each run reloads its data). */
@HiltWorker
class PicksWidgetWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            FindoraPicksWidget().updateAll(applicationContext)
            return Result.success()
        }
    }

/**
 * Periodic refresh every [PERIOD_HOURS] hours while a widget is placed (scheduled when the first one is added,
 * cancelled when the last one is removed), plus a one-off refresh a few seconds after favourites or viewings
 * change; a burst of changes is folded into one redraw.
 */
@Singleton
class WorkManagerHomeWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val workManager: Provider<WorkManager>,
        @ApplicationScope private val scope: CoroutineScope,
    ) : HomeWidgetRefresher {
        override fun ensureScheduled() {
            scope.launch {
                if (!hasWidgets()) return@launch
                val request = PeriodicWorkRequestBuilder<PicksWidgetWorker>(PERIOD_HOURS, TimeUnit.HOURS).build()
                workManager.get().enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        override fun requestRefresh() {
            scope.launch {
                if (!hasWidgets()) return@launch
                val request =
                    OneTimeWorkRequestBuilder<PicksWidgetWorker>()
                        .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                        .build()
                workManager.get().enqueueUniqueWork(REFRESH_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }

        private suspend fun hasWidgets(): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(FindoraPicksWidget::class.java).isNotEmpty()

        companion object {
            const val PERIODIC_NAME = "picks-widget-periodic"
            const val REFRESH_NAME = "picks-widget-refresh"
            const val PERIOD_HOURS = 3L
            const val DEBOUNCE_SECONDS = 3L

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            }
        }
    }
