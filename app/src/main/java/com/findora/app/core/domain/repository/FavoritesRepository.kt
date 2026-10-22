package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.Property
import kotlinx.coroutines.flow.Flow

/** Favourites are stored per user, so guests and signed-in accounts never see each other's lists. */
interface FavoritesRepository {
    fun observeFavoriteIds(userId: String): Flow<Set<String>>

    fun observeFavorites(userId: String): Flow<List<Property>>

    /** Adds or removes [propertyId]; returns true when it is a favourite afterwards. */
    suspend fun toggle(
        userId: String,
        propertyId: String,
    ): Boolean
}
