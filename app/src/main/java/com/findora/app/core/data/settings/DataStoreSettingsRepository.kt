package com.findora.app.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Settings in Preferences DataStore. Unknown or corrupted values fall back to defaults instead of crashing. */
@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map(::toSettings)
                .distinctUntilChanged()

        override suspend fun setThemeMode(mode: ThemeMode) = put(Keys.theme, mode.name)

        override suspend fun setLanguage(language: AppLanguage) = put(Keys.language, language.tag)

        override suspend fun setRegion(regionId: String) {
            require(regionId.isNotBlank()) { "regionId must not be blank" }
            put(Keys.region, regionId)
        }

        override suspend fun setCurrencyOverride(currency: Currency?) {
            dataStore.edit { prefs ->
                if (currency == null) prefs.remove(Keys.currency) else prefs[Keys.currency] = currency.code
            }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            dataStore.edit { it[Keys.onboarding] = completed }
        }

        override suspend fun setBiometricLockEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.biometric] = enabled }
        }

        private suspend fun put(
            key: Preferences.Key<String>,
            value: String,
        ) {
            dataStore.edit { it[key] = value }
        }

        private fun toSettings(prefs: Preferences): AppSettings =
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.System,
                language = AppLanguage.fromTag(prefs[Keys.language].orEmpty()),
                regionId = prefs[Keys.region] ?: RegionPack.DEFAULT_REGION_ID,
                currencyOverride =
                    prefs[Keys.currency]?.let { code ->
                        Currency.entries.firstOrNull { it.code == code }
                    },
                onboardingCompleted = prefs[Keys.onboarding] ?: false,
                biometricLockEnabled = prefs[Keys.biometric] ?: false,
            )

        private object Keys {
            val theme = stringPreferencesKey("theme_mode")
            val language = stringPreferencesKey("language")
            val region = stringPreferencesKey("region_id")
            val currency = stringPreferencesKey("currency_override")
            val onboarding = booleanPreferencesKey("onboarding_completed")
            val biometric = booleanPreferencesKey("biometric_lock")
        }
    }
