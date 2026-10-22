package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.findora.app.core.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PropertyDao {
    @Query(
        """
        SELECT * FROM properties
        WHERE region_id = :regionId
          AND (:type IS NULL OR type = :type)
          AND (:minRent IS NULL OR rent_amount >= :minRent)
          AND (:maxRent IS NULL OR rent_amount <= :maxRent)
          AND (:minBedrooms IS NULL OR bedrooms >= :minBedrooms)
          AND (:verifiedOnly = 0 OR verified = 1)
        ORDER BY created_at DESC, id ASC
        """,
    )
    abstract fun observeByRegion(
        regionId: String,
        type: String?,
        minRent: Long?,
        maxRent: Long?,
        minBedrooms: Int?,
        verifiedOnly: Boolean,
    ): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id")
    abstract fun observeById(id: String): Flow<PropertyEntity?>

    @Query("SELECT * FROM properties WHERE id = :id")
    abstract suspend fun findById(id: String): PropertyEntity?

    @Query("SELECT COUNT(*) FROM properties WHERE region_id = :regionId")
    abstract suspend fun countInRegion(regionId: String): Int

    /** Oldest fetch time in the region: the cache is only as fresh as its stalest row. */
    @Query("SELECT MIN(fetched_at) FROM properties WHERE region_id = :regionId")
    abstract suspend fun oldestFetchInRegion(regionId: String): Long?

    @Upsert
    abstract suspend fun upsertAll(properties: List<PropertyEntity>)

    @Query("DELETE FROM properties WHERE region_id = :regionId AND id NOT IN (:keepIds)")
    abstract suspend fun deleteRegionExcept(
        regionId: String,
        keepIds: List<String>,
    )

    @Query("DELETE FROM properties WHERE region_id = :regionId")
    abstract suspend fun deleteRegion(regionId: String)

    /** Atomically makes the cached region equal to [properties] (listings removed upstream disappear). */
    @Transaction
    open suspend fun replaceRegion(
        regionId: String,
        properties: List<PropertyEntity>,
    ) {
        if (properties.isEmpty()) {
            deleteRegion(regionId)
        } else {
            deleteRegionExcept(regionId, properties.map(PropertyEntity::id))
            upsertAll(properties)
        }
    }
}
