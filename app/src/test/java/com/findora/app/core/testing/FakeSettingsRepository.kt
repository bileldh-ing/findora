package com.findora.app.core.testing

import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.model.ReduceMotion
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

    override suspend fun setAreaUnit(unit: AreaUnit) = settings.update { it.copy(areaUnit = unit) }

    override suspend fun setReduceMotion(mode: ReduceMotion) = settings.update { it.copy(reduceMotion = mode) }

    override suspend fun setNotificationEnabled(
        toggle: NotificationToggle,
        enabled: Boolean,
    ) = settings.update {
        when (toggle) {
            NotificationToggle.Messages -> it.copy(messageNotifications = enabled)
            NotificationToggle.ViewingReminders -> it.copy(viewingReminderNotifications = enabled)
            NotificationToggle.InApp -> it.copy(inAppNotifications = enabled)
            NotificationToggle.SearchAlerts -> it.copy(searchAlertNotifications = enabled)
        }
    }
}
