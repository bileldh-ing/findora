package com.findora.app.core.domain.chat

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.viewing.ViewingDay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** How much room there is on the rent, derived from the listing's own price history and age. */
enum class PriceFlexibility {
    /** The latest month is cheaper than an earlier one: already reduced, so the agent holds the price. */
    RecentlyReduced,

    /** Listed for a while without a reduction: the owner may consider an offer for a longer lease. */
    OpenToOffers,

    /** A fresh listing at its first price. */
    Firm,
}

/** One sentence of the agent's answer. The data layer turns each part into localized text. */
sealed interface ReplyPart {
    data class Greeting(
        val agentName: String,
        val propertyTitle: String,
    ) : ReplyPart

    data class Availability(
        val propertyTitle: String,
        val listedDays: Long,
    ) : ReplyPart

    /** Up to [AgentReplyEngine.MAX_SLOTS] open viewing times, soonest first; empty when none are left. */
    data class ViewingTimes(
        val slots: List<Instant>,
        val window: VisitWindow,
    ) : ReplyPart

    data class Price(
        val rent: Money,
        val flexibility: PriceFlexibility,
    ) : ReplyPart

    data class Pets(
        val allowed: Boolean,
    ) : ReplyPart

    data class Parking(
        val available: Boolean,
    ) : ReplyPart

    data class Furnished(
        val furnished: Boolean,
    ) : ReplyPart

    data class Size(
        val bedrooms: Int,
        val bathrooms: Int,
        val areaSqm: Int,
    ) : ReplyPart

    data object Thanks : ReplyPart

    /** Nothing recognised: the agent lists what it can answer. */
    data object Help : ReplyPart
}

@Immutable
data class AgentReply(
    val parts: List<ReplyPart>,
) {
    init {
        require(parts.isNotEmpty()) { "a reply says something" }
    }
}

/** Everything the agent may use to answer: the message, the listing and the bookable calendar. */
data class AgentReplyRequest(
    val message: String,
    val property: Property,
    /** From [com.findora.app.core.domain.viewing.ViewingSlotGenerator], so taken and busy slots are never offered. */
    val days: List<ViewingDay>,
    val now: Instant,
    val zone: ZoneId,
    /** True when the agent has not answered in this conversation yet, so it introduces itself. */
    val firstReply: Boolean,
)

/**
 * The demo agent: deterministic, rule-based answers built only from the listing's data (availability, rent and
 * its history, viewing slots, pets, parking, furnishing, size). The same request always gets the same reply, and
 * nothing is invented that the listing does not say.
 */
class AgentReplyEngine
    @Inject
    constructor() {
        fun reply(request: AgentReplyRequest): AgentReply {
            val topics = AgentTopics.topics(request.message)
            val property = request.property
            val answers =
                ANSWER_ORDER.filter { it in topics }.map { topic -> answer(topic, request) }
            val parts = mutableListOf<ReplyPart>()
            if (request.firstReply || (AgentTopic.Greeting in topics && answers.isEmpty())) {
                parts += ReplyPart.Greeting(property.agent.name, property.title)
            }
            parts += answers
            when {
                answers.isNotEmpty() -> Unit
                AgentTopic.Thanks in topics -> parts += ReplyPart.Thanks
                else -> parts += ReplyPart.Help
            }
            return AgentReply(parts)
        }

        private fun answer(
            topic: AgentTopic,
            request: AgentReplyRequest,
        ): ReplyPart {
            val property = request.property
            return when (topic) {
                AgentTopic.Availability ->
                    ReplyPart.Availability(property.title, listedDays(property, request.now))
                AgentTopic.Viewing -> viewingTimes(request)
                AgentTopic.Price -> ReplyPart.Price(property.rent, flexibility(property, request.now))
                AgentTopic.Pets -> ReplyPart.Pets(property.petFriendly)
                AgentTopic.Parking -> ReplyPart.Parking(property.parking)
                AgentTopic.Furnished -> ReplyPart.Furnished(property.furnished)
                AgentTopic.Size -> ReplyPart.Size(property.bedrooms, property.bathrooms, property.areaSqm)
                AgentTopic.Greeting, AgentTopic.Thanks -> error("not an answer topic: $topic")
            }
        }

        private fun viewingTimes(request: AgentReplyRequest): ReplyPart.ViewingTimes {
            val window = AgentTopics.visitWindow(request.message)
            val today = request.now.atZone(request.zone).toLocalDate()
            val days =
                request.days.filter { day ->
                    when (window) {
                        VisitWindow.Tomorrow -> day.date == today.plusDays(1)
                        VisitWindow.ThisWeek -> day.date.isBefore(today.plusDays(WEEK_DAYS))
                        VisitWindow.Any -> true
                    }
                }
            val open = days.map { day -> day.slots.filter { it.isAvailable && it.start.isAfter(request.now) } }
            val slots =
                if (window == VisitWindow.Tomorrow) {
                    spacedSlots(open.flatten().map { it.start })
                } else {
                    // One time per day reads like a person offering options rather than a calendar dump.
                    open.mapNotNull { it.firstOrNull()?.start }.take(MAX_SLOTS)
                }
            return ReplyPart.ViewingTimes(slots, window)
        }

        private fun spacedSlots(starts: List<Instant>): List<Instant> {
            val picked = mutableListOf<Instant>()
            starts.sorted().forEach { start ->
                if (picked.size < MAX_SLOTS && picked.all { Duration.between(it, start).abs() >= SAME_DAY_GAP }) {
                    picked += start
                }
            }
            return picked
        }

        private fun listedDays(
            property: Property,
            now: Instant,
        ): Long = Duration.between(property.createdAt, now).toDays().coerceAtLeast(0)

        private fun flexibility(
            property: Property,
            now: Instant,
        ): PriceFlexibility {
            val history = property.priceHistory.sortedBy { it.month }
            val latest = history.lastOrNull()?.amount
            val reduced = latest != null && history.dropLast(1).any { it.amount > latest }
            return when {
                reduced -> PriceFlexibility.RecentlyReduced
                listedDays(property, now) >= OFFER_AFTER_DAYS -> PriceFlexibility.OpenToOffers
                else -> PriceFlexibility.Firm
            }
        }

        /** Typing time for a reply: longer answers take longer to "type", within human-looking bounds. */
        fun typingDelay(reply: AgentReply): Duration =
            Duration.ofMillis(
                (BASE_TYPING_MILLIS + reply.parts.size * PER_PART_MILLIS).coerceAtMost(MAX_TYPING_MILLIS),
            )

        companion object {
            const val MAX_SLOTS = 3
            const val OFFER_AFTER_DAYS = 45L
            private const val WEEK_DAYS = 7L
            private val SAME_DAY_GAP: Duration = Duration.ofMinutes(90)
            private const val BASE_TYPING_MILLIS = 1_200L
            private const val PER_PART_MILLIS = 700L
            private const val MAX_TYPING_MILLIS = 4_000L
            private val ANSWER_ORDER =
                listOf(
                    AgentTopic.Availability,
                    AgentTopic.Viewing,
                    AgentTopic.Price,
                    AgentTopic.Size,
                    AgentTopic.Furnished,
                    AgentTopic.Pets,
                    AgentTopic.Parking,
                )
        }
    }
