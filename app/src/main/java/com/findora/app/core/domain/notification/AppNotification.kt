package com.findora.app.core.domain.notification

import androidx.compose.runtime.Immutable
import java.time.Instant

/** What a notification is about. [id] is the stable value stored in the database. */
enum class NotificationKind(
    val id: String,
) {
    NewMessage("new_message"),
    ViewingReminder("viewing_reminder"),
    ViewingBooked("viewing_booked"),
    ;

    companion object {
        fun fromIdOrNull(id: String): NotificationKind? = entries.firstOrNull { it.id == id }
    }
}

/** Where tapping a notification goes. */
sealed interface NotificationTarget {
    data class Chat(
        val conversationId: String,
    ) : NotificationTarget

    data class Property(
        val propertyId: String,
    ) : NotificationTarget
}

/**
 * One entry of the notifications center. [subject] is the listing title (or the agent's name for a message)
 * captured when it was created; [preview] is the message text for a message and empty otherwise; [eventAt] is
 * the viewing's start for viewing notifications.
 */
@Immutable
data class AppNotification(
    val id: Long,
    val kind: NotificationKind,
    val subject: String,
    val preview: String,
    val propertyId: String,
    val conversationId: String?,
    val eventAt: Instant?,
    val createdAt: Instant,
    val readAt: Instant?,
) {
    val isRead: Boolean get() = readAt != null

    val target: NotificationTarget
        get() = conversationId?.let { NotificationTarget.Chat(it) } ?: NotificationTarget.Property(propertyId)
}

/** A notification to add; the repository stamps its id and creation time. */
data class NewNotification(
    val kind: NotificationKind,
    val subject: String,
    val propertyId: String,
    val preview: String = "",
    val conversationId: String? = null,
    val eventAt: Instant? = null,
)
