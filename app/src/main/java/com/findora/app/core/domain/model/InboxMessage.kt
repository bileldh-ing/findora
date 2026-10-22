package com.findora.app.core.domain.model

import java.time.Instant

/** A message received by the user (agent replies, viewing confirmations). */
data class InboxMessage(
    val id: String,
    val propertyId: String,
    val body: String,
    val sentAt: Instant,
)
