package com.findora.app.core.data.chat

import android.content.Context
import com.findora.app.R
import com.findora.app.core.domain.chat.AgentReply
import com.findora.app.core.domain.chat.AgentReplyFormatter
import com.findora.app.core.domain.chat.PriceFlexibility
import com.findora.app.core.domain.chat.ReplyPart
import com.findora.app.core.domain.chat.VisitWindow
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.locale.LocalizedContext
import com.findora.app.core.locale.areaText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/**
 * Writes the agent's reply in the app's current language from string resources, one sentence per part. The
 * text is stored as sent, like a real message, so it keeps the language it was written in.
 */
class ResourceAgentReplyFormatter
    @Inject
    constructor(
        private val localized: LocalizedContext,
    ) : AgentReplyFormatter {
        override fun format(
            reply: AgentReply,
            zone: ZoneId,
            display: DisplayPreferences,
        ): String = Writer(localized.get(), display).write(reply, zone)

        override fun listingGone(): String = localized.get().getString(R.string.agent_reply_listing_gone)

        /** One reply's writer: the language is resolved once, so every sentence of a message matches. */
        private class Writer(
            private val context: Context,
            private val display: DisplayPreferences,
        ) {
            fun write(
                reply: AgentReply,
                zone: ZoneId,
            ): String = reply.parts.joinToString(separator = " ") { sentence(it, zone) }

            private val locale: Locale get() = context.resources.configuration.locales[0]

            private fun sentence(
                part: ReplyPart,
                zone: ZoneId,
            ): String =
                when (part) {
                    is ReplyPart.Greeting ->
                        context.getString(
                            R.string.agent_reply_greeting,
                            part.agentName,
                            part.propertyTitle,
                        )
                    is ReplyPart.Availability -> availability(part)
                    is ReplyPart.ViewingTimes -> viewingTimes(part, zone)
                    is ReplyPart.Price -> price(part)
                    is ReplyPart.Pets ->
                        context.getString(
                            if (part.allowed) R.string.agent_reply_pets_yes else R.string.agent_reply_pets_no,
                        )
                    is ReplyPart.Parking ->
                        context.getString(
                            if (part.available) R.string.agent_reply_parking_yes else R.string.agent_reply_parking_no,
                        )
                    is ReplyPart.Furnished ->
                        context.getString(
                            if (part.furnished) {
                                R.string.agent_reply_furnished_yes
                            } else {
                                R.string.agent_reply_furnished_no
                            },
                        )
                    is ReplyPart.Size -> size(part)
                    ReplyPart.Thanks -> context.getString(R.string.agent_reply_thanks)
                    ReplyPart.Help -> context.getString(R.string.agent_reply_help)
                }

            private fun availability(part: ReplyPart.Availability): String {
                val available = context.getString(R.string.agent_reply_available, part.propertyTitle)
                val listed =
                    if (part.listedDays < 1) {
                        context.getString(R.string.agent_reply_listed_today)
                    } else {
                        val days = part.listedDays.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        context.resources.getQuantityString(R.plurals.agent_reply_listed_days, days, days)
                    }
                return "$available $listed"
            }

            private fun viewingTimes(
                part: ReplyPart.ViewingTimes,
                zone: ZoneId,
            ): String {
                if (part.slots.isEmpty()) return context.getString(R.string.agent_reply_no_slots)
                val list =
                    part.slots.joinToString(
                        context.getString(R.string.agent_reply_list_separator),
                    ) { slot(it, zone) }
                val offer =
                    if (part.window == VisitWindow.Tomorrow) {
                        context.getString(R.string.agent_reply_slots_tomorrow, list)
                    } else {
                        context.getString(R.string.agent_reply_slots, list)
                    }
                return "$offer ${context.getString(R.string.agent_reply_book_hint)}"
            }

            private fun slot(
                start: Instant,
                zone: ZoneId,
            ): String {
                val dateTime = start.atZone(zone)
                val day = DateTimeFormatter.ofPattern(DAY_PATTERN, locale).format(dateTime)
                val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dateTime)
                return context.getString(R.string.agent_reply_slot, day, time)
            }

            private fun price(part: ReplyPart.Price): String {
                val rent = PriceFormatter.formatDisplay(display.display(part.rent), locale)
                val res =
                    when (part.flexibility) {
                        PriceFlexibility.RecentlyReduced -> R.string.agent_reply_price_reduced
                        PriceFlexibility.OpenToOffers -> R.string.agent_reply_price_offers
                        PriceFlexibility.Firm -> R.string.agent_reply_price_firm
                    }
                return context.getString(res, rent)
            }

            private fun size(part: ReplyPart.Size): String {
                val resources = context.resources
                return context.getString(
                    R.string.agent_reply_size,
                    resources.getQuantityString(R.plurals.agent_reply_bedrooms, part.bedrooms, part.bedrooms),
                    resources.getQuantityString(R.plurals.agent_reply_bathrooms, part.bathrooms, part.bathrooms),
                    areaText(context, part.areaSqm, display.areaUnit, locale),
                )
            }
        }

        private companion object {
            const val DAY_PATTERN = "EEE d MMM"
        }
    }
