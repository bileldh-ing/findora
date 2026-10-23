package com.findora.app.core.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findora.app.R
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.locale.LocalizedContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Fires about an hour before a viewing, in the language chosen in Settings, honouring the viewing-reminder and
 * in-app notification toggles. Does nothing when the viewing was cancelled (deleted), belongs to a
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
        private val notifications: NotificationRepository,
        private val clock: AppClock,
        private val zone: ZoneId,
        private val settings: SettingsRepository,
        private val localized: LocalizedContext,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val viewingId = inputData.getLong(KEY_VIEWING_ID, NO_ID)
            val viewing = if (viewingId == NO_ID) null else viewings.viewing(viewingId)
            if (viewing != null && viewing.startAt.isAfter(clock.now())) {
                val prefs = settings.settings.first()
                val context = localized.resolve(prefs.language)
                val title =
                    properties.observeProperty(viewing.propertyId).first()?.title
                        ?: context.getString(R.string.reminder_fallback_title)
                val time =
                    DateTimeFormatter
                        .ofLocalizedTime(FormatStyle.SHORT)
                        .withLocale(context.resources.configuration.locales[0])
                        .format(viewing.startAt.atZone(zone))
                if (prefs.isEnabled(NotificationToggle.InApp)) {
                    notifications.add(
                        viewing.userId,
                        NewNotification(
                            NotificationKind.ViewingReminder,
                            title,
                            viewing.propertyId,
                            eventAt = viewing.startAt,
                        ),
                    )
                }
                if (prefs.isEnabled(NotificationToggle.ViewingReminders)) {
                    notifier.post(ViewingReminder(viewing.id, viewing.propertyId, title, time))
                }
            }
            return if (viewingId == NO_ID) Result.failure() else Result.success()
        }

        companion object {
            const val KEY_VIEWING_ID = "viewing_id"
            private const val NO_ID = -1L
        }
    }
