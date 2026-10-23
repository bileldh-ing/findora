package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.AppLanguage

/** The per-app language: applied to the whole app (and Android 13+ system settings), not only to Findora's prefs. */
interface LanguageController {
    /** The language in effect now ([AppLanguage.System] when the app follows the device). */
    fun current(): AppLanguage

    /** Applies [language] immediately: visible activities are recreated, so text and RTL layout flip at once. */
    fun apply(language: AppLanguage)
}
