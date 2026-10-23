package com.findora.app.core.data.owner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.findora.app.core.domain.owner.OwnerModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Owner mode, kept in the settings DataStore next to the other preferences. */
@Singleton
class DataStoreOwnerModeRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : OwnerModeRepository {
        override val ownerMode: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }.distinctUntilChanged()

        override suspend fun setOwnerMode(enabled: Boolean) {
            dataStore.edit { it[KEY] = enabled }
        }

        private companion object {
            val KEY = booleanPreferencesKey("owner_mode")
        }
    }
