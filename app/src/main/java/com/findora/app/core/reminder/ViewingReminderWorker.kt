package com.findora.app.core.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findora.app.R
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.ViewingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Fires about an hour before a viewing. Does nothing when the viewing was cancelled (deleted), belongs to a
 * signed-out guest whose data was cleared, or already started.
 */
@HiltWorker
class ViewingReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val viewings: ViewingRepository,
        private val properties: PropertyRepository,
        private val notifier: ViewingReminderNotifier,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val viewingId = inputData.getLong(KEY_VIEWING_ID, NO_ID)
            val viewing = if (viewingId == NO_ID) null else viewings.viewing(viewingId)
            if (viewing != null && viewing.startAt.isAfter(clock.now())) {
                val title =
                    properties.observeProperty(viewing.propertyId).first()?.title
                        ?: applicationContext.getString(R.string.reminder_fallback_title)
                val time =
                    DateTimeFormatter
                        .ofLocalizedTime(FormatStyle.SHORT)
                        .format(viewing.startAt.atZone(zone))
                notifier.post(ViewingReminder(viewing.id, viewing.propertyId, title, time))
            }
            return if (viewingId == NO_ID) Result.failure() else Result.success()
        }

        companion object {
            const val KEY_VIEWING_ID = "viewing_id"
            private const val NO_ID = -1L
        }
    }
