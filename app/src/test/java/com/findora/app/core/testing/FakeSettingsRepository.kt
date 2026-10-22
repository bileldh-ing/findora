package com.findora.app.core.testing

import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.ThemeMode
import com.findora.app.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }

    override suspend fun setLanguage(language: AppLanguage) = settings.update { it.copy(language = language) }

    override suspend fun setRegion(regionId: String) = settings.update { it.copy(regionId = regionId) }

    override suspend fun setCurrencyOverride(currency: Currency?) =
        settings.update { it.copy(currencyOverride = currency) }

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        settings.update { it.copy(onboardingCompleted = completed) }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) =
        settings.update { it.copy(biometricLockEnabled = enabled) }
}
