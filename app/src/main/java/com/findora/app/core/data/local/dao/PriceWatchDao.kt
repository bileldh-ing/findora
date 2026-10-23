package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.findora.app.core.data.local.entity.PriceWatchEntity

/** Last-checked rents of each user's favourites (price-drop alerts). */
@Dao
abstract class PriceWatchDao {
    @Query("SELECT * FROM price_watch WHERE user_id = :userId")
    abstract suspend fun forUser(userId: String): List<PriceWatchEntity>

    @Upsert
    protected abstract suspend fun upsert(rows: List<PriceWatchEntity>)

    @Query("DELETE FROM price_watch WHERE user_id = :userId")
    protected abstract suspend fun deleteForUser(userId: String)

    @Transaction
    open suspend fun replace(
        userId: String,
        rows: List<PriceWatchEntity>,
    ) {
        deleteForUser(userId)
        if (rows.isNotEmpty()) upsert(rows)
    }
}
