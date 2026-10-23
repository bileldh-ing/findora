package com.findora.app.core.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.findora.app.core.data.local.entity.OwnerListingEntity
import com.findora.app.core.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

/** A (listing, user, time) triple read from favourites, chats, viewings or interaction events. */
data class ListingSignalRow(
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "at") val atMillis: Long,
)

/** A chat thread about one of the owner's listings, with its last message. */
data class OwnerEnquiryRow(
    @ColumnInfo(name = "id") val conversationId: String,
    @ColumnInfo(name = "property_id") val propertyId: String,
    @ColumnInfo(name = "property_title") val propertyTitle: String,
    @ColumnInfo(name = "message_body") val lastMessage: String?,
    @ColumnInfo(name = "message_sent_at") val lastMessageAtMillis: Long?,
)

/** Owner listings and the activity of other users on them (owner dashboard). */
@Suppress("TooManyFunctions") // One query per table the dashboard reads.
@Dao
abstract class OwnerListingDao {
    @Query("SELECT * FROM owner_listings WHERE owner_id = :ownerId ORDER BY updated_at DESC, id ASC")
    abstract fun observeForOwner(ownerId: String): Flow<List<OwnerListingEntity>>

    @Query("SELECT * FROM owner_listings WHERE id = :id AND owner_id = :ownerId")
    abstract fun observe(
        id: String,
        ownerId: String,
    ): Flow<OwnerListingEntity?>

    @Query("SELECT * FROM owner_listings WHERE id = :id AND owner_id = :ownerId")
    abstract suspend fun find(
        id: String,
        ownerId: String,
    ): OwnerListingEntity?

    @Query("SELECT id FROM owner_listings WHERE owner_id = :ownerId")
    abstract suspend fun idsForOwner(ownerId: String): List<String>

    @Insert
    abstract suspend fun insert(listing: OwnerListingEntity)

    @Update
    abstract suspend fun update(listing: OwnerListingEntity): Int

    @Query("DELETE FROM owner_listings WHERE id = :id AND owner_id = :ownerId")
    protected abstract suspend fun deleteRow(
        id: String,
        ownerId: String,
    ): Int

    @Query("DELETE FROM properties WHERE id = :id AND owner_id = :ownerId")
    protected abstract suspend fun deleteLiveCopy(
        id: String,
        ownerId: String,
    ): Int

    @Upsert
    protected abstract suspend fun upsertLiveCopy(property: PropertyEntity)

    /** Saves [listing] and makes [live] its public copy in one transaction (publish / resume). */
    @Transaction
    open suspend fun publish(
        listing: OwnerListingEntity,
        live: PropertyEntity,
    ) {
        update(listing)
        upsertLiveCopy(live)
    }

    /** Saves [listing] and removes its public copy (pause / unpublish). */
    @Transaction
    open suspend fun takeOffMarket(listing: OwnerListingEntity) {
        update(listing)
        deleteLiveCopy(listing.id, listing.ownerId)
    }

    /** Deletes the listing and its public copy. Returns false when it does not belong to [ownerId]. */
    @Transaction
    open suspend fun delete(
        id: String,
        ownerId: String,
    ): Boolean {
        deleteLiveCopy(id, ownerId)
        return deleteRow(id, ownerId) > 0
    }

    @Query(
        "SELECT property_id, user_id, occurred_at AS at FROM interaction_events " +
            "WHERE type = 'viewed' AND user_id != :ownerId AND property_id IN (:ids)",
    )
    abstract fun observeViews(
        ownerId: String,
        ids: List<String>,
    ): Flow<List<ListingSignalRow>>

    @Query(
        "SELECT property_id, user_id, created_at AS at FROM favorites " +
            "WHERE user_id != :ownerId AND property_id IN (:ids)",
    )
    abstract fun observeSaves(
        ownerId: String,
        ids: List<String>,
    ): Flow<List<ListingSignalRow>>

    @Query(
        "SELECT property_id, user_id, created_at AS at FROM conversations " +
            "WHERE user_id != :ownerId AND property_id IN (:ids)",
    )
    abstract fun observeChats(
        ownerId: String,
        ids: List<String>,
    ): Flow<List<ListingSignalRow>>

    @Query(
        "SELECT property_id, user_id, created_at AS at FROM viewings " +
            "WHERE user_id != :ownerId AND property_id IN (:ids)",
    )
    abstract fun observeViewingRequests(
        ownerId: String,
        ids: List<String>,
    ): Flow<List<ListingSignalRow>>

    @Query(
        """
        SELECT c.id, c.property_id, c.property_title, m.body AS message_body, m.sent_at AS message_sent_at
        FROM conversations c
        JOIN owner_listings o ON o.id = c.property_id AND o.owner_id = :ownerId
        LEFT JOIN messages m ON m.id = (
            SELECT l.id FROM messages l WHERE l.conversation_id = c.id ORDER BY l.sent_at DESC, l.rowid DESC LIMIT 1
        )
        WHERE c.user_id != :ownerId
        ORDER BY m.sent_at DESC, c.created_at DESC
        """,
    )
    abstract fun observeEnquiries(ownerId: String): Flow<List<OwnerEnquiryRow>>
}
