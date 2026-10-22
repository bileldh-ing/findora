package com.findora.app.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.BookingOutcome
import com.findora.app.core.data.local.dao.ViewingDao
import com.findora.app.core.data.local.entity.ViewingEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.BookingError
import com.findora.app.core.domain.model.BookingResult
import com.findora.app.core.domain.model.TimeRange
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.domain.repository.ViewingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomViewingRepository
    @Inject
    constructor(
        private val dao: ViewingDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewingRepository {
        override fun observeViewings(userId: String): Flow<List<Viewing>> =
            dao
                .observeForUser(userId)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override fun observePropertyBookings(
            propertyId: String,
            from: Instant,
        ): Flow<List<TimeRange>> =
            dao
                .observeForProperty(propertyId, from.toEpochMilli())
                .map { rows -> rows.map { it.toDomain().range } }
                .flowOn(ioDispatcher)

        override suspend fun viewing(id: Long): Viewing? = withContext(ioDispatcher) { dao.byId(id)?.toDomain() }

        override suspend fun schedule(
            userId: String,
            propertyId: String,
            startAt: Instant,
            durationMinutes: Int,
            note: String,
        ): BookingResult {
            require(durationMinutes > 0) { "durationMinutes must be positive" }
            val now = clock.now()
            if (!startAt.isAfter(now)) return BookingResult.Rejected(BookingError.InThePast)
            val entity =
                ViewingEntity(
                    userId = userId,
                    propertyId = propertyId,
                    startAtMillis = startAt.toEpochMilli(),
                    durationMinutes = durationMinutes,
                    status = STATUS_BOOKED,
                    note = note.trim().take(MAX_NOTE_LENGTH),
                    createdAtMillis = now.toEpochMilli(),
                )
            return withContext(ioDispatcher) {
                val outcome =
                    try {
                        dao.bookIfFree(entity)
                    } catch (_: SQLiteConstraintException) {
                        // The unique (property, start) index is the last line of defence against a race.
                        BookingOutcome.SlotTaken
                    }
                when (outcome) {
                    is BookingOutcome.Booked -> BookingResult.Booked(entity.copy(id = outcome.id).toDomain())
                    BookingOutcome.SlotTaken -> BookingResult.Rejected(BookingError.SlotTaken)
                    BookingOutcome.Overlaps -> BookingResult.Rejected(BookingError.Overlaps)
                }
            }
        }

        override suspend fun cancel(
            userId: String,
            viewingId: Long,
        ): Boolean = withContext(ioDispatcher) { dao.delete(viewingId, userId) > 0 }

        companion object {
            const val STATUS_BOOKED = "booked"
            const val MAX_NOTE_LENGTH = 280
        }
    }

internal fun ViewingEntity.toDomain(): Viewing =
    Viewing(
        id = id,
        userId = userId,
        propertyId = propertyId,
        startAt = Instant.ofEpochMilli(startAtMillis),
        durationMinutes = durationMinutes,
        note = note,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
    )
