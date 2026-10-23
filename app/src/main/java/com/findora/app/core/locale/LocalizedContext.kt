package com.findora.app.core.locale

import android.content.Context
import android.content.res.Configuration
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.repository.LanguageController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A context whose resources speak the language chosen in Settings. Code outside activities (notifications, the
 * reminder worker, the demo agent's replies) uses it instead of the application context, whose configuration
 * below Android 13 still follows the device language.
 */
@Singleton
class LocalizedContext
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val languages: LanguageController,
    ) {
        fun get(): Context = forLanguage(languages.current())

        val locale: Locale get() = get().resources.configuration.locales[0]

        /**
         * For background work: the language AppCompat reports, or [stored] (the Settings copy) when no activity
         * has started in this process yet and AppCompat cannot tell.
         */
        fun resolve(stored: AppLanguage): Context =
            forLanguage(languages.current().takeUnless { it == AppLanguage.System } ?: stored)

        fun forLanguage(language: AppLanguage): Context {
            if (language == AppLanguage.System) return context
            // setLocale also sets the layout direction, so Arabic text gets an RTL configuration.
            val configuration =
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
            return context.createConfigurationContext(configuration)
        }
    }
