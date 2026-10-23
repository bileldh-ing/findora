package com.findora.app.feature.savedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.alert.SaveSearch
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SaveSearchEffect {
    data class Saved(
        val name: String,
    ) : SaveSearchEffect
}

/** "Save this search" on Home: keeps the current Home filter and region under a name, for the current user. */
@HiltViewModel
class SaveSearchViewModel
    @Inject
    constructor(
        private val saveSearch: SaveSearch,
        private val filters: HomeFilterRepository,
        private val currentRegion: CurrentRegion,
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<SaveSearchEffect>(Channel.BUFFERED)
        val effects: Flow<SaveSearchEffect> = effectChannel.receiveAsFlow()

        fun save(name: String) {
            val clean = name.trim()
            if (clean.isEmpty()) return
            viewModelScope.launch {
                val user = auth.session.value?.userId ?: AuthRepository.GUEST_USER_ID
                val filter = filters.filter.value
                saveSearch(user, clean, filter.query, currentRegion.region.first().id, filter)
                effectChannel.send(SaveSearchEffect.Saved(clean))
            }
        }
    }
