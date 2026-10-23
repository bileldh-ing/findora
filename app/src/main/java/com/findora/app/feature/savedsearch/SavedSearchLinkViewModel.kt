package com.findora.app.feature.savedsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resolving a `findora://search/{id}` link; [Done] carries whether the search was found and applied. */
sealed interface SavedSearchLinkState {
    data object Resolving : SavedSearchLinkState

    data class Done(
        val applied: Boolean,
    ) : SavedSearchLinkState
}

/**
 * Applies the linked saved search to Home, but only for the user who saved it (the link arrives through the
 * session guard, so a session exists; another user's id or a deleted search just opens Home unchanged).
 */
@HiltViewModel
class SavedSearchLinkViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val savedSearches: SavedSearchRepository,
        private val filters: HomeFilterRepository,
        private val currentRegion: CurrentRegion,
        private val auth: AuthRepository,
    ) : ViewModel() {
        /** [FindoraRoute.SavedSearchResults.searchId], put in the handle by the navigation graph. */
        private val searchId: Long = savedStateHandle.get<Long>(SEARCH_ID_KEY) ?: NO_ID

        private val mutableState = MutableStateFlow<SavedSearchLinkState>(SavedSearchLinkState.Resolving)
        val state: StateFlow<SavedSearchLinkState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                val user = auth.session.value?.userId ?: AuthRepository.GUEST_USER_ID
                // Claim first: a different owner's leftover filter is dropped before this one is applied.
                filters.claim(user)
                val search = savedSearches.savedSearch(user, searchId)
                if (search != null) applySavedSearch(search, filters, currentRegion)
                mutableState.value = SavedSearchLinkState.Done(applied = search != null)
            }
        }

        companion object {
            const val SEARCH_ID_KEY = "searchId"
            private const val NO_ID = -1L
        }
    }
