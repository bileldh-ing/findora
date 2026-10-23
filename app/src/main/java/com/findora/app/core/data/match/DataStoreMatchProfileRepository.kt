package com.findora.app.core.data.match

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.repository.MatchProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profiles live in the app's Preferences DataStore as one versioned JSON document per user
 * (`match_profile_<userId>`). A profile is small, always read and written whole and never queried or joined,
 * so a Room table would add a schema version and a migration for no benefit (see README, Best Match engine).
 * An unreadable document reads as "no profile" so the quiz is offered again instead of crashing.
 */
@Singleton
class DataStoreMatchProfileRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val json: Json,
    ) : MatchProfileRepository {
        override fun observe(userId: String): Flow<PreferenceProfile?> {
            val key = key(userId)
            return dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { prefs -> prefs[key]?.let(::decode) }
                .distinctUntilChanged()
        }

        override suspend fun save(
            userId: String,
            profile: PreferenceProfile,
        ) {
            val encoded = json.encodeToString(PreferenceProfileDto.serializer(), PreferenceProfileDto.from(profile))
            dataStore.edit { it[key(userId)] = encoded }
        }

        override suspend fun clear(userId: String) {
            dataStore.edit { it.remove(key(userId)) }
        }

        private fun decode(text: String): PreferenceProfile? =
            try {
                json.decodeFromString(PreferenceProfileDto.serializer(), text).toDomain()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun key(userId: String): Preferences.Key<String> {
            require(userId.isNotBlank()) { "userId must not be blank" }
            return stringPreferencesKey("$KEY_PREFIX$userId")
        }

        private companion object {
            const val KEY_PREFIX = "match_profile_"
        }
    }
