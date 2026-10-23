package com.findora.app.core.alert

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
import com.findora.app.navigation.PropertyDeepLink
import com.findora.app.navigation.SearchDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A system notification about new listings for a saved search; texts are already localized. */
data class NewMatchesAlert(
    val searchId: Long,
    val title: String,
    val body: String,
)

/** A system notification about a favourite's lower rent; texts are already localized. */
data class PriceDropAlert(
    val propertyId: String,
    val title: String,
    val body: String,
)

/**
 * Posts on the "Search alerts" channel. New matches open Home with the search applied (`findora://search/{id}`);
 * a price drop opens the listing (`findora://property/{id}`). [channelName] and [channelDescription] are the
 * localized channel texts, refreshed on every post so the channel follows the in-app language.
 */
interface SearchAlertNotifier {
    /** Returns false when notifications are off or the permission was denied (nothing is posted). */
    fun postNewMatches(
        alert: NewMatchesAlert,
        channel: ChannelTexts,
    ): Boolean

    fun postPriceDrop(
        alert: PriceDropAlert,
        channel: ChannelTexts,
    ): Boolean
}

data class ChannelTexts(
    val name: String,
    val description: String,
)

@Singleton
class AndroidSearchAlertNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SearchAlertNotifier {
        private val manager = NotificationManagerCompat.from(context)

        override fun postNewMatches(
            alert: NewMatchesAlert,
            channel: ChannelTexts,
        ): Boolean =
            post(
                tag = TAG_SEARCH,
                id = searchNotificationId(alert.searchId),
                title = alert.title,
                body = alert.body,
                uri = SearchDeepLink.uriFor(alert.searchId),
                channel = channel,
            )

        override fun postPriceDrop(
            alert: PriceDropAlert,
            channel: ChannelTexts,
        ): Boolean =
            post(
                tag = TAG_PRICE,
                id = alert.propertyId.hashCode(),
                title = alert.title,
                body = alert.body,
                uri = PropertyDeepLink.uriFor(alert.propertyId),
                channel = channel,
            )

        // The permission is checked by canPost() right before notify(); lint cannot see through the helper.
        @SuppressLint("MissingPermission")
        @Suppress("LongParameterList")
        private fun post(
            tag: String,
            id: Int,
            title: String,
            body: String,
            uri: String,
            channel: ChannelTexts,
        ): Boolean {
            if (!canPost()) return false
            ensureChannel(channel)
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending =
                PendingIntent.getActivity(
                    context,
                    "$tag:$id".hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_search_alert)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()
            manager.notify(tag, id, notification)
            return true
        }

        private fun canPost(): Boolean {
            val permitted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            return permitted && manager.areNotificationsEnabled()
        }

        private fun ensureChannel(texts: ChannelTexts) {
            val channel =
                NotificationChannel(CHANNEL_ID, texts.name, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = texts.description }
            manager.createNotificationChannel(channel)
        }

        companion object {
            const val CHANNEL_ID = "search_alerts"
            const val TAG_SEARCH = "search-alert"
            const val TAG_PRICE = "price-drop"

            /** One notification per saved search: a later batch replaces the earlier one. */
            fun searchNotificationId(searchId: Long): Int = (searchId % Int.MAX_VALUE).toInt()
        }
    }
