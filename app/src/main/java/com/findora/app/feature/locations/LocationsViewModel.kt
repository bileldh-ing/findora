package com.findora.app.feature.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationsUiState(
    val regions: List<RegionPack> = emptyList(),
    val currentRegionId: String? = null,
)

sealed interface LocationsUiEffect {
    /** A region or neighbourhood was chosen; show it on Home. */
    data object ShowHome : LocationsUiEffect
}

/**
 * Minimal Locations tab until the map arrives: pick a region pack, or a neighbourhood, which also becomes
 * the Home search text.
 */
@HiltViewModel
class LocationsViewModel
    @Inject
    constructor(
        private val currentRegion: CurrentRegion,
        private val filters: HomeFilterRepository,
    ) : ViewModel() {
        private val regions = MutableStateFlow<List<RegionPack>>(emptyList())

        val state: StateFlow<LocationsUiState> =
            combine(regions, currentRegion.region) { all, current -> LocationsUiState(all, current.id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LocationsUiState())

        private val effectChannel = Channel<LocationsUiEffect>(Channel.BUFFERED)
        val effects: Flow<LocationsUiEffect> = effectChannel.receiveAsFlow()

        init {
            viewModelScope.launch { regions.value = currentRegion.all() }
        }

        fun selectRegion(regionId: String) =
            choose {
                currentRegion.select(regionId)
                filters.update { it.copy(query = "") }
            }

        fun selectLocality(
            regionId: String,
            locality: String,
        ) = choose {
            currentRegion.select(regionId)
            filters.update { it.copy(query = locality) }
        }

        private fun choose(block: suspend () -> Unit) {
            viewModelScope.launch {
                block()
                effectChannel.send(LocationsUiEffect.ShowHome)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
