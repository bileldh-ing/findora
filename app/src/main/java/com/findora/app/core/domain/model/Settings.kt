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

/** User-facing settings persisted in DataStore. A null [currencyOverride] means "use the region's currency". */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    val regionId: String = RegionPack.DEFAULT_REGION_ID,
    val currencyOverride: Currency? = null,
    val onboardingCompleted: Boolean = false,
)
