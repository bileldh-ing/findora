package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import kotlinx.coroutines.flow.Flow

interface InteractionRepository {
    suspend fun record(event: InteractionEvent)

    /** Deletes the most recent stored copy of [event] (same user, listing, type and time); used by Undo. */
    suspend fun remove(event: InteractionEvent)

    fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEvent>>

    /** Ids of the listings the user has an event of one of [types] about, most recent first, each once. */
    fun observePropertyIds(
        userId: String,
        types: Set<InteractionType>,
    ): Flow<List<String>>
}
