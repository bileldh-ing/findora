package com.findora.app.core.domain.viewing

import com.findora.app.core.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ViewingSlotGeneratorTest {
    private val tunis = ZoneId.of("Africa/Tunis")

    /** Monday 26 October 2026, 10:10 in Tunis. */
    private val monday = LocalDate.of(2026, 10, 26)

    private fun generator(
        at: LocalDateTime = monday.atTime(10, 10),
        zone: ZoneId = tunis,
        config: SlotConfig = SlotConfig(),
    ) = ViewingSlotGenerator({ at.atZone(zone).toInstant() }, zone, config)

    private fun range(
        date: LocalDate,
        start: LocalTime,
        minutes: Long = 30,
        zone: ZoneId = tunis,
    ): TimeRange {
        val begin = date.atTime(start).atZone(zone).toInstant()
        return TimeRange(begin, begin.plusSeconds(minutes * 60))
    }

    private fun ViewingDay.localTimes(zone: ZoneId = tunis) = slots.map { it.start.atZone(zone).toLocalTime() }

    @Test
    fun coversTheNextFourteenDaysFromToday() {
        val days = generator().days(emptyList(), emptyList())
        assertEquals(14, days.size)
        assertEquals(monday, days.first().date)
        assertEquals(monday.plusDays(13), days.last().date)
        assertTrue(days.first().isToday)
        assertTrue(days.drop(1).none { it.isToday })
    }

    @Test
    fun aFullDayRunsFromOpeningUntilTheLastSlotThatEndsByClosing() {
        val tuesday = generator().days(emptyList(), emptyList())[1]
        val times = tuesday.localTimes()
        assertEquals(18, times.size)
        assertEquals(LocalTime.of(9, 0), times.first())
        assertEquals(LocalTime.of(17, 30), times.last())
        assertEquals(18, tuesday.availableCount)
    }

    @Test
    fun todaysPastSlotsAreRemoved() {
        val today = generator().days(emptyList(), emptyList()).first()
        assertEquals(LocalTime.of(10, 30), today.localTimes().first())
        assertEquals(15, today.slots.size)
    }

    @Test
    fun aSlotStartingRightNowIsAlreadyGone() {
        val today = generator(at = monday.atTime(10, 30)).days(emptyList(), emptyList()).first()
        assertEquals(LocalTime.of(11, 0), today.localTimes().first())
    }

    @Test
    fun todayIsDroppedOnceNoSlotIsLeft() {
        val days = generator(at = monday.atTime(17, 45)).days(emptyList(), emptyList())
        assertEquals(13, days.size)
        assertEquals(monday.plusDays(1), days.first().date)
        assertFalse(days.first().isToday)
    }

    @Test
    fun aSlotBookedAtTheListingIsTakenButItsNeighboursAreFree() {
        val tuesday = monday.plusDays(1)
        val day =
            generator()
                .days(propertyBookings = listOf(range(tuesday, LocalTime.of(11, 0))), userBookings = emptyList())[1]
        val byTime = day.slots.associateBy { it.start.atZone(tunis).toLocalTime() }
        assertEquals(SlotStatus.Taken, byTime.getValue(LocalTime.of(11, 0)).status)
        // Half-open ranges: 10:30-11:00 and 11:30-12:00 only touch the booking.
        assertEquals(SlotStatus.Available, byTime.getValue(LocalTime.of(10, 30)).status)
        assertEquals(SlotStatus.Available, byTime.getValue(LocalTime.of(11, 30)).status)
        assertEquals(17, day.availableCount)
    }

    @Test
    fun theUsersOtherViewingsMakeOverlappingSlotsBusy() {
        val tuesday = monday.plusDays(1)
        val elsewhere = range(tuesday, LocalTime.of(14, 15))
        val day = generator().days(propertyBookings = emptyList(), userBookings = listOf(elsewhere))[1]
        val byTime = day.slots.associateBy { it.start.atZone(tunis).toLocalTime() }
        assertEquals(SlotStatus.Busy, byTime.getValue(LocalTime.of(14, 0)).status)
        assertEquals(SlotStatus.Busy, byTime.getValue(LocalTime.of(14, 30)).status)
        assertEquals(SlotStatus.Available, byTime.getValue(LocalTime.of(13, 30)).status)
        assertEquals(SlotStatus.Available, byTime.getValue(LocalTime.of(15, 0)).status)
    }

    @Test
    fun takenAtTheListingWinsOverBusy() {
        val tuesday = monday.plusDays(1)
        val booking = range(tuesday, LocalTime.of(9, 0))
        val day = generator().days(listOf(booking), listOf(booking))[1]
        assertEquals(SlotStatus.Taken, day.slots.first().status)
    }

    @Test
    fun aCancelledViewingNoLongerBlocksItsSlot() {
        val tuesday = monday.plusDays(1)
        val booking = range(tuesday, LocalTime.of(9, 0))
        val generator = generator()
        val start = booking.start
        assertFalse(generator.isBookable(start, listOf(booking), listOf(booking)))
        // Cancelling deletes the viewing, so it simply disappears from both lists.
        assertTrue(generator.isBookable(start, emptyList(), emptyList()))
    }

    @Test
    fun isBookableRejectsPastAndOffGridTimes() {
        val generator = generator()
        assertFalse(generator.isBookable(monday.atTime(10, 0).atZone(tunis).toInstant(), emptyList(), emptyList()))
        assertFalse(generator.isBookable(monday.atTime(10, 45).atZone(tunis).toInstant(), emptyList(), emptyList()))
        assertTrue(generator.isBookable(monday.atTime(11, 0).atZone(tunis).toInstant(), emptyList(), emptyList()))
    }

    @Test
    fun slotsFollowTheInjectedZone() {
        // 05:00 UTC is 06:00 in Tunis (whole day ahead) but 14:00 in Tokyo.
        val instant = LocalDateTime.of(2026, 10, 26, 5, 0)
        val utc = ZoneId.of("UTC")
        val now = instant.atZone(utc).toInstant()
        val tokyo = ZoneId.of("Asia/Tokyo")
        val inTunis = ViewingSlotGenerator({ now }, tunis, SlotConfig()).days(emptyList(), emptyList()).first()
        val inTokyo = ViewingSlotGenerator({ now }, tokyo, SlotConfig()).days(emptyList(), emptyList()).first()
        assertEquals(18, inTunis.slots.size)
        assertEquals(LocalTime.of(9, 0), inTunis.localTimes().first())
        assertEquals(LocalTime.of(14, 30), inTokyo.localTimes(tokyo).first())
        assertEquals(7, inTokyo.slots.size)
    }

    @Test
    fun aDaylightSavingDayStillHasDistinctRealSlots() {
        // Europe/Paris falls back on 25 October 2026 at 03:00; opening hours are unaffected.
        val paris = ZoneId.of("Europe/Paris")
        val days = generator(at = LocalDateTime.of(2026, 10, 24, 20, 0), zone = paris).days(emptyList(), emptyList())
        val sunday = days.first { it.date == LocalDate.of(2026, 10, 25) }
        assertEquals(
            18,
            sunday.slots
                .map { it.start }
                .distinct()
                .size,
        )
        assertEquals(LocalTime.of(9, 0), sunday.localTimes(paris).first())
    }

    @Test
    fun hourlySlotsWithACustomConfig() {
        val config = SlotConfig(stepMinutes = 60, durationMinutes = 60, horizonDays = 3)
        val days = generator(config = config).days(emptyList(), emptyList())
        assertEquals(3, days.size)
        assertEquals((9..17).map { LocalTime.of(it, 0) }, days[1].localTimes())
        assertEquals(LocalTime.of(11, 0), days[0].localTimes().first())
    }
}
