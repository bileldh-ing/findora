package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/** A booked visit of a listing by one user. Cancelling deletes it, which frees the slot. */
@Immutable
data class Viewing(
    val id: Long,
    val userId: String,
    val propertyId: String,
    val startAt: Instant,
    val durationMinutes: Int,
    val note: String,
    val createdAt: Instant,
) {
    val endAt: Instant get() = startAt.plusSeconds(durationMinutes * SECONDS_PER_MINUTE)

    val range: TimeRange get() = TimeRange(startAt, endAt)

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}

/** A half-open time interval [start, end). */
@Immutable
data class TimeRange(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(end.isAfter(start)) { "end must be after start" }
    }

    fun overlaps(other: TimeRange): Boolean = start.isBefore(other.end) && other.start.isBefore(end)
}

/** Why a viewing could not be booked. */
enum class BookingError {
    /** The slot started already. */
    InThePast,

    /** Someone already booked this listing at that time. */
    SlotTaken,

    /** The user has another viewing at that time. */
    Overlaps,
}

sealed interface BookingResult {
    data class Booked(
        val viewing: Viewing,
    ) : BookingResult

    data class Rejected(
        val error: BookingError,
    ) : BookingResult
}
