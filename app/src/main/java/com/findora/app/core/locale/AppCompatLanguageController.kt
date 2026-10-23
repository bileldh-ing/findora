package com.findora.app.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.findora.app.core.di.ApplicationScope
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.repository.LanguageController
import com.findora.app.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language through [AppCompatDelegate.setApplicationLocales]: on Android 13+ the system stores it
 * (and shows it in the app's system language settings, declared by `locales_config.xml`); below 13 AppCompat
 * persists it itself (`autoStoreLocales` in the manifest) and recreates every AppCompatActivity.
 *
 * AppCompat can only read the stored list once an activity exists, so the copy in DataStore is the fallback
 * for work that runs without UI (the reminder worker, the demo agent replying in the background).
 */
@Singleton
class AppCompatLanguageController
    @Inject
    constructor(
        settings: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : LanguageController {
        @Volatile
        private var stored: AppLanguage = AppLanguage.System

        init {
            scope.launch {
                settings.settings
                    .map { it.language }
                    .distinctUntilChanged()
                    .collect { stored = it }
            }
        }

        override fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            val first = if (locales.isEmpty) null else locales[0]
            return first?.let { AppLanguage.fromTag(it.language) } ?: stored
        }

        override fun apply(language: AppLanguage) {
            stored = language
            val locales =
                if (language == AppLanguage.System) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
