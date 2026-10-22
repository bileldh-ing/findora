package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.FavoriteEntity
import com.findora.app.core.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoriteDao {
    @Query("SELECT property_id FROM favorites WHERE user_id = :userId ORDER BY created_at DESC")
    abstract fun observeIds(userId: String): Flow<List<String>>

    /** Favourite listings that are still cached, most recently favourited first. */
    @Query(
        """
        SELECT p.* FROM properties p
        INNER JOIN favorites f ON f.property_id = p.id
        WHERE f.user_id = :userId
        ORDER BY f.created_at DESC
        """,
    )
    abstract fun observeProperties(userId: String): Flow<List<PropertyEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id = :userId AND property_id = :propertyId)")
    abstract suspend fun isFavorite(
        userId: String,
        propertyId: String,
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE user_id = :userId AND property_id = :propertyId")
    abstract suspend fun delete(
        userId: String,
        propertyId: String,
    )

    @Query("SELECT COUNT(*) FROM favorites WHERE user_id = :userId")
    abstract suspend fun count(userId: String): Int

    /** Returns true when the listing is a favourite after the call. */
    @Transaction
    open suspend fun toggle(
        userId: String,
        propertyId: String,
        nowMillis: Long,
    ): Boolean =
        if (isFavorite(userId, propertyId)) {
            delete(userId, propertyId)
            false
        } else {
            insert(FavoriteEntity(userId, propertyId, nowMillis))
            true
        }
}
