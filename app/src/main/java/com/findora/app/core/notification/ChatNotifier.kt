package com.findora.app.core.notification

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
import com.findora.app.navigation.ChatDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An agent message that arrived while its thread was not on screen. */
data class ChatNotification(
    val conversationId: String,
    val agentName: String,
    val propertyTitle: String,
    val body: String,
)

/** Posts agent messages on the "Messages" channel; a tap opens the thread through `findora://chat/{id}`. */
interface ChatNotifier {
    /** Returns false when notifications are off or the permission was denied (nothing is posted). */
    fun post(notification: ChatNotification): Boolean

    /** Removes the thread's notification once the user opened it in the app. */
    fun cancel(conversationId: String)
}

@Singleton
class AndroidChatNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ChatNotifier {
        private val manager = NotificationManagerCompat.from(context)

        // The permission is checked by canPost() right before notify(); lint cannot see through the helper.
        @SuppressLint("MissingPermission")
        override fun post(notification: ChatNotification): Boolean {
            if (!canPost()) return false
            ensureChannel()
            val built =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(notification.agentName)
                    .setSubText(notification.propertyTitle)
                    .setContentText(notification.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openConversationIntent(notification.conversationId))
                    .build()
            manager.notify(TAG, notificationId(notification.conversationId), built)
            return true
        }

        override fun cancel(conversationId: String) {
            manager.cancel(TAG, notificationId(conversationId))
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
                    context.getString(R.string.messages_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.messages_channel_description) }
            manager.createNotificationChannel(channel)
        }

        private fun openConversationIntent(conversationId: String): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(ChatDeepLink.uriFor(conversationId)))
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                notificationId(conversationId),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        companion object {
            const val CHANNEL_ID = "messages"
            private const val TAG = "chat"

            /** One notification per thread: a newer reply replaces the previous one. */
            fun notificationId(conversationId: String): Int = conversationId.hashCode()
        }
    }
