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

    /** New listings for a saved search: [AppNotification.subject] is the search name. */
    NewMatches("new_matches"),

    /** A favourite got cheaper: [AppNotification.preview] is the old and new rent. */
    PriceDrop("price_drop"),
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

    /** Home with the saved search's filter applied. */
    data class SavedSearch(
        val searchId: Long,
    ) : NotificationTarget
}

/**
 * One entry of the notifications center. [subject] is the listing title (or the agent's name for a message, or the
 * search name for new matches) captured when it was created; [preview] is the message text for a message, the
 * newest listing's title for new matches, the old and new rent for a price drop and empty otherwise; [eventAt] is
 * the viewing's start for viewing notifications; [count] is the number of new listings for new matches.
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
    val savedSearchId: Long? = null,
    val count: Int = 0,
) {
    val isRead: Boolean get() = readAt != null

    val target: NotificationTarget
        get() =
            conversationId?.let { NotificationTarget.Chat(it) }
                ?: savedSearchId?.let { NotificationTarget.SavedSearch(it) }
                ?: NotificationTarget.Property(propertyId)
}

/** A notification to add; the repository stamps its id and creation time. */
data class NewNotification(
    val kind: NotificationKind,
    val subject: String,
    val propertyId: String,
    val preview: String = "",
    val conversationId: String? = null,
    val eventAt: Instant? = null,
    val savedSearchId: Long? = null,
    val count: Int = 0,
)
