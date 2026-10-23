package com.findora.app.feature.match.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.match.DeckEntry
import com.findora.app.core.domain.match.DreamMatchDeck
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** What the user did with a card (by gesture or by button). */
enum class DeckAction(
    val interaction: InteractionType,
) {
    /** Right: saved to favourites and to the Matches collection. */
    Save(InteractionType.DeckSaved),

    /** Left: skipped, a mild negative signal for the match engine. */
    Skip(InteractionType.DeckSkipped),

    /** Up: saved, then the viewing booking opens. */
    SuperLike(InteractionType.DeckSuperLiked),
}

data class DeckUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val cards: List<DeckEntry> = emptyList(),
    /** Index of the card on top of the stack; equal to [total] once every card was acted on. */
    val index: Int = 0,
    /** True when the order follows the user's preference profile (Best match) rather than Recommended. */
    val rankedByMatch: Boolean = false,
    /** The most recent action that Undo would revert, or null when there is nothing to undo. */
    val lastAction: DeckAction? = null,
    /** How many cards were saved (or super-liked) in this run of the deck. */
    val savedCount: Int = 0,
) {
    val total: Int get() = cards.size
    val current: DeckEntry? get() = cards.getOrNull(index)
    val next: DeckEntry? get() = cards.getOrNull(index + 1)
    val canUndo: Boolean get() = lastAction != null
    val isFinished: Boolean get() = !isLoading && !loadFailed && index >= cards.size

    /** "n" of "n / total": the card on top, counted from 1. */
    val position: Int get() = (index + 1).coerceAtMost(total)

    /** 0..1 share of the deck already acted on. */
    val progress: Float get() = if (total == 0) 1f else index.toFloat() / total
}

sealed interface DeckUiEvent {
    data class Act(
        val action: DeckAction,
    ) : DeckUiEvent

    data object Undo : DeckUiEvent

    /** Deals the deck again, including homes skipped before (saved ones stay out). */
    data object Restart : DeckUiEvent

    /** Loads the deck again after a failure. */
    data object Retry : DeckUiEvent
}

sealed interface DeckUiEffect {
    /** A super-like: open the booking screen for the agent's viewing slots. */
    data class OpenScheduleViewing(
        val propertyId: String,
    ) : DeckUiEffect
}

/**
 * Dream Match: the current region's homes as a stack of cards, best match first (Recommended order without a
 * profile), leaving out homes already saved or skipped before. Every action is persisted (favourite plus a
 * deck interaction event for the match engine) and can be undone, most recent first.
 */
@HiltViewModel
class DeckViewModel
    @Inject
    constructor(
        private val properties: PropertyRepository,
        private val favorites: FavoritesRepository,
        private val interactions: InteractionRepository,
        private val auth: AuthRepository,
        private val currentRegion: CurrentRegion,
        private val clock: AppClock,
        private val observeMatchContext: ObserveMatchContext,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val mutableState = MutableStateFlow(DeckUiState())
        private val effectChannel = Channel<DeckUiEffect>(Channel.BUFFERED)

        /** Actions of this run, most recent last; Undo pops them one by one. */
        private val performed = ArrayDeque<Performed>()

        /** Actions are applied one at a time so Undo always reverts a fully persisted action. */
        private val mutex = Mutex()

        val state: StateFlow<DeckUiState> = mutableState.asStateFlow()
        val effects: Flow<DeckUiEffect> = effectChannel.receiveAsFlow()

        init {
            load(includeSkipped = false)
        }

        fun onEvent(event: DeckUiEvent) {
            when (event) {
                is DeckUiEvent.Act -> act(event.action)
                DeckUiEvent.Undo -> undo()
                DeckUiEvent.Restart -> load(includeSkipped = true)
                DeckUiEvent.Retry -> load(includeSkipped = false)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun load(includeSkipped: Boolean) {
            viewModelScope.launch {
                mutex.withLock {
                    mutableState.update { it.copy(isLoading = true, loadFailed = false) }
                    try {
                        val user = userId.first()
                        val region = currentRegion.region.first()
                        val listings = properties.observeProperties(region.id).first()
                        val saved = favorites.observeFavoriteIds(user).first()
                        val skipped =
                            if (includeSkipped) {
                                emptySet()
                            } else {
                                interactions.observePropertyIds(user, setOf(InteractionType.DeckSkipped)).first().toSet()
                            }
                        val context = matchContextOrNull()
                        performed.clear()
                        mutableState.value =
                            DeckUiState(
                                isLoading = false,
                                cards = DreamMatchDeck.order(listings, saved + skipped, context),
                                rankedByMatch = context != null,
                            )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w(TAG, "Could not deal the deck", error)
                        mutableState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
                }
            }
        }

        /** A broken profile store deals the Recommended deck instead of failing. */
        private suspend fun matchContextOrNull(): MatchContext? =
            observeMatchContext()
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(null)
                }.first()

        private fun act(action: DeckAction) {
            viewModelScope.launch {
                mutex.withLock {
                    val entry = mutableState.value.current ?: return@withLock
                    val user = userId.first()
                    val id = entry.property.id
                    val addedFavorite = action != DeckAction.Skip && ensureFavorite(user, id)
                    val event = InteractionEvent(user, id, action.interaction, clock.now())
                    bestEffort("record ${action.interaction}") { interactions.record(event) }
                    performed.addLast(Performed(action, event, addedFavorite))
                    mutableState.update { state ->
                        state.copy(
                            index = state.index + 1,
                            lastAction = action,
                            savedCount = state.savedCount + if (action == DeckAction.Skip) 0 else 1,
                        )
                    }
                    if (action == DeckAction.SuperLike) effectChannel.send(DeckUiEffect.OpenScheduleViewing(id))
                }
            }
        }

        private fun undo() {
            viewModelScope.launch {
                mutex.withLock {
                    val last = performed.removeLastOrNull() ?: return@withLock
                    bestEffort("remove ${last.event.type}") { interactions.remove(last.event) }
                    if (last.addedFavorite) {
                        val user = last.event.userId
                        val id = last.event.propertyId
                        if (id in favorites.observeFavoriteIds(user).first()) favorites.toggle(user, id)
                    }
                    mutableState.update { state ->
                        state.copy(
                            index = (state.index - 1).coerceAtLeast(0),
                            lastAction = performed.lastOrNull()?.action,
                            savedCount = state.savedCount - if (last.action == DeckAction.Skip) 0 else 1,
                        )
                    }
                }
            }
        }

        /** Saves [id] unless it already is a favourite; true when this call added it (so Undo removes it). */
        private suspend fun ensureFavorite(
            user: String,
            id: String,
        ): Boolean = id !in favorites.observeFavoriteIds(user).first() && favorites.toggle(user, id)

        /** Interaction signals are best effort: a failed write never blocks the deck. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun bestEffort(
            what: String,
            block: suspend () -> Unit,
        ) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Could not $what", error)
            }
        }

        private data class Performed(
            val action: DeckAction,
            val event: InteractionEvent,
            val addedFavorite: Boolean,
        )

        private companion object {
            const val TAG = "DeckViewModel"
        }
    }
