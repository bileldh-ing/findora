package com.findora.app.core.domain.repository

import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.compare.CompareSelection
import kotlinx.coroutines.flow.Flow

/** The listings a user picked to compare side by side (at most [CompareSelection.MAX_ITEMS]), in column order. */
interface CompareRepository {
    fun observe(userId: String): Flow<List<String>>

    suspend fun add(
        userId: String,
        propertyId: String,
    ): CompareAddResult

    suspend fun remove(
        userId: String,
        propertyId: String,
    )

    /** Moves the column at [fromIndex] to [toIndex] (both clamped to the selection). */
    suspend fun move(
        userId: String,
        fromIndex: Int,
        toIndex: Int,
    )

    suspend fun clear(userId: String)
}
