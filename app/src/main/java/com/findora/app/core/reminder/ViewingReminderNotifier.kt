package com.findora.app.core.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.findora.app.MainActivity
import com.findora.app.R
import com.findora.app.core.locale.LocalizedContext
import com.findora.app.navigation.PropertyDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a viewing reminder says. [timeText] is already formatted for the user's locale and zone. */
data class ViewingReminder(
    val viewingId: Long,
    val propertyId: String,
    val propertyTitle: String,
    val timeText: String,
)

/** Posts viewing reminders on the "Viewing reminders" channel; a tap opens the listing's detail page. */
interface ViewingReminderNotifier {
    /** Returns false when notifications are off or the permission was denied (nothing is posted). */
    fun post(reminder: ViewingReminder): Boolean
}

@Singleton
class AndroidViewingReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val localized: LocalizedContext,
    ) : ViewingReminderNotifier {
        private val manager = NotificationManagerCompat.from(context)

        // The permission is checked by canPost() right before notify(); lint cannot see through the helper.
        @SuppressLint("MissingPermission")
        override fun post(reminder: ViewingReminder): Boolean {
            if (!canPost()) return false
            ensureChannel()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_viewing)
                    .setContentTitle(localized.get().getString(R.string.reminder_title))
                    .setContentText(
                        localized.get().getString(R.string.reminder_body, reminder.propertyTitle, reminder.timeText),
                    ).setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(openPropertyIntent(reminder))
                    .build()
            manager.notify(notificationId(reminder.viewingId), notification)
            return true
        }

        private fun canPost(): Boolean {
            val permitted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            return permitted && manager.areNotificationsEnabled()
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    localized.get().getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = localized.get().getString(R.string.reminder_channel_description) }
            manager.createNotificationChannel(channel)
        }

        private fun openPropertyIntent(reminder: ViewingReminder): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(PropertyDeepLink.uriFor(reminder.propertyId)))
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                notificationId(reminder.viewingId),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        companion object {
            const val CHANNEL_ID = "viewing_reminders"

            fun notificationId(viewingId: Long): Int = (viewingId % Int.MAX_VALUE).toInt()
        }
    }
