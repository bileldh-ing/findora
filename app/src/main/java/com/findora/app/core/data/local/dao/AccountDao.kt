package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.findora.app.core.data.local.entity.LoginAttemptEntity

/** Account-level writes that span tables: sign-in throttling and account deletion (one query per table). */
@Suppress("TooManyFunctions")
@Dao
abstract class AccountDao {
    @Query("SELECT * FROM login_attempts WHERE email = :email")
    abstract suspend fun loginAttempts(email: String): LoginAttemptEntity?

    @Upsert
    abstract suspend fun upsertLoginAttempts(attempts: LoginAttemptEntity)

    @Query("DELETE FROM login_attempts WHERE email = :email")
    abstract suspend fun clearLoginAttempts(email: String)

    @Query("DELETE FROM favorites WHERE user_id = :userId")
    protected abstract suspend fun deleteFavorites(userId: String)

    @Query("DELETE FROM recent_searches WHERE user_id = :userId")
    protected abstract suspend fun deleteRecentSearches(userId: String)

    @Query("DELETE FROM saved_search_seen WHERE search_id IN (SELECT id FROM saved_searches WHERE user_id = :userId)")
    protected abstract suspend fun deleteSavedSearchSeen(userId: String)

    @Query("DELETE FROM price_watch WHERE user_id = :userId")
    protected abstract suspend fun deletePriceWatch(userId: String)

    @Query("DELETE FROM saved_searches WHERE user_id = :userId")
    protected abstract suspend fun deleteSavedSearches(userId: String)

    @Query("DELETE FROM viewings WHERE user_id = :userId")
    protected abstract suspend fun deleteViewings(userId: String)

    @Query("DELETE FROM interaction_events WHERE user_id = :userId")
    protected abstract suspend fun deleteInteractions(userId: String)

    @Query("DELETE FROM messages WHERE sender_id = :userId OR recipient_id = :userId")
    protected abstract suspend fun deleteMessages(userId: String)

    @Query("DELETE FROM conversations WHERE user_id = :userId")
    protected abstract suspend fun deleteConversations(userId: String)

    @Query("DELETE FROM notifications WHERE user_id = :userId")
    protected abstract suspend fun deleteNotifications(userId: String)

    @Query("DELETE FROM properties WHERE owner_id = :userId")
    protected abstract suspend fun deletePublishedListings(userId: String)

    @Query("DELETE FROM owner_listings WHERE owner_id = :userId")
    protected abstract suspend fun deleteOwnerListings(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    protected abstract suspend fun deleteUser(userId: String): Int

    /** Removes the user and every row keyed by their id in one transaction. Returns false if no such user. */
    @Transaction
    open suspend fun deleteAccount(
        userId: String,
        email: String,
    ): Boolean {
        deleteOwnedRows(userId)
        clearLoginAttempts(email)
        return deleteUser(userId) > 0
    }

    /** Clears data a guest created, so the next guest session starts fresh. */
    @Transaction
    open suspend fun deleteGuestData(guestId: String) {
        deleteOwnedRows(guestId)
    }

    private suspend fun deleteOwnedRows(userId: String) {
        deleteFavorites(userId)
        deleteRecentSearches(userId)
        deleteSavedSearchSeen(userId)
        deleteSavedSearches(userId)
        deletePriceWatch(userId)
        deleteViewings(userId)
        deleteInteractions(userId)
        deleteMessages(userId)
        deleteConversations(userId)
        deleteNotifications(userId)
        deletePublishedListings(userId)
        deleteOwnerListings(userId)
    }
}
