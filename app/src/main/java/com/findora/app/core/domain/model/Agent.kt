package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

/** Listing agent. Agents are shown with an initials avatar, never a photo of a person. */
@Immutable
data class Agent(
    val id: String,
    val name: String,
    val phone: String,
    val avatar: InitialsAvatar,
    val responseTimeMinutes: Int,
) {
    /**
     * Contact address for the listing enquiry. Seed agents are fictional, so the address lives on the
     * reserved `.example` domain (RFC 2606) and can never reach a real person.
     */
    val email: String get() = "$id@$SAMPLE_EMAIL_DOMAIN"

    companion object {
        const val SAMPLE_EMAIL_DOMAIN = "agents.findora.example"
    }
}

/** Generated avatar: up to two letters on a solid background colour (ARGB). */
@Immutable
data class InitialsAvatar(
    val initials: String,
    val backgroundArgb: Long,
)
