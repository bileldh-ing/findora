package com.findora.app.core.domain.model

enum class ThemeMode { System, Light, Dark }

/** In-app language; [tag] is a BCP 47 tag, empty for "follow the system". */
enum class AppLanguage(
    val tag: String,
) {
    System(""),
    English("en"),
    French("fr"),
    Arabic("ar"),
    ;

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/** How areas are shown. Listings store square metres; imperial converts to square feet for display only. */
enum class AreaUnit { Metric, Imperial }

/** Reduce-motion override: follow the system animator scale, or force decorative motion off or on. */
enum class ReduceMotion { System, On, Off }

/** Which notifications the user wants. Each one is respected by the code that would post it. */
enum class NotificationToggle {
    /** System notifications for agent replies. */
    Messages,

    /** System notifications an hour before a viewing. */
    ViewingReminders,

    /** New entries in the in-app notifications center. */
    InApp,

    /** System notifications for saved-search matches and price drops on favourites. */
    SearchAlerts,
}

/**
 * User-facing settings persisted in DataStore. A null [currencyOverride] means "show each listing in its own
 * currency"; a value converts every displayed rent to that currency with the bundled indicative rates.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    val regionId: String = RegionPack.DEFAULT_REGION_ID,
    val currencyOverride: Currency? = null,
    val onboardingCompleted: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val areaUnit: AreaUnit = AreaUnit.Metric,
    val reduceMotion: ReduceMotion = ReduceMotion.System,
    val messageNotifications: Boolean = true,
    val viewingReminderNotifications: Boolean = true,
    val inAppNotifications: Boolean = true,
    val searchAlertNotifications: Boolean = true,
) {
    fun isEnabled(toggle: NotificationToggle): Boolean =
        when (toggle) {
            NotificationToggle.Messages -> messageNotifications
            NotificationToggle.ViewingReminders -> viewingReminderNotifications
            NotificationToggle.InApp -> inAppNotifications
            NotificationToggle.SearchAlerts -> searchAlertNotifications
        }
}
