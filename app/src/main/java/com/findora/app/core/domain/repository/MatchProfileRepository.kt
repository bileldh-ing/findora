package com.findora.app.core.domain.repository

import com.findora.app.core.domain.match.PreferenceProfile
import kotlinx.coroutines.flow.Flow

/** The preference quiz answers, one profile per user (the guest has its own). */
interface MatchProfileRepository {
    /** The user's profile, or null until they finish (or skip through) the quiz. */
    fun observe(userId: String): Flow<PreferenceProfile?>

    suspend fun save(
        userId: String,
        profile: PreferenceProfile,
    )

    /** Forgets the user's profile (account deletion). */
    suspend fun clear(userId: String)
}
