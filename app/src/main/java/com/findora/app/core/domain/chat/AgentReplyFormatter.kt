package com.findora.app.core.domain.chat

import com.findora.app.core.domain.format.DisplayPreferences
import java.time.ZoneId

/** Turns the engine's structured reply into the text stored in the thread (localized by the data layer). */
interface AgentReplyFormatter {
    /** [display] decides the currency and area unit the reply quotes (the user's Settings). */
    fun format(
        reply: AgentReply,
        zone: ZoneId,
        display: DisplayPreferences = DisplayPreferences(),
    ): String

    /** What the agent says when the listing has left the app, so the rules have nothing to answer from. */
    fun listingGone(): String
}
