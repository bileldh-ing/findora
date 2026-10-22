package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setRegion(regionId: String)

    suspend fun setCurrencyOverride(currency: Currency?)

    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Ask for a biometric (or device credential) unlock before showing the app when a session is restored. */
    suspend fun setBiometricLockEnabled(enabled: Boolean)
}
