package com.findora.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val suggestions: List<SearchSuggestion> = emptyList(),
    val recents: List<RecentSearch> = emptyList(),
    /** True once suggestions for the current (debounced) text have been looked up. */
    val suggestionsLoaded: Boolean = false,
) {
    val showRecents: Boolean get() = query.isBlank()
}

sealed interface SearchUiEvent {
    data class QueryChanged(
        val query: String,
    ) : SearchUiEvent

    /** Search for [query] (keyboard search, a suggestion or a recent search). */
    data class Submit(
        val query: String,
    ) : SearchUiEvent

    data class DeleteRecent(
        val query: String,
    ) : SearchUiEvent

    data object ClearRecents : SearchUiEvent
}

sealed interface SearchUiEffect {
    /** The search was applied to the Home feed; close the screen. */
    data object Applied : SearchUiEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val properties: PropertyRepository,
        private val history: SearchHistoryRepository,
        private val filters: HomeFilterRepository,
        private val auth: AuthRepository,
        private val currentRegion: CurrentRegion,
    ) : ViewModel() {
        private val query = MutableStateFlow(filters.filter.value.query)
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        private val suggestions: Flow<Pair<String, List<SearchSuggestion>>> =
            query
                .debounce(DEBOUNCE_MILLIS)
                .map(String::trim)
                .distinctUntilChanged()
                .mapLatest { text ->
                    val regionId = currentRegion.region.first().id
                    text to if (text.isEmpty()) emptyList() else properties.suggestions(regionId, text)
                }.onStart { emit("" to emptyList()) }

        val state: StateFlow<SearchUiState> =
            combine(
                query,
                suggestions,
                userId.flatMapLatest { history.observeRecent(it) },
            ) { text, (forText, found), recents ->
                val current = text.trim() == forText
                SearchUiState(
                    query = text,
                    suggestions = if (current) found else emptyList(),
                    recents = recents,
                    suggestionsLoaded = current && forText.isNotEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState(query = query.value))

        private val effectChannel = Channel<SearchUiEffect>(Channel.BUFFERED)
        val effects: Flow<SearchUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SearchUiEvent) {
            when (event) {
                is SearchUiEvent.QueryChanged -> query.value = event.query
                is SearchUiEvent.Submit -> submit(event.query)
                is SearchUiEvent.DeleteRecent -> viewModelScope.launch { history.delete(userId.first(), event.query) }
                SearchUiEvent.ClearRecents -> viewModelScope.launch { history.clear(userId.first()) }
            }
        }

        private fun submit(raw: String) {
            val text = raw.trim()
            viewModelScope.launch {
                if (text.isNotEmpty()) history.record(userId.first(), text)
                filters.update { it.copy(query = text) }
                effectChannel.send(SearchUiEffect.Applied)
            }
        }

        private companion object {
            const val DEBOUNCE_MILLIS = 300L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
