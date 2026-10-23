package com.findora.app.feature.savedsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import com.findora.app.core.domain.repository.SearchAlertScheduler
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A saved search as listed: [regionName] is the region it searches ("" when the region no longer exists). */
data class SavedSearchItem(
    val search: SavedSearch,
    val regionName: String,
)

data class SavedSearchesUiState(
    val isLoading: Boolean = true,
    val items: List<SavedSearchItem> = emptyList(),
)

sealed interface SavedSearchesUiEvent {
    /** Show the search's results on Home. */
    data class Run(
        val id: Long,
    ) : SavedSearchesUiEvent

    data class Rename(
        val id: Long,
        val name: String,
    ) : SavedSearchesUiEvent

    data class Delete(
        val id: Long,
    ) : SavedSearchesUiEvent

    data class SetAlerts(
        val id: Long,
        val enabled: Boolean,
    ) : SavedSearchesUiEvent

    data class SetFrequency(
        val id: Long,
        val frequency: AlertFrequency,
    ) : SavedSearchesUiEvent
}

sealed interface SavedSearchesUiEffect {
    /** The search was applied to the Home feed: show Home. */
    data object ShowResults : SavedSearchesUiEffect
}

/** The current user's saved searches: run, rename, delete and alert settings. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedSearchesViewModel
    @Inject
    constructor(
        private val savedSearches: SavedSearchRepository,
        private val filters: HomeFilterRepository,
        private val currentRegion: CurrentRegion,
        private val scheduler: SearchAlertScheduler,
        auth: AuthRepository,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        val state: StateFlow<SavedSearchesUiState> =
            combine(
                userId.flatMapLatest { savedSearches.observeSavedSearches(it) },
                flow { emit(currentRegion.all().associate { it.id to it.name }) },
            ) { searches, regionNames ->
                SavedSearchesUiState(
                    isLoading = false,
                    items = searches.map { SavedSearchItem(it, regionNames[it.regionId].orEmpty()) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SavedSearchesUiState())

        private val effectChannel = Channel<SavedSearchesUiEffect>(Channel.BUFFERED)
        val effects: Flow<SavedSearchesUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SavedSearchesUiEvent) {
            viewModelScope.launch {
                val user = userId.first()
                when (event) {
                    is SavedSearchesUiEvent.Run -> run(user, event.id)
                    is SavedSearchesUiEvent.Rename -> savedSearches.rename(user, event.id, event.name)
                    is SavedSearchesUiEvent.Delete -> savedSearches.delete(user, event.id)
                    is SavedSearchesUiEvent.SetAlerts -> {
                        savedSearches.setAlertsEnabled(user, event.id, event.enabled)
                        if (event.enabled) scheduler.ensureScheduled()
                    }
                    is SavedSearchesUiEvent.SetFrequency -> savedSearches.setFrequency(user, event.id, event.frequency)
                }
            }
        }

        private suspend fun run(
            user: String,
            id: Long,
        ) {
            val search = savedSearches.savedSearch(user, id) ?: return
            applySavedSearch(search, filters, currentRegion)
            effectChannel.send(SavedSearchesUiEffect.ShowResults)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** Puts [search] on the Home feed: its region (when it still exists) and its filter. */
internal suspend fun applySavedSearch(
    search: SavedSearch,
    filters: HomeFilterRepository,
    currentRegion: CurrentRegion,
) {
    val region = search.regionId
    if (region.isNotBlank() && currentRegion.all().any { it.id == region }) currentRegion.select(region)
    filters.update { search.filter }
}
