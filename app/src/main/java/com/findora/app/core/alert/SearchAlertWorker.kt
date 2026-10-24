package com.findora.app.core.alert

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findora.app.R
import com.findora.app.core.domain.alert.AlertReport
import com.findora.app.core.domain.alert.CheckSearchAlerts
import com.findora.app.core.domain.alert.PriceDrop
import com.findora.app.core.domain.alert.SearchAlert
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.locale.LocalizedContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Checks the signed-in user's saved searches and favourites and reports what is new, once:
 * - periodic runs ([KEY_ON_REFRESH] false) first refresh the regions the searches look at (the fake backend is
 *   local, so no network constraint), then check daily and instant searches;
 * - runs queued after a listings refresh only check instant searches.
 * Entries go to the notifications center when in-app notifications are on and to the "Search alerts" channel when
 * search alerts are on (and POST_NOTIFICATIONS is granted). Nothing runs for a signed-out user, so an alert never
 * appears on a device for someone who is no longer signed in.
 */
@HiltWorker
class SearchAlertWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val auth: AuthRepository,
        private val currentRegion: CurrentRegion,
        private val properties: PropertyRepository,
        private val checkAlerts: CheckSearchAlerts,
        private val notifications: NotificationRepository,
        private val notifier: SearchAlertNotifier,
        private val settings: SettingsRepository,
        private val localized: LocalizedContext,
        private val displayPreferences: ObserveDisplayPreferences,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val session = auth.restoreSession() ?: return Result.success()
            val onRefresh = inputData.getBoolean(KEY_ON_REFRESH, false)
            val fallbackRegion = currentRegion.region.first().id
            if (!onRefresh) {
                checkAlerts
                    .regionsToRefresh(session.userId, fallbackRegion)
                    .forEach { properties.refresh(it, force = false) }
            }
            val report = checkAlerts(session.userId, fallbackRegion, onRefresh)
            if (!report.isEmpty) publish(session.userId, report, settings.settings.first())
            return Result.success()
        }

        private suspend fun publish(
            userId: String,
            report: AlertReport,
            prefs: AppSettings,
        ) {
            val context = localized.resolve(prefs.language)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val channel =
                ChannelTexts(
                    context.getString(R.string.search_alerts_channel_name),
                    context.getString(R.string.search_alerts_channel_description),
                )
            val inApp = prefs.isEnabled(NotificationToggle.InApp)
            val system = prefs.isEnabled(NotificationToggle.SearchAlerts)
            report.searchAlerts.forEach { alert ->
                if (inApp) notifications.add(userId, alert.toNotification())
                if (system) notifier.postNewMatches(alert.toSystemAlert(context), channel)
            }
            val display = displayPreferences().first()
            report.priceDrops.forEach { drop ->
                val change = priceChange(context, drop, locale, display)
                if (inApp) {
                    notifications.add(
                        userId,
                        NewNotification(NotificationKind.PriceDrop, drop.property.title, drop.property.id, change),
                    )
                }
                if (system) {
                    notifier.postPriceDrop(
                        PriceDropAlert(
                            drop.property.id,
                            context.getString(R.string.price_drop_title, drop.property.title),
                            change,
                        ),
                        channel,
                    )
                }
            }
        }

        private fun SearchAlert.toNotification(): NewNotification {
            val newest = newListings.first()
            return NewNotification(
                kind = NotificationKind.NewMatches,
                subject = search.name,
                propertyId = newest.id,
                preview = newest.title,
                savedSearchId = search.id,
                count = newListings.size,
            )
        }

        private fun SearchAlert.toSystemAlert(context: Context): NewMatchesAlert =
            NewMatchesAlert(
                searchId = search.id,
                title =
                    context.resources.getQuantityString(
                        R.plurals.search_alert_title,
                        newListings.size,
                        newListings.size,
                        search.name,
                    ),
                body = context.getString(R.string.search_alert_body, newListings.first().title),
            )

        private fun priceChange(
            context: Context,
            drop: PriceDrop,
            locale: Locale,
            display: DisplayPreferences,
        ): String =
            context.getString(
                R.string.price_drop_body,
                PriceFormatter.formatDisplay(display.display(drop.current), locale),
                PriceFormatter.formatDisplay(display.display(drop.previous), locale),
            )

        companion object {
            const val KEY_ON_REFRESH = "on_refresh"
        }
    }
