package com.findora.app.core.domain.usecase

import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.repository.ReferenceDataRepository
import com.findora.app.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The display currency and area unit from Settings, with the bundled exchange rates attached. */
class ObserveDisplayPreferences
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val referenceData: ReferenceDataRepository,
    ) {
        operator fun invoke(): Flow<DisplayPreferences> =
            settings.settings
                .map { prefs ->
                    DisplayPreferences(
                        currency = prefs.currencyOverride,
                        areaUnit = prefs.areaUnit,
                        rates = prefs.currencyOverride?.let { referenceData.exchangeRates() },
                    )
                }.distinctUntilChanged()
    }
