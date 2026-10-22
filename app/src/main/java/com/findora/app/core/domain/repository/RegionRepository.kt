package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.RegionPack

interface RegionRepository {
    /** All region packs; the default (Kerala) comes first. */
    suspend fun regions(): List<RegionPack>

    suspend fun region(id: String): RegionPack?
}
