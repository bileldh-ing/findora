package com.findora.app.feature.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.insight.NeighbourhoodScore
import com.findora.app.core.domain.insight.PriceTrend
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.match.MatchResult
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.CompareRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.navigation.PropertyDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Why the detail page has nothing to show. */
enum class DetailError {
    /** No listing with this id is cached (removed listing or a stale shared link). */
    NotFound,

    /** Reading the listing failed; Retry tries again. */
    Unavailable,
}

sealed interface DetailUiState {
    data object Loading : DetailUiState

    @Immutable
    data class Content(
        val property: Property,
        val isFavorite: Boolean,
        /** Same type in the same region, the current listing excluded. */
        val similar: List<Property>,
        val similarFavoriteIds: Set<String>,
        /** Last 12 months of rent; null when the listing has fewer than two months of history. */
        val priceTrend: PriceTrend? = PriceTrend.from(property.priceHistory),
        /** Always sample data until a neighbourhood source exists (see [NeighbourhoodScore.isSampleData]). */
        val neighbourhood: NeighbourhoodScore = NeighbourhoodScore.sample(property.regionId, property.locality),
        val affordability: AffordabilityUiState = AffordabilityUiState(property.rent),
        /** The listing scored against the user's preference profile; null when they have none. */
        val match: MatchResult? = null,
        /** True while the listing is in the user's compare selection. */
        val isInCompare: Boolean = false,
    ) : DetailUiState

    data class Error(
        val error: DetailError,
    ) : DetailUiState
}

sealed interface DetailUiEvent {
    data object ToggleFavorite : DetailUiEvent

    data class ToggleSimilarFavorite(
        val propertyId: String,
    ) : DetailUiEvent

    data object Retry : DetailUiEvent

    data object Share : DetailUiEvent

    data object CallAgent : DetailUiEvent

    data object EmailAgent : DetailUiEvent

    data object OpenInMaps : DetailUiEvent

    /** Adds the listing to the compare selection, or removes it when it is already there. */
    data object ToggleCompare : DetailUiEvent

    /** Anything in the "Can I afford it?" calculator. */
    data class Affordability(
        val event: AffordabilityEvent,
    ) : DetailUiEvent
}

/** One-shot actions: [External] ones are handed to another app with an Android intent. */
sealed interface DetailUiEffect {
    /** Performed by another app (share sheet, dialer, e-mail, maps). */
    sealed interface External : DetailUiEffect

    data class Share(
        val title: String,
        val link: String,
    ) : External

    data class Dial(
        val phone: String,
    ) : External

    data class Email(
        val address: String,
        val listingTitle: String,
        val link: String,
    ) : External

    data class OpenMap(
        val point: GeoPoint,
        val label: String,
    ) : External

    /** The listing was offered to the compare selection; the snackbar says what happened. */
    data class CompareAdded(
        val result: CompareAddResult,
    ) : DetailUiEffect

    /** The listing was taken out of the compare selection. */
    data object CompareRemoved : DetailUiEffect
}

/**
 * A listing's detail page. Streams the listing, its favourite state and similar homes from the local
 * cache; records a "viewed" interaction when opened, and "shared" / "contacted agent" / favourite signals
 * as the user acts, for the best-match engine, and scores the listing against the user's profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val properties: PropertyRepository,
        private val favorites: FavoritesRepository,
        private val interactions: InteractionRepository,
        private val auth: AuthRepository,
        private val clock: AppClock,
        observeMatchContext: ObserveMatchContext,
        private val compare: CompareRepository,
    ) : ViewModel() {
        /** Read by name (type-safe routes store each field under its own key), so this stays JVM-testable. */
        val propertyId: String = requireNotNull(savedStateHandle.get<String>(PROPERTY_ID_KEY)) { "propertyId missing" }

        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val attempt = MutableStateFlow(0)
        private val effectChannel = Channel<DetailUiEffect>(Channel.BUFFERED)

        /** The calculator form; null until first used, then kept while the listing's rent updates. */
        private val affordability = MutableStateFlow<AffordabilityUiState?>(null)

        /** A broken profile store hides the match card instead of breaking the page. */
        private val matchContext: Flow<MatchContext?> =
            observeMatchContext().catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            }

        val effects: Flow<DetailUiEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<DetailUiState> =
            attempt
                .flatMapLatest {
                    content()
                        .onStart { emit(DetailUiState.Loading) }
                        .catch { error ->
                            if (error is CancellationException) throw error
                            emit(DetailUiState.Error(DetailError.Unavailable))
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DetailUiState.Loading)

        init {
            viewModelScope.launch { record(propertyId, InteractionType.Viewed) }
        }

        fun onEvent(event: DetailUiEvent) {
            when (event) {
                DetailUiEvent.ToggleFavorite -> toggleFavorite(propertyId)
                is DetailUiEvent.ToggleSimilarFavorite -> toggleFavorite(event.propertyId)
                DetailUiEvent.Retry -> attempt.value += 1
                DetailUiEvent.Share ->
                    withProperty { property ->
                        record(property.id, InteractionType.Shared)
                        DetailUiEffect.Share(property.title, PropertyDeepLink.uriFor(property.id))
                    }
                DetailUiEvent.CallAgent ->
                    withProperty { property ->
                        record(property.id, InteractionType.ContactedAgent)
                        DetailUiEffect.Dial(property.agent.phone)
                    }
                DetailUiEvent.EmailAgent ->
                    withProperty { property ->
                        record(property.id, InteractionType.ContactedAgent)
                        DetailUiEffect.Email(property.agent.email, property.title, PropertyDeepLink.uriFor(property.id))
                    }
                DetailUiEvent.OpenInMaps ->
                    withProperty { property -> DetailUiEffect.OpenMap(property.location, property.title) }
                is DetailUiEvent.Affordability -> onAffordability(event.event)
                DetailUiEvent.ToggleCompare -> toggleCompare()
            }
        }

        private fun content(): Flow<DetailUiState> =
            properties.observeProperty(propertyId).flatMapLatest { property ->
                if (property == null) {
                    flowOf(DetailUiState.Error(DetailError.NotFound))
                } else {
                    combine(
                        userId.flatMapLatest { favorites.observeFavoriteIds(it) },
                        similarTo(property),
                        affordability,
                        matchContext,
                        userId.flatMapLatest { compare.observe(it) },
                    ) { favoriteIds, similar, form, context, compared ->
                        DetailUiState.Content(
                            property = property,
                            isFavorite = property.id in favoriteIds,
                            similar = similar,
                            similarFavoriteIds = similar.mapTo(HashSet()) { it.id }.intersect(favoriteIds),
                            affordability = form?.copy(rent = property.rent) ?: AffordabilityUiState(property.rent),
                            match = context?.score(property),
                            isInCompare = property.id in compared,
                        )
                    }
                }
            }

        private fun similarTo(property: Property): Flow<List<Property>> =
            properties
                .observeProperties(property.regionId, PropertyFilter(type = property.type))
                .map { homes -> homes.filter { it.id != property.id }.take(SIMILAR_LIMIT) }
                // Similar homes are a bonus: if they cannot be read the listing itself still shows.
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(emptyList())
                }

        private fun toggleFavorite(id: String) {
            viewModelScope.launch {
                val nowFavorite = favorites.toggle(userId.first(), id)
                record(id, if (nowFavorite) InteractionType.Favorited else InteractionType.Unfavorited)
            }
        }

        private fun toggleCompare() {
            val content = state.value as? DetailUiState.Content ?: return
            viewModelScope.launch {
                val user = userId.first()
                val effect =
                    if (content.isInCompare) {
                        compare.remove(user, propertyId)
                        DetailUiEffect.CompareRemoved
                    } else {
                        DetailUiEffect.CompareAdded(compare.add(user, propertyId))
                    }
                effectChannel.send(effect)
            }
        }

        private fun onAffordability(event: AffordabilityEvent) {
            val content = state.value as? DetailUiState.Content ?: return
            affordability.update { form -> (form ?: content.affordability).reduce(event) }
        }

        private fun withProperty(action: suspend (Property) -> DetailUiEffect) {
            val content = state.value as? DetailUiState.Content ?: return
            viewModelScope.launch { effectChannel.send(action(content.property)) }
        }

        /** Interaction signals are best effort: a failed write never blocks the page or the action. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun record(
            id: String,
            type: InteractionType,
        ) {
            try {
                interactions.record(InteractionEvent(userId.first(), id, type, clock.now()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Could not record $type for $id", error)
            }
        }

        companion object {
            /** The `FindoraRoute.PropertyDetail` field the id is stored under. */
            const val PROPERTY_ID_KEY = "propertyId"
            const val SIMILAR_LIMIT = 10
            private const val STOP_TIMEOUT_MILLIS = 5_000L
            private const val TAG = "DetailViewModel"
        }
    }
