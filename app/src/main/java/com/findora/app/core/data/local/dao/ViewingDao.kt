package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.findora.app.core.data.local.entity.ViewingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewingDao {
    /** Throws SQLiteConstraintException when the slot is already booked for that listing. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(viewing: ViewingEntity): Long

    @Query("SELECT * FROM viewings WHERE user_id = :userId AND start_at >= :fromMillis ORDER BY start_at ASC")
    fun observeUpcoming(
        userId: String,
        fromMillis: Long,
    ): Flow<List<ViewingEntity>>

    @Query(
        "SELECT start_at FROM viewings WHERE property_id = :propertyId AND start_at BETWEEN :fromMillis AND :toMillis",
    )
    suspend fun bookedSlots(
        propertyId: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<Long>

    @Query("UPDATE viewings SET status = :status WHERE id = :id AND user_id = :userId")
    suspend fun updateStatus(
        id: Long,
        userId: String,
        status: String,
    ): Int

    @Query("DELETE FROM viewings WHERE id = :id AND user_id = :userId")
    suspend fun delete(
        id: Long,
        userId: String,
    ): Int
}
