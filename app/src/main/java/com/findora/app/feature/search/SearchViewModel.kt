package com.findora.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.search.NaturalLanguageQueryParser
import com.findora.app.core.domain.search.ParsedIntent
import com.findora.app.core.domain.search.QueryMeaning
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Places" looks up neighbourhoods and listing names; "Ask" reads the text as a sentence describing a home. */
enum class SearchMode { Places, Ask }

/** What Ask Findora understood from the sentence, ready to show as removable chips. */
data class AskUiState(
    /** Null while the field is empty. */
    val intent: ParsedIntent? = null,
    /** The region the result applies to: the one the sentence points at, else the current one. */
    val regionId: String = "",
    val regionName: String = "",
    val regionCurrency: Currency = Currency.INR,
    val switchesRegion: Boolean = false,
    val hasProfile: Boolean = false,
) {
    /** Budgets are compared with listing rents, which are priced in the region's currency. */
    val currencyMismatch: Boolean get() = intent?.budget?.currency?.let { it != regionCurrency } == true

    val canApply: Boolean get() = intent?.isEmpty == false

    /** The order Apply uses: the one the sentence asked for, else Best match once a preference profile exists. */
    val appliedSort: SortOrder
        get() = intent?.sort ?: if (hasProfile) SortOrder.BestMatch else SortOrder.Recommended

    /** Lifestyle words were found but there is no profile for Best match to weigh them with. */
    val lifestyleNeedsProfile: Boolean get() = !hasProfile && intent?.lifestyle?.isNotEmpty() == true
}

data class SearchUiState(
    val query: String = "",
    val suggestions: List<SearchSuggestion> = emptyList(),
    val recents: List<RecentSearch> = emptyList(),
    /** True once suggestions for the current (debounced) text have been looked up. */
    val suggestionsLoaded: Boolean = false,
    val mode: SearchMode = SearchMode.Places,
    val ask: AskUiState = AskUiState(),
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

    data class SetMode(
        val mode: SearchMode,
    ) : SearchUiEvent

    /** Text from voice input: always read as an Ask Findora sentence. */
    data class Dictated(
        val text: String,
    ) : SearchUiEvent

    /** Drop one understood chip from the current sentence. */
    data class RemoveMeaning(
        val meaning: QueryMeaning,
    ) : SearchUiEvent

    /** Apply what Ask Findora understood to the Home feed (or search the words when nothing was understood). */
    data object ApplyAsk : SearchUiEvent
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
        private val profiles: MatchProfileRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow(filters.filter.value.query)
        private val mode = MutableStateFlow(SearchMode.Places)
        private val removed = MutableStateFlow<Set<QueryMeaning>>(emptySet())
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        private val ask: Flow<AskUiState> =
            combine(
                query,
                removed,
                flow { emit(currentRegion.all()) },
                currentRegion.region,
                userId.flatMapLatest { profiles.observe(it) }.map { it != null }.distinctUntilChanged(),
            ) { text, gone, all, current, hasProfile -> interpret(text, gone, all, current, hasProfile) }

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
                mode,
                ask,
            ) { text, (forText, found), recents, searchMode, askState ->
                val current = text.trim() == forText
                SearchUiState(
                    query = text,
                    suggestions = if (current) found else emptyList(),
                    recents = recents,
                    suggestionsLoaded = current && forText.isNotEmpty(),
                    mode = searchMode,
                    ask = askState,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                SearchUiState(query = query.value),
            )

        private val effectChannel = Channel<SearchUiEffect>(Channel.BUFFERED)
        val effects: Flow<SearchUiEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: SearchUiEvent) {
            when (event) {
                is SearchUiEvent.QueryChanged -> changeQuery(event.query)
                is SearchUiEvent.Submit -> submit(event.query)
                is SearchUiEvent.DeleteRecent -> viewModelScope.launch { history.delete(userId.first(), event.query) }
                SearchUiEvent.ClearRecents -> viewModelScope.launch { history.clear(userId.first()) }
                is SearchUiEvent.SetMode -> mode.value = event.mode
                is SearchUiEvent.Dictated -> {
                    mode.value = SearchMode.Ask
                    changeQuery(event.text)
                }
                is SearchUiEvent.RemoveMeaning -> removed.value += event.meaning
                SearchUiEvent.ApplyAsk -> applyAsk()
            }
        }

        private fun changeQuery(text: String) {
            if (text != query.value) removed.value = emptySet()
            query.value = text
        }

        /**
         * Replaces the Home filter with what the sentence describes, switching region when it names a place (or a
         * currency) of another one. Without a sort word the feed is ordered by Best match once the user has a
         * preference profile. When nothing was understood the words are searched as plain text instead.
         */
        private fun applyAsk() {
            viewModelScope.launch {
                val text = query.value.trim()
                val user = userId.first()
                val ask =
                    interpret(
                        text,
                        removed.value,
                        currentRegion.all(),
                        currentRegion.region.first(),
                        profiles.observe(user).first() != null,
                    )
                val intent = ask.intent
                if (intent == null || intent.isEmpty) {
                    submit(text)
                } else {
                    history.record(user, text)
                    if (ask.switchesRegion) currentRegion.select(ask.regionId)
                    filters.update { intent.filter.copy(sort = ask.appliedSort) }
                    effectChannel.send(SearchUiEffect.Applied)
                }
            }
        }

        private fun interpret(
            text: String,
            removed: Set<QueryMeaning>,
            regions: List<RegionPack>,
            current: RegionPack,
            hasProfile: Boolean,
        ): AskUiState {
            val intent =
                text
                    .takeIf { it.isNotBlank() }
                    ?.let { NaturalLanguageQueryParser.parse(it, regions) }
                    ?.let { parsed -> removed.fold(parsed) { acc, meaning -> acc.without(meaning) } }
            val target = intent?.regionId?.let { id -> regions.firstOrNull { it.id == id } } ?: current
            return AskUiState(
                intent = intent,
                regionId = target.id,
                regionName = target.name,
                regionCurrency = target.currency,
                switchesRegion = target.id != current.id,
                hasProfile = hasProfile,
            )
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
