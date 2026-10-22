package com.findora.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local account. [email] is stored lower-cased and unique; the password is only ever a salted hash. */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "password_salt") val passwordSalt: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)

/** A favourite belongs to one user; no foreign key so favourites survive a cache refresh. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["user_id", "property_id"],
    indices = [Index(value = ["property_id"]), Index(value = ["user_id", "created_at"])],
)
data class FavoriteEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation_id", "sent_at"]), Index(value = ["recipient_id", "read_at"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "recipient_id") val recipientId: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAtMillis: Long,
    @ColumnInfo(name = "read_at") val readAtMillis: Long?,
)

@Entity(tableName = "saved_searches", indices = [Index(value = ["user_id", "created_at"])])
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val query: String,
    @ColumnInfo(name = "filter_json") val filterJson: String,
    @ColumnInfo(name = "alerts_enabled") val alertsEnabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "last_notified_at") val lastNotifiedAtMillis: Long?,
)

/** A booked viewing. The unique (property, start) index makes double booking impossible. */
@Entity(
    tableName = "viewings",
    indices = [Index(value = ["property_id", "start_at"], unique = true), Index(value = ["user_id", "start_at"])],
)
data class ViewingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "start_at") val startAtMillis: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val status: String,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
)

@Entity(
    tableName = "recent_searches",
    indices = [
        Index(
            value = ["user_id", "normalized_query"],
            unique = true,
        ), Index(value = ["user_id", "searched_at"]),
    ],
)
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    val query: String,
    @ColumnInfo(name = "normalized_query") val normalizedQuery: String,
    @ColumnInfo(name = "searched_at") val searchedAtMillis: Long,
)

@Entity(
    tableName = "interaction_events",
    indices = [Index(value = ["user_id", "occurred_at"]), Index(value = ["property_id"])],
)
data class InteractionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    val type: String,
    @ColumnInfo(name = "occurred_at") val occurredAtMillis: Long,
    @ColumnInfo(name = "dwell_millis") val dwellMillis: Long,
)

/** Failed sign-in attempts per normalized e-mail, persisted so a lockout survives an app restart. */
@Entity(tableName = "login_attempts")
data class LoginAttemptEntity(
    @PrimaryKey val email: String,
    @ColumnInfo(name = "failed_count") val failedCount: Int,
    @ColumnInfo(name = "locked_until") val lockedUntilMillis: Long?,
)
