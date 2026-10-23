package com.findora.app.feature.match.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.LocationAnchor
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Locality
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The six quiz steps, in order. */
enum class QuizStep { Budget, Rooms, Types, Locations, Lifestyle, MustHaves }

/** The answers so far. A null or empty field means "no preference" for that part of the profile. */
data class QuizDraft(
    val budgetMin: Long? = null,
    val budgetMax: Long? = null,
    val bedrooms: Int? = null,
    val bathrooms: Int? = null,
    val types: Set<PropertyType> = emptySet(),
    val areas: Set<String> = emptySet(),
    val commute: String? = null,
    val lifestyle: Set<LifestyleTag> = emptySet(),
    val mustHaves: Set<MustHave> = emptySet(),
) {
    /** Clears the answers of [step] (Skip). */
    fun without(step: QuizStep): QuizDraft =
        when (step) {
            QuizStep.Budget -> copy(budgetMin = null, budgetMax = null)
            QuizStep.Rooms -> copy(bedrooms = null, bathrooms = null)
            QuizStep.Types -> copy(types = emptySet())
            QuizStep.Locations -> copy(areas = emptySet(), commute = null)
            QuizStep.Lifestyle -> copy(lifestyle = emptySet())
            QuizStep.MustHaves -> copy(mustHaves = emptySet())
        }

    /** The profile these answers describe, with neighbourhood names resolved against [localities]. */
    fun toProfile(
        localities: List<Locality>,
        currency: Currency,
        bounds: ListingBounds?,
    ): PreferenceProfile {
        val ceiling = budgetMax ?: bounds?.maxRent
        val byName = localities.associateBy { it.name }
        val answeredBudget = budgetMin != null || budgetMax != null
        return PreferenceProfile(
            budget =
                if (answeredBudget && ceiling != null && ceiling > 0) {
                    BudgetRange(budgetMin?.coerceAtMost(ceiling), ceiling, currency)
                } else {
                    null
                },
            minBedrooms = bedrooms,
            minBathrooms = bathrooms,
            propertyTypes = types,
            anchors =
                areas.mapNotNull { name ->
                    byName[name]?.let { LocationAnchor(it.name, it.location, LocationAnchor.Kind.Neighbourhood) }
                } +
                    listOfNotNull(
                        commute?.let { name -> byName[name] }?.let {
                            LocationAnchor(it.name, it.location, LocationAnchor.Kind.Commute)
                        },
                    ),
            lifestyle = lifestyle,
            mustHaves = mustHaves,
        )
    }

    companion object {
        /** Pre-fills the quiz when editing; a budget in another currency (other region) is not carried over. */
        fun from(
            profile: PreferenceProfile,
            currency: Currency,
        ): QuizDraft =
            QuizDraft(
                budgetMin = profile.budget?.takeIf { it.currency == currency }?.min,
                budgetMax = profile.budget?.takeIf { it.currency == currency }?.max,
                bedrooms = profile.minBedrooms,
                bathrooms = profile.minBathrooms,
                types = profile.propertyTypes,
                areas = profile.neighbourhoods.mapTo(LinkedHashSet()) { it.name },
                commute = profile.commute?.name,
                lifestyle = profile.lifestyle,
                mustHaves = profile.mustHaves,
            )
    }
}

data class MatchQuizUiState(
    val step: QuizStep = QuizStep.Budget,
    val draft: QuizDraft = QuizDraft(),
    val localities: List<Locality> = emptyList(),
    val currency: Currency = Currency.INR,
    val bounds: ListingBounds? = null,
    /** True when an existing profile was loaded (opened from Settings to edit it). */
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = QuizStep.entries.size
    val progress: Float get() = stepNumber.toFloat() / stepCount
    val isLastStep: Boolean get() = step == QuizStep.entries.last()
}

sealed interface MatchQuizUiEvent {
    data object Next : MatchQuizUiEvent

    data object Back : MatchQuizUiEvent

    data object Skip : MatchQuizUiEvent

    /** New budget handles; a value at the slider's end means "no limit" on that side and is stored as null. */
    data class SetBudget(
        val min: Long?,
        val max: Long?,
    ) : MatchQuizUiEvent

    data class SetBedrooms(
        val count: Int?,
    ) : MatchQuizUiEvent

    data class SetBathrooms(
        val count: Int?,
    ) : MatchQuizUiEvent

    data class ToggleType(
        val type: PropertyType,
    ) : MatchQuizUiEvent

    data class ToggleArea(
        val name: String,
    ) : MatchQuizUiEvent

    data class SetCommute(
        val name: String?,
    ) : MatchQuizUiEvent

    data class ToggleLifestyle(
        val tag: LifestyleTag,
    ) : MatchQuizUiEvent

    data class ToggleMustHave(
        val mustHave: MustHave,
    ) : MatchQuizUiEvent
}

sealed interface MatchQuizUiEffect {
    /** The profile was saved; the screen closes and Home re-ranks. */
    data object Finished : MatchQuizUiEffect

    /** Back from the first step: leave without saving. */
    data object Closed : MatchQuizUiEffect
}

/**
 * The six-step preference quiz. Each step can be answered, skipped (its answers cleared) or revisited with
 * Back; the last step saves the profile for the signed-in user (or the guest). Opened from Settings it
 * starts from the saved profile so the user edits rather than re-answers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatchQuizViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val profiles: MatchProfileRepository,
        private val properties: PropertyRepository,
        private val currentRegion: CurrentRegion,
    ) : ViewModel() {
        private val userId: Flow<String> =
            auth.session.map { it?.userId ?: AuthRepository.GUEST_USER_ID }.distinctUntilChanged()
        private val mutableState = MutableStateFlow(MatchQuizUiState())
        private val effectChannel = Channel<MatchQuizUiEffect>(Channel.BUFFERED)

        val state: StateFlow<MatchQuizUiState> = mutableState.asStateFlow()
        val effects: Flow<MatchQuizUiEffect> = effectChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                val region = currentRegion.region.first()
                val existing = profiles.observe(userId.first()).first()
                mutableState.update { state ->
                    state.copy(
                        localities = region.localities,
                        currency = region.currency,
                        draft = existing?.let { QuizDraft.from(it, region.currency) } ?: state.draft,
                        isEditing = existing != null,
                    )
                }
            }
            viewModelScope.launch {
                currentRegion.region
                    .flatMapLatest { region ->
                        boundsOf(region).map { region to it }
                    }.collect { (region, bounds) ->
                        mutableState.update {
                            it.copy(localities = region.localities, currency = region.currency, bounds = bounds)
                        }
                    }
            }
        }

        private fun boundsOf(region: RegionPack): Flow<ListingBounds?> = properties.observeBounds(region.id)

        fun onEvent(event: MatchQuizUiEvent) {
            when (event) {
                MatchQuizUiEvent.Next -> advance()
                MatchQuizUiEvent.Skip -> {
                    mutableState.update { it.copy(draft = it.draft.without(it.step)) }
                    advance()
                }
                MatchQuizUiEvent.Back -> back()
                is MatchQuizUiEvent.SetBudget -> editDraft { it.copy(budgetMin = event.min, budgetMax = event.max) }
                is MatchQuizUiEvent.SetBedrooms -> editDraft { it.copy(bedrooms = event.count) }
                is MatchQuizUiEvent.SetBathrooms -> editDraft { it.copy(bathrooms = event.count) }
                is MatchQuizUiEvent.ToggleType -> editDraft { it.copy(types = it.types.toggle(event.type)) }
                is MatchQuizUiEvent.ToggleArea -> editDraft { it.copy(areas = it.areas.toggle(event.name)) }
                is MatchQuizUiEvent.SetCommute -> editDraft { it.copy(commute = event.name) }
                is MatchQuizUiEvent.ToggleLifestyle -> editDraft { it.copy(lifestyle = it.lifestyle.toggle(event.tag)) }
                is MatchQuizUiEvent.ToggleMustHave ->
                    editDraft { it.copy(mustHaves = it.mustHaves.toggle(event.mustHave)) }
            }
        }

        private fun editDraft(change: (QuizDraft) -> QuizDraft) {
            mutableState.update { it.copy(draft = change(it.draft)) }
        }

        private fun advance() {
            val current = mutableState.value
            if (current.isSaving) return
            if (!current.isLastStep) {
                mutableState.update { it.copy(step = QuizStep.entries[it.step.ordinal + 1]) }
                return
            }
            mutableState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val state = mutableState.value
                profiles.save(userId.first(), state.draft.toProfile(state.localities, state.currency, state.bounds))
                effectChannel.send(MatchQuizUiEffect.Finished)
            }
        }

        private fun back() {
            val step = mutableState.value.step
            if (step.ordinal == 0) {
                viewModelScope.launch { effectChannel.send(MatchQuizUiEffect.Closed) }
            } else {
                mutableState.update { it.copy(step = QuizStep.entries[step.ordinal - 1]) }
            }
        }

        private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
    }
