package com.findora.app.core.domain.viewing

import androidx.compose.runtime.Immutable
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Opening hours and granularity of the viewing calendar. */
data class SlotConfig(
    val opensAt: LocalTime = DEFAULT_OPENS_AT,
    /** The last slot must end by this time. */
    val closesAt: LocalTime = DEFAULT_CLOSES_AT,
    val stepMinutes: Int = DEFAULT_STEP_MINUTES,
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val horizonDays: Int = DEFAULT_HORIZON_DAYS,
) {
    init {
        require(stepMinutes > 0 && durationMinutes > 0 && horizonDays > 0) { "positive values required" }
        require(opensAt.isBefore(closesAt)) { "opensAt must be before closesAt" }
    }

    companion object {
        val DEFAULT_OPENS_AT: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_CLOSES_AT: LocalTime = LocalTime.of(18, 0)
        const val DEFAULT_STEP_MINUTES = 30
        const val DEFAULT_DURATION_MINUTES = 30
        const val DEFAULT_HORIZON_DAYS = 14
    }
}

enum class SlotStatus {
    Available,

    /** Already booked at this listing (by this user or someone else). */
    Taken,

    /** The user has another viewing at that time. */
    Busy,
}

@Immutable
data class ViewingSlot(
    val range: TimeRange,
    val status: SlotStatus,
) {
    val start: Instant get() = range.start
    val isAvailable: Boolean get() = status == SlotStatus.Available
}

@Immutable
data class ViewingDay(
    val date: LocalDate,
    val slots: List<ViewingSlot>,
    val isToday: Boolean = false,
) {
    val availableCount: Int get() = slots.count { it.isAvailable }
}

/**
 * Builds the bookable calendar: the next [SlotConfig.horizonDays] days in [zone] from [clock]'s today, one slot
 * every [SlotConfig.stepMinutes] between opening and closing. Slots that already started are left out (a day with
 * none left is dropped); slots overlapping a booking at the listing are [SlotStatus.Taken] and slots overlapping
 * one of the user's own viewings are [SlotStatus.Busy]. Cancelled viewings are deleted, so they free their slot.
 */
class ViewingSlotGenerator(
    private val clock: AppClock,
    private val zone: ZoneId,
    val config: SlotConfig,
) {
    @Inject
    constructor(clock: AppClock, zone: ZoneId) : this(clock, zone, SlotConfig())

    fun days(
        propertyBookings: List<TimeRange>,
        userBookings: List<TimeRange>,
    ): List<ViewingDay> {
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        return (0 until config.horizonDays)
            .map { offset -> today.plusDays(offset.toLong()) }
            .map { date ->
                ViewingDay(date, slotsOn(date, now, propertyBookings, userBookings), isToday = date == today)
            }.filter { it.slots.isNotEmpty() }
    }

    /** Whether a slot starting at [start] can be booked now (used to re-check right before saving). */
    fun isBookable(
        start: Instant,
        propertyBookings: List<TimeRange>,
        userBookings: List<TimeRange>,
    ): Boolean =
        days(propertyBookings, userBookings)
            .flatMap { it.slots }
            .any { it.start == start && it.isAvailable }

    private fun slotsOn(
        date: LocalDate,
        now: Instant,
        propertyBookings: List<TimeRange>,
        userBookings: List<TimeRange>,
    ): List<ViewingSlot> {
        val lastStart = config.closesAt.minusMinutes(config.durationMinutes.toLong())
        if (lastStart.isBefore(config.opensAt)) return emptyList()
        val starts = mutableListOf<Instant>()
        var time = config.opensAt
        while (!time.isAfter(lastStart)) {
            // atZone shifts a time that falls in a DST gap forward, so every slot is a real instant.
            starts += date.atTime(time).atZone(zone).toInstant()
            val next = time.plusMinutes(config.stepMinutes.toLong())
            if (!next.isAfter(time)) break
            time = next
        }
        return starts
            .distinct()
            .filter { it.isAfter(now) }
            .map { start ->
                val range = TimeRange(start, start.plusSeconds(config.durationMinutes * SECONDS_PER_MINUTE))
                val status =
                    when {
                        propertyBookings.any { it.overlaps(range) } -> SlotStatus.Taken
                        userBookings.any { it.overlaps(range) } -> SlotStatus.Busy
                        else -> SlotStatus.Available
                    }
                ViewingSlot(range, status)
            }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
