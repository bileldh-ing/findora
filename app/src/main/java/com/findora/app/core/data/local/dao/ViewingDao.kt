package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.ViewingEntity
import kotlinx.coroutines.flow.Flow

/** Outcome of [ViewingDao.bookIfFree]. */
sealed interface BookingOutcome {
    data class Booked(
        val id: Long,
    ) : BookingOutcome

    data object SlotTaken : BookingOutcome

    data object Overlaps : BookingOutcome
}

@Dao
abstract class ViewingDao {
    /** Throws SQLiteConstraintException when the slot is already booked for that listing. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(viewing: ViewingEntity): Long

    @Query("SELECT * FROM viewings WHERE user_id = :userId AND start_at >= :fromMillis ORDER BY start_at ASC")
    abstract fun observeUpcoming(
        userId: String,
        fromMillis: Long,
    ): Flow<List<ViewingEntity>>

    @Query("SELECT * FROM viewings WHERE user_id = :userId ORDER BY start_at ASC")
    abstract fun observeForUser(userId: String): Flow<List<ViewingEntity>>

    /** Bookings at a listing (any user) still running at or after [fromMillis]. */
    @Query(
        "SELECT * FROM viewings WHERE property_id = :propertyId " +
            "AND start_at + duration_minutes * 60000 > :fromMillis ORDER BY start_at ASC",
    )
    abstract fun observeForProperty(
        propertyId: String,
        fromMillis: Long,
    ): Flow<List<ViewingEntity>>

    @Query("SELECT * FROM viewings WHERE id = :id")
    abstract suspend fun byId(id: Long): ViewingEntity?

    @Query(
        "SELECT start_at FROM viewings WHERE property_id = :propertyId AND start_at BETWEEN :fromMillis AND :toMillis",
    )
    abstract suspend fun bookedSlots(
        propertyId: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<Long>

    @Query(
        "SELECT COUNT(*) FROM viewings WHERE property_id = :propertyId " +
            "AND start_at < :endMillis AND start_at + duration_minutes * 60000 > :startMillis",
    )
    protected abstract suspend fun countAtProperty(
        propertyId: String,
        startMillis: Long,
        endMillis: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM viewings WHERE user_id = :userId " +
            "AND start_at < :endMillis AND start_at + duration_minutes * 60000 > :startMillis",
    )
    protected abstract suspend fun countForUser(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Int

    /** Inserts [viewing] unless its time overlaps a booking at the listing or another viewing of the user. */
    @Transaction
    open suspend fun bookIfFree(viewing: ViewingEntity): BookingOutcome {
        val end = viewing.startAtMillis + viewing.durationMinutes * MILLIS_PER_MINUTE
        return when {
            countAtProperty(viewing.propertyId, viewing.startAtMillis, end) > 0 -> BookingOutcome.SlotTaken
            countForUser(viewing.userId, viewing.startAtMillis, end) > 0 -> BookingOutcome.Overlaps
            else -> BookingOutcome.Booked(insert(viewing))
        }
    }

    @Query("UPDATE viewings SET status = :status WHERE id = :id AND user_id = :userId")
    abstract suspend fun updateStatus(
        id: Long,
        userId: String,
        status: String,
    ): Int

    @Query("DELETE FROM viewings WHERE id = :id AND user_id = :userId")
    abstract suspend fun delete(
        id: Long,
        userId: String,
    ): Int

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
