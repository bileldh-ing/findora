package com.findora.app.core.domain.usecase

import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.RegionRepository
import com.findora.app.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/** The region chosen in settings resolved to its pack (falls back to the default region if it disappeared). */
class CurrentRegion
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val regions: RegionRepository,
    ) {
        val region: Flow<RegionPack> =
            settings.settings
                .map { it.regionId }
                .distinctUntilChanged()
                .mapNotNull { id -> regions.region(id) ?: regions.regions().firstOrNull() }

        suspend fun all(): List<RegionPack> = regions.regions()

        suspend fun select(regionId: String) = settings.setRegion(regionId)
    }
