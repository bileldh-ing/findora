package com.findora.app.feature.owner.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingError
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.ListingPhotoStore
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.OwnerListingRepository
import com.findora.app.core.domain.owner.PublishResult
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.RegionRepository
import com.findora.app.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Autosave progress shown under the wizard's title. */
enum class SaveState { Idle, Saving, Saved }

data class WizardUiState(
    val loading: Boolean = true,
    val listingId: String? = null,
    val step: WizardStep = WizardStep.Basics,
    val draft: ListingDraft = ListingDraft(),
    val status: ListingStatus = ListingStatus.Draft,
    /** Errors of the current step; only filled once the user tried to leave it (or publish). */
    val errors: Map<ListingField, ListingError> = emptyMap(),
    val regions: List<RegionPack> = emptyList(),
    val saveState: SaveState = SaveState.Idle,
    /** Photos being imported right now. */
    val importingPhotos: Int = 0,
    val publishing: Boolean = false,
    /** Guests and signed-out users cannot own listings. */
    val needsAccount: Boolean = false,
    /** The listing to edit does not exist or belongs to someone else. */
    val notFound: Boolean = false,
    /** The signed-in owner (the listing's contact in the preview). */
    val owner: UserAccount? = null,
) {
    val region: RegionPack? get() = regions.firstOrNull { it.id == draft.regionId }
    val canAddPhotos: Boolean get() = draft.photos.size + importingPhotos < ListingValidator.PHOTOS_MAX
    val photoSlotsLeft: Int get() = (ListingValidator.PHOTOS_MAX - draft.photos.size - importingPhotos).coerceAtLeast(0)
}

/** Everything the wizard's user can do. */
sealed interface WizardEvent {
    data class TypeSelected(
        val type: PropertyType,
    ) : WizardEvent

    data class TitleChanged(
        val title: String,
    ) : WizardEvent

    data class DescriptionChanged(
        val description: String,
    ) : WizardEvent

    data class RegionSelected(
        val regionId: String,
    ) : WizardEvent

    data class LocalitySelected(
        val name: String,
    ) : WizardEvent

    data class PinPlaced(
        val point: GeoPoint,
    ) : WizardEvent

    /** Puts the pin on the chosen neighbourhood's centre (the accessible alternative to tapping the map). */
    data object PinAtLocality : WizardEvent

    data class BedroomsChanged(
        val value: Int?,
    ) : WizardEvent

    data class BathroomsChanged(
        val value: Int?,
    ) : WizardEvent

    data class AreaChanged(
        val value: Int?,
    ) : WizardEvent

    data class FloorChanged(
        val value: Int?,
    ) : WizardEvent

    data class FurnishedChanged(
        val value: Boolean,
    ) : WizardEvent

    data class PetsChanged(
        val value: Boolean,
    ) : WizardEvent

    data class ParkingChanged(
        val value: Boolean,
    ) : WizardEvent

    data class AmenityToggled(
        val amenity: Amenity,
    ) : WizardEvent

    /** Content URIs returned by the Photo Picker. */
    data class PhotosPicked(
        val uris: List<String>,
    ) : WizardEvent

    data class PhotoRemoved(
        val index: Int,
    ) : WizardEvent

    data class PhotoMoved(
        val from: Int,
        val to: Int,
    ) : WizardEvent

    data class CoverChosen(
        val index: Int,
    ) : WizardEvent

    data class RentChanged(
        val value: Long?,
    ) : WizardEvent

    data class CurrencySelected(
        val currency: Currency,
    ) : WizardEvent

    data class DepositChanged(
        val value: Long?,
    ) : WizardEvent

    data object Next : WizardEvent

    data object Back : WizardEvent

    /** Jump to an earlier step (from the preview's "Edit" links or the step indicator). */
    data class GoTo(
        val step: WizardStep,
    ) : WizardEvent

    data object Publish : WizardEvent
}

sealed interface WizardEffect {
    data class Published(
        val listingId: String,
    ) : WizardEffect

    /** Back on the first step: leave the wizard (the draft is already saved). */
    data object Close : WizardEffect

    /** Some picked items could not be imported (unreadable or not images). */
    data class PhotosFailed(
        val count: Int,
    ) : WizardEffect

    /** More photos were picked than the listing has room for. */
    data class PhotosOverLimit(
        val max: Int,
    ) : WizardEffect
}

/**
 * The create / edit listing wizard (UDF). A new wizard creates its draft at once so every change can be saved:
 * edits are autosaved [AUTOSAVE_MILLIS] after the last change and immediately on every step change. Next validates the
 * current step ([ListingValidator]) and shows its errors inline; once errors are shown they follow the edits live.
 * Publish validates everything, jumps to the first incomplete step if needed, and otherwise makes the listing live.
 */
@Suppress("TooManyFunctions") // One small handler per wizard event keeps each rule readable.
@HiltViewModel
class ListingWizardViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val auth: AuthRepository,
        private val listings: OwnerListingRepository,
        private val photoStore: ListingPhotoStore,
        private val regions: RegionRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(WizardUiState())
        val state: StateFlow<WizardUiState> = mutableState.asStateFlow()

        private val effectChannel = Channel<WizardEffect>(Channel.BUFFERED)
        val effects: Flow<WizardEffect> = effectChannel.receiveAsFlow()

        private var saveJob: Job? = null
        private var ownerId: String? = null

        init {
            val requestedId: String? = savedStateHandle[LISTING_ID_KEY]
            viewModelScope.launch { load(requestedId, savedStateHandle) }
        }

        private suspend fun load(
            requestedId: String?,
            savedStateHandle: SavedStateHandle,
        ) {
            val session = auth.session.value
            val packs = regions.regions()
            if (session == null || session.isGuest) {
                mutableState.update { it.copy(loading = false, needsAccount = true, regions = packs) }
                return
            }
            ownerId = session.userId
            val listing =
                if (requestedId != null) {
                    listings.observeListing(session.userId, requestedId).first()
                } else {
                    // Process death after creation reuses the same draft instead of creating another one.
                    val created: String? = savedStateHandle[CREATED_ID_KEY]
                    created?.let { listings.observeListing(session.userId, it).first() }
                        ?: listings.createDraft(session.userId).also { savedStateHandle[CREATED_ID_KEY] = it.id }
                }
            if (listing == null) {
                mutableState.update { it.copy(loading = false, notFound = true, regions = packs) }
                return
            }
            val draft = listing.draft.withDefaults(packs, settings.settings.first().regionId)
            val owner = auth.observeCurrentUser().first()
            mutableState.update {
                it.copy(
                    owner = owner,
                    loading = false,
                    listingId = listing.id,
                    draft = draft,
                    status = listing.status,
                    regions = packs,
                )
            }
            if (draft != listing.draft) scheduleSave()
        }

        /** A new draft starts in the region the user browses, priced in its currency. */
        private fun ListingDraft.withDefaults(
            packs: List<RegionPack>,
            currentRegion: String,
        ): ListingDraft {
            val region =
                (
                    packs.firstOrNull {
                        it.id == currentRegion
                    } ?: packs.firstOrNull()
                ).takeIf { regionId == null }
            return if (region == null) this else copy(regionId = region.id, currency = currency ?: region.currency)
        }

        @Suppress("CyclomaticComplexMethod") // One branch per event, each a one-line draft change.
        fun onEvent(event: WizardEvent) {
            if (state.value.listingId == null) return
            when (event) {
                is WizardEvent.TypeSelected -> edit { copy(type = event.type) }
                is WizardEvent.TitleChanged -> edit { copy(title = event.title.take(ListingValidator.TITLE_MAX)) }
                is WizardEvent.DescriptionChanged ->
                    edit { copy(description = event.description.take(ListingValidator.DESCRIPTION_MAX)) }
                is WizardEvent.RegionSelected -> selectRegion(event.regionId)
                is WizardEvent.LocalitySelected -> selectLocality(event.name)
                is WizardEvent.PinPlaced -> edit { copy(pin = event.point) }
                WizardEvent.PinAtLocality -> pinAtLocality()
                is WizardEvent.BedroomsChanged -> edit { copy(bedrooms = event.value) }
                is WizardEvent.BathroomsChanged -> edit { copy(bathrooms = event.value) }
                is WizardEvent.AreaChanged -> edit { copy(areaSqm = event.value) }
                is WizardEvent.FloorChanged -> edit { copy(floor = event.value) }
                is WizardEvent.FurnishedChanged -> edit { copy(furnished = event.value) }
                is WizardEvent.PetsChanged -> edit { copy(petFriendly = event.value) }
                is WizardEvent.ParkingChanged -> edit { copy(parking = event.value) }
                is WizardEvent.AmenityToggled ->
                    edit {
                        copy(
                            amenities =
                                if (event.amenity in
                                    amenities
                                ) {
                                    amenities - event.amenity
                                } else {
                                    amenities + event.amenity
                                },
                        )
                    }
                is WizardEvent.PhotosPicked -> importPhotos(event.uris)
                is WizardEvent.PhotoRemoved -> removePhoto(event.index)
                is WizardEvent.PhotoMoved -> edit { copy(photos = photos.moved(event.from, event.to)) }
                is WizardEvent.CoverChosen -> edit { copy(photos = photos.moved(event.index, 0)) }
                is WizardEvent.RentChanged -> edit { copy(rent = event.value) }
                is WizardEvent.CurrencySelected -> edit { copy(currency = event.currency) }
                is WizardEvent.DepositChanged -> edit { copy(deposit = event.value) }
                WizardEvent.Next -> next()
                WizardEvent.Back -> back()
                is WizardEvent.GoTo -> goTo(event.step)
                WizardEvent.Publish -> publish()
            }
        }

        /** Applies a draft change, re-checks errors already on screen and schedules the autosave. */
        private fun edit(change: ListingDraft.() -> ListingDraft) {
            mutableState.update { current ->
                val draft = current.draft.change()
                val errors =
                    if (current.errors.isEmpty()) {
                        current.errors
                    } else {
                        ListingValidator.validate(current.step, draft, current.regionBounds(draft))
                    }
                current.copy(draft = draft, errors = errors)
            }
            scheduleSave()
        }

        private fun WizardUiState.regionBounds(draft: ListingDraft = this.draft) =
            regions.firstOrNull { it.id == draft.regionId }?.bounds

        private fun selectRegion(regionId: String) {
            val region = state.value.regions.firstOrNull { it.id == regionId } ?: return
            edit {
                copy(
                    regionId = region.id,
                    locality = locality.takeIf { name -> region.localities.any { it.name == name } }.orEmpty(),
                    pin = pin?.takeIf { it in region.bounds },
                    currency = if (rent == null) region.currency else currency ?: region.currency,
                )
            }
        }

        private fun selectLocality(name: String) {
            val locality =
                state.value.region
                    ?.localities
                    ?.firstOrNull { it.name == name } ?: return
            edit { copy(locality = locality.name, pin = pin ?: locality.location) }
        }

        private fun pinAtLocality() {
            val draft = state.value.draft
            val locality =
                state.value.region
                    ?.localities
                    ?.firstOrNull { it.name == draft.locality } ?: return
            edit { copy(pin = locality.location) }
        }

        private fun importPhotos(uris: List<String>) {
            val listingId = state.value.listingId ?: return
            val room = state.value.photoSlotsLeft
            val accepted = uris.take(room)
            if (uris.size >
                room
            ) {
                viewModelScope.launch {
                    effectChannel.send(
                        WizardEffect.PhotosOverLimit(ListingValidator.PHOTOS_MAX),
                    )
                }
            }
            if (accepted.isEmpty()) return
            mutableState.update { it.copy(importingPhotos = it.importingPhotos + accepted.size) }
            viewModelScope.launch {
                var failed = 0
                accepted.forEach { uri ->
                    val photo = photoStore.import(listingId, uri)
                    mutableState.update { it.copy(importingPhotos = it.importingPhotos - 1) }
                    if (photo == null) failed++ else edit { copy(photos = photos + photo) }
                }
                if (failed > 0) effectChannel.send(WizardEffect.PhotosFailed(failed))
            }
        }

        private fun removePhoto(index: Int) {
            val current = state.value
            val photo = current.draft.photos.getOrNull(index) ?: return
            edit { copy(photos = photos.filterIndexed { i, _ -> i != index }) }
            // A never-published listing shows its photos nowhere else; a live one keeps them until republished.
            if (current.status == ListingStatus.Draft) viewModelScope.launch { photoStore.delete(photo) }
        }

        private fun next() {
            val current = state.value
            val errors = ListingValidator.validate(current.step, current.draft, current.regionBounds())
            if (errors.isNotEmpty()) {
                mutableState.update { it.copy(errors = errors) }
                return
            }
            val nextStep = current.step.next() ?: return
            mutableState.update { it.copy(step = nextStep, errors = emptyMap()) }
            saveNow()
        }

        private fun back() {
            val previous = state.value.step.previous()
            if (previous == null) {
                saveNow()
                viewModelScope.launch { effectChannel.send(WizardEffect.Close) }
            } else {
                mutableState.update { it.copy(step = previous, errors = emptyMap()) }
                saveNow()
            }
        }

        /** Earlier steps are always reachable; a later one only when every step before it is complete. */
        private fun goTo(step: WizardStep) {
            val current = state.value
            if (step.ordinal > current.step.ordinal) {
                val blocking =
                    WizardStep.entries
                        .take(step.ordinal)
                        .firstOrNull {
                            ListingValidator
                                .validate(
                                    it,
                                    current.draft,
                                    current.regionBounds(),
                                ).isNotEmpty()
                        }
                if (blocking != null) {
                    mutableState.update {
                        it.copy(
                            step = blocking,
                            errors = ListingValidator.validate(blocking, current.draft, current.regionBounds()),
                        )
                    }
                    return
                }
            }
            mutableState.update { it.copy(step = step, errors = emptyMap()) }
            saveNow()
        }

        private fun publish() {
            val current = state.value
            val listingId = current.listingId
            if (listingId == null || current.publishing) return
            val invalidStep = ListingValidator.firstInvalidStep(current.draft, current.regionBounds())
            if (invalidStep != null) {
                mutableState.update {
                    it.copy(
                        step = invalidStep,
                        errors = ListingValidator.validate(invalidStep, current.draft, current.regionBounds()),
                    )
                }
                return
            }
            mutableState.update { it.copy(publishing = true) }
            viewModelScope.launch {
                save()
                val owner = auth.observeCurrentUser().first()
                val result = owner?.let { listings.publish(it, listingId) } ?: PublishResult.NotFound
                mutableState.update { it.copy(publishing = false) }
                when (result) {
                    is PublishResult.Published -> {
                        mutableState.update { it.copy(status = ListingStatus.Published) }
                        effectChannel.send(WizardEffect.Published(result.listingId))
                    }
                    is PublishResult.Invalid ->
                        mutableState.update {
                            it.copy(
                                step = result.step,
                                errors = ListingValidator.validate(result.step, it.draft, it.regionBounds()),
                            )
                        }
                    PublishResult.NotFound -> mutableState.update { it.copy(notFound = true) }
                }
            }
        }

        private fun scheduleSave() {
            saveJob?.cancel()
            saveJob =
                viewModelScope.launch {
                    delay(AUTOSAVE_MILLIS)
                    save()
                }
        }

        private fun saveNow() {
            saveJob?.cancel()
            saveJob = viewModelScope.launch { save() }
        }

        private suspend fun save() {
            val owner = ownerId ?: return
            val current = state.value
            val listingId = current.listingId ?: return
            mutableState.update { it.copy(saveState = SaveState.Saving) }
            listings.saveDraft(owner, listingId, current.draft)
            mutableState.update { it.copy(saveState = SaveState.Saved) }
        }

        companion object {
            /** Navigation argument of [com.findora.app.navigation.FindoraRoute.ListingEditor]. */
            const val LISTING_ID_KEY = "listingId"
            private const val CREATED_ID_KEY = "createdListingId"
            const val AUTOSAVE_MILLIS = 600L
        }
    }

/** [this] with the item at [from] moved to [to] (indices clamped). */
internal fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> {
    val target = to.coerceIn(indices)
    return if (from !in indices || from == target) this else toMutableList().apply { add(target, removeAt(from)) }
}
