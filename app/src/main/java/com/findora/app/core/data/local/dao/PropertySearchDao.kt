package com.findora.app.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.findora.app.core.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

/** Aggregates over the cached listings of a region; MIN/MAX are null when the region is empty. */
data class BoundsRow(
    @ColumnInfo(name = "min_rent") val minRent: Long?,
    @ColumnInfo(name = "max_rent") val maxRent: Long?,
    @ColumnInfo(name = "min_area") val minArea: Int?,
    @ColumnInfo(name = "max_area") val maxArea: Int?,
    val count: Int,
)

/** Read-only queries behind the Home feed and search. */
@Dao
interface PropertySearchDao {
    /** Paged feed for a query built by PropertyQueryBuilder; invalidated whenever the properties table changes. */
    @RawQuery(observedEntities = [PropertyEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, PropertyEntity>

    @Query(
        """
        SELECT MIN(rent_amount) AS min_rent, MAX(rent_amount) AS max_rent,
               MIN(area_sqm) AS min_area, MAX(area_sqm) AS max_area, COUNT(*) AS count
        FROM properties WHERE region_id = :regionId
        """,
    )
    fun observeBounds(regionId: String): Flow<BoundsRow>

    @Query(
        """
        SELECT DISTINCT locality FROM properties
        WHERE region_id = :regionId AND locality LIKE :pattern ESCAPE '\'
        ORDER BY locality ASC LIMIT :limit
        """,
    )
    suspend fun matchingLocalities(
        regionId: String,
        pattern: String,
        limit: Int,
    ): List<String>

    @Query(
        """
        SELECT title FROM properties
        WHERE region_id = :regionId AND title LIKE :pattern ESCAPE '\'
        ORDER BY rating DESC, title ASC LIMIT :limit
        """,
    )
    suspend fun matchingTitles(
        regionId: String,
        pattern: String,
        limit: Int,
    ): List<String>
}
