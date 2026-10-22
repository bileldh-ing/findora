package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.domain.model.TimeRange
import com.findora.app.core.domain.model.Viewing
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Viewings are owned by one user id (a signed-in account or the guest id). */
interface ViewingRepository {
    /** Every viewing of [userId], earliest first. */
    fun observeViewings(userId: String): Flow<List<Viewing>>

    /** The time ranges already booked at [propertyId] (by anyone) that end after [from]. */
    fun observePropertyBookings(
        propertyId: String,
        from: Instant,
    ): Flow<List<TimeRange>>

    suspend fun viewing(id: Long): Viewing?

    /** Books a slot. Rejects past slots, slots taken at the listing and slots overlapping the user's viewings. */
    suspend fun schedule(
        userId: String,
        propertyId: String,
        startAt: Instant,
        durationMinutes: Int,
        note: String,
    ): BookingResult

    /** Cancels (deletes) a viewing of [userId]. Returns false when there was no such viewing. */
    suspend fun cancel(
        userId: String,
        viewingId: Long,
    ): Boolean
}

/** Schedules the local "viewing in one hour" reminder. Implemented with WorkManager. */
interface ViewingReminderScheduler {
    fun schedule(viewing: Viewing)

    fun cancel(viewingId: Long)
}
