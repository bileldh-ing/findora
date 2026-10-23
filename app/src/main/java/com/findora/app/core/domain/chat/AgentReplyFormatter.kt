package com.findora.app.core.domain.chat

import java.time.ZoneId

/** Turns the engine's structured reply into the text stored in the thread (localized by the data layer). */
interface AgentReplyFormatter {
    fun format(
        reply: AgentReply,
        zone: ZoneId,
    ): String

    /** What the agent says when the listing has left the app, so the rules have nothing to answer from. */
    fun listingGone(): String
}
