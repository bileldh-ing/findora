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
)

/** Generated avatar: up to two letters on a solid background colour (ARGB). */
@Immutable
data class InitialsAvatar(
    val initials: String,
    val backgroundArgb: Long,
)
