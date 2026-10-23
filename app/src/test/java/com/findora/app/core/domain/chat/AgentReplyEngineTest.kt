package com.findora.app.core.domain.chat

import com.findora.app.core.domain.model.PricePoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.TimeRange
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class AgentReplyEngineTest {
    private val zone: ZoneId = ZoneId.of("Africa/Tunis")

    /** Monday 26 October 2026, 10:10 in Tunis. */
    private val now: Instant = LocalDateTime.of(2026, 10, 26, 10, 10).atZone(zone).toInstant()
    private val engine = AgentReplyEngine()
    private val generator = ViewingSlotGenerator({ now }, zone)

    private val home: Property =
        seedProperties.first().copy(
            title = "Georgie House",
            petFriendly = true,
            parking = false,
            furnished = true,
            bedrooms = 3,
            bathrooms = 2,
            areaSqm = 120,
            createdAt = now.minus(Duration.ofDays(10)),
            priceHistory =
                listOf(
                    PricePoint(YearMonth.of(2026, 9), 30_000),
                    PricePoint(YearMonth.of(2026, 10), 30_000),
                ),
        )

    private fun reply(
        message: String,
        property: Property = home,
        firstReply: Boolean = false,
        bookings: List<TimeRange> = emptyList(),
    ): AgentReply =
        engine.reply(
            AgentReplyRequest(
                message = message,
                property = property,
                days = generator.days(bookings, emptyList()),
                now = now,
                zone = zone,
                firstReply = firstReply,
            ),
        )

    private inline fun <reified T : ReplyPart> AgentReply.part(): T = parts.filterIsInstance<T>().single()

    @Test
    fun theFirstReplyIntroducesTheAgentThenAnswers() {
        val answer = reply("Is it still available?", firstReply = true)
        assertEquals(
            listOf(
                ReplyPart.Greeting(home.agent.name, "Georgie House"),
                ReplyPart.Availability("Georgie House", listedDays = 10),
            ),
            answer.parts,
        )
    }

    @Test
    fun laterRepliesDoNotGreetAgain() {
        assertEquals(listOf(ReplyPart.Availability("Georgie House", 10)), reply("Is it still available?").parts)
    }

    @Test
    fun visitThisWeekOffersOneOpenSlotPerDayWithinSevenDays() {
        val slots = reply("Can I visit this week?").part<ReplyPart.ViewingTimes>()
        assertEquals(VisitWindow.ThisWeek, slots.window)
        assertEquals(AgentReplyEngine.MAX_SLOTS, slots.slots.size)
        val days = slots.slots.map { it.atZone(zone).toLocalDate() }
        assertEquals(days.distinct(), days)
        val today = now.atZone(zone).toLocalDate()
        assertTrue(days.all { !it.isBefore(today) && it.isBefore(today.plusDays(7)) })
        assertTrue(slots.slots.all { it.isAfter(now) })
        assertEquals(slots.slots.sorted(), slots.slots)
    }

    @Test
    fun takenSlotsAreNeverOffered() {
        val first = reply("Can I visit?").part<ReplyPart.ViewingTimes>().slots.first()
        val taken = TimeRange(first, first.plus(Duration.ofMinutes(30)))
        val again = reply("Can I visit?", bookings = listOf(taken)).part<ReplyPart.ViewingTimes>().slots
        assertFalse(first in again)
        assertEquals(first.plus(Duration.ofMinutes(30)), again.first())
    }

    @Test
    fun tomorrowOffersSpacedTimesOnThatDay() {
        val slots = reply("Could I come tomorrow?").part<ReplyPart.ViewingTimes>()
        assertEquals(VisitWindow.Tomorrow, slots.window)
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        assertEquals(AgentReplyEngine.MAX_SLOTS, slots.slots.size)
        assertTrue(slots.slots.all { it.atZone(zone).toLocalDate() == tomorrow })
        slots.slots.zipWithNext().forEach { (a, b) -> assertTrue(Duration.between(a, b) >= Duration.ofMinutes(90)) }
    }

    @Test
    fun noOpenSlotsIsSaidPlainly() {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        val wholeDay =
            TimeRange(tomorrow.atStartOfDay(zone).toInstant(), tomorrow.plusDays(1).atStartOfDay(zone).toInstant())
        val slots = reply("Can I visit tomorrow?", bookings = listOf(wholeDay)).part<ReplyPart.ViewingTimes>()
        assertTrue(slots.slots.isEmpty())
    }

    @Test
    fun priceFlexibilityFollowsTheListingHistoryAndAge() {
        assertEquals(PriceFlexibility.Firm, reply("Is the price negotiable?").part<ReplyPart.Price>().flexibility)
        val reduced =
            home.copy(
                priceHistory =
                    listOf(
                        PricePoint(YearMonth.of(2026, 9), 32_000),
                        PricePoint(YearMonth.of(2026, 10), 30_000),
                    ),
            )
        assertEquals(
            PriceFlexibility.RecentlyReduced,
            reply("Is the price negotiable?", reduced).part<ReplyPart.Price>().flexibility,
        )
        val old = home.copy(createdAt = now.minus(Duration.ofDays(AgentReplyEngine.OFFER_AFTER_DAYS)))
        val answer = reply("Any discount on the rent?", old).part<ReplyPart.Price>()
        assertEquals(PriceFlexibility.OpenToOffers, answer.flexibility)
        assertEquals(home.rent, answer.rent)
    }

    @Test
    fun petsParkingFurnishingAndSizeComeFromTheListing() {
        val answer = reply("Do you allow dogs? Is there parking? Is it furnished? How many bedrooms?")
        assertEquals(
            listOf(
                ReplyPart.Size(3, 2, 120),
                ReplyPart.Furnished(true),
                ReplyPart.Pets(true),
                ReplyPart.Parking(false),
            ),
            answer.parts,
        )
        val other = home.copy(petFriendly = false, parking = true, furnished = false)
        val second = reply("pets? car? furniture?", other)
        assertEquals(listOf(ReplyPart.Furnished(false), ReplyPart.Pets(false), ReplyPart.Parking(true)), second.parts)
    }

    @Test
    fun frenchAndArabicMessagesAreUnderstood() {
        val french = reply("Bonjour, est-il toujours disponible ? Le prix est-il négociable ?")
        assertEquals(listOf(ReplyPart.Availability::class, ReplyPart.Price::class), french.parts.map { it::class })
        assertEquals(
            VisitWindow.ThisWeek,
            reply("Puis-je visiter cette semaine ?").part<ReplyPart.ViewingTimes>().window,
        )
        val arabic = reply("هل ما زال متاحاً؟ وهل السعر قابل للتفاوض؟")
        assertEquals(listOf(ReplyPart.Availability::class, ReplyPart.Price::class), arabic.parts.map { it::class })
        val visit = reply("هل يمكنني الزيارة هذا الأسبوع؟").part<ReplyPart.ViewingTimes>()
        assertEquals(VisitWindow.ThisWeek, visit.window)
        assertEquals(
            listOf(ReplyPart.Pets(true), ReplyPart.Parking(false)),
            reply("هل يسمح بالحيوانات؟ هل يوجد موقف للسيارة؟").parts,
        )
    }

    @Test
    fun unknownQuestionsGetHelpAndThanksGetThanks() {
        assertEquals(listOf(ReplyPart.Help), reply("What is the meaning of life?").parts)
        assertEquals(listOf(ReplyPart.Thanks), reply("Thank you!").parts)
        // A bare greeting is answered with an introduction and what the agent can help with.
        assertEquals(
            listOf(ReplyPart.Greeting(home.agent.name, "Georgie House"), ReplyPart.Help),
            reply("Hello").parts,
        )
        assertEquals(
            listOf(ReplyPart.Greeting(home.agent.name, "Georgie House"), ReplyPart.Help),
            reply("hmm", firstReply = true).parts,
        )
    }

    @Test
    fun wordsThatOnlyLookLikeKeywordsDoNotMatch() {
        assertEquals(listOf(ReplyPart.Help), reply("Can I pay by card? I like this category of chat").parts)
        assertTrue(AgentTopics.topics("scarlet carpet").isEmpty())
    }

    @Test
    fun repliesAreDeterministic() {
        val message = "Is it available, can I visit and is the price negotiable?"
        assertEquals(reply(message, firstReply = true), reply(message, firstReply = true))
    }

    @Test
    fun typingTimeGrowsWithTheReplyButStaysBounded() {
        val short = engine.typingDelay(AgentReply(listOf(ReplyPart.Thanks)))
        val long =
            engine.typingDelay(
                reply("available? visit? price? pets? parking? furnished? size?", firstReply = true),
            )
        assertTrue(long > short)
        assertTrue(long <= Duration.ofSeconds(4))
        assertTrue(short >= Duration.ofSeconds(1))
    }
}
