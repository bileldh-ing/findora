package com.findora.app.core.testing

import com.findora.app.core.data.local.entity.PropertyEntity
import com.findora.app.core.data.local.toEntity

/** Seed listings as cache rows, optionally overriding id/region/rent for query tests. */
fun seedEntity(
    index: Int = 0,
    id: String? = null,
    regionId: String? = null,
    rent: Long? = null,
    createdAtMillis: Long? = null,
    fetchedAtMillis: Long = 0,
): PropertyEntity {
    val base = seedListings[index].toEntity(fetchedAtMillis)
    return base.copy(
        id = id ?: base.id,
        regionId = regionId ?: base.regionId,
        rentAmount = rent ?: base.rentAmount,
        createdAtMillis = createdAtMillis ?: base.createdAtMillis,
    )
}
