package com.findora.app.core.testing

import com.findora.app.core.domain.model.BookingError
import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.domain.model.TimeRange
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory viewings with the same rules as the Room repository (past, taken, overlapping). */
class FakeViewingRepository(
    private val now: () -> Instant,
) : ViewingRepository {
    val all = MutableStateFlow<List<Viewing>>(emptyList())

    /** When set, the next schedule() returns this instead of applying the rules. */
    var nextResult: BookingResult? = null
    var failCancel = false
    private var nextId = 1L

    fun add(viewing: Viewing): Viewing {
        val stored = viewing.copy(id = nextId++)
        all.update { it + stored }
        return stored
    }

    override fun observeViewings(userId: String): Flow<List<Viewing>> =
        all.map { list -> list.filter { it.userId == userId }.sortedBy { it.startAt } }

    override fun observePropertyBookings(
        propertyId: String,
        from: Instant,
    ): Flow<List<TimeRange>> =
        all.map { list -> list.filter { it.propertyId == propertyId && it.endAt.isAfter(from) }.map { it.range } }

    override suspend fun viewing(id: Long): Viewing? = all.value.firstOrNull { it.id == id }

    override suspend fun schedule(
        userId: String,
        propertyId: String,
        startAt: Instant,
        durationMinutes: Int,
        note: String,
    ): BookingResult {
        nextResult?.let {
            nextResult = null
            return it
        }
        val candidate = Viewing(0, userId, propertyId, startAt, durationMinutes, note.trim(), now())
        return when {
            !startAt.isAfter(now()) -> BookingResult.Rejected(BookingError.InThePast)
            all.value.any { it.propertyId == propertyId && it.range.overlaps(candidate.range) } ->
                BookingResult.Rejected(BookingError.SlotTaken)
            all.value.any { it.userId == userId && it.range.overlaps(candidate.range) } ->
                BookingResult.Rejected(BookingError.Overlaps)
            else -> BookingResult.Booked(add(candidate))
        }
    }

    override suspend fun cancel(
        userId: String,
        viewingId: Long,
    ): Boolean {
        if (failCancel) return false
        val before = all.value.size
        all.update { list -> list.filterNot { it.id == viewingId && it.userId == userId } }
        return all.value.size < before
    }
}

class RecordingReminderScheduler(
    var schedules: Boolean = true,
) : ViewingReminderScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(viewing: Viewing): Boolean {
        scheduled += viewing.id
        return schedules
    }

    override fun cancel(viewingId: Long) {
        cancelled += viewingId
    }
}
