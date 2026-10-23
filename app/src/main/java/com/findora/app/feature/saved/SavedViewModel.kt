package com.findora.app.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.InteractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedUiState(
    val isLoading: Boolean = true,
    val homes: List<Property> = emptyList(),
    /** Homes saved from Dream Match (swiped right or super-liked) that are still favourites, newest first. */
    val matches: List<Property> = emptyList(),
)

/** The current user's favourites and Dream Match matches (guests have their own, wiped with the guest session). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel
    @Inject
    constructor(
        private val favorites: FavoritesRepository,
        private val auth: AuthRepository,
        private val interactions: InteractionRepository,
    ) : ViewModel() {
        private val userId = auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()

        val state: StateFlow<SavedUiState> =
            userId
                .flatMapLatest { user ->
                    combine(favorites.observeFavorites(user), matchIds(user)) { homes, matchIds ->
                        val byId = homes.associateBy(Property::id)
                        SavedUiState(isLoading = false, homes = homes, matches = matchIds.mapNotNull(byId::get))
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SavedUiState())

        fun toggleFavorite(propertyId: String) {
            viewModelScope.launch { favorites.toggle(userId.first(), propertyId) }
        }

        private fun matchIds(user: String): Flow<List<String>> =
            interactions.observePropertyIds(user, InteractionType.DeckMatches)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
