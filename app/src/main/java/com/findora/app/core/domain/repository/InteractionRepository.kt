package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.InteractionEvent
import kotlinx.coroutines.flow.Flow

interface InteractionRepository {
    suspend fun record(event: InteractionEvent)

    fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEvent>>
}
