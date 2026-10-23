package com.findora.app.feature.owner.wizard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.WizardStep

private val MaxContentWidth = 720.dp

@Composable
fun ListingWizardRoute(
    onClose: () -> Unit,
    onOpenListing: (String) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListingWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenListing by rememberUpdatedState(onOpenListing)
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WizardEffect.Close -> currentOnClose()
                is WizardEffect.Published -> currentOnOpenListing(effect.listingId)
                is WizardEffect.PhotosFailed ->
                    snackbarHostState.showSnackbar(
                        resources.getQuantityString(R.plurals.owner_photos_failed, effect.count, effect.count),
                    )
                is WizardEffect.PhotosOverLimit ->
                    snackbarHostState.showSnackbar(
                        resources.getQuantityString(R.plurals.owner_photos_over_limit, effect.max, effect.max),
                    )
            }
        }
    }
    val pickPhotos =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(ListingValidator.PHOTOS_MAX),
        ) { uris -> if (uris.isNotEmpty()) viewModel.onEvent(WizardEvent.PhotosPicked(uris.map { it.toString() })) }
    BackHandler(enabled = !state.loading && state.listingId != null) { viewModel.onEvent(WizardEvent.Back) }
    ListingWizardScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        onCreateAccount = onCreateAccount,
        onPickPhotos = {
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * The listing wizard: a header with the step ("Step 3 of 7"), its progress and the autosave status, the step's form,
 * and Back / Next (Publish on the preview). [pinPicker] draws the map of the location step; tests replace it.
 */
@Composable
fun ListingWizardScreen(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    onClose: () -> Unit,
    onCreateAccount: () -> Unit,
    onPickPhotos: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    pinPicker: @Composable (
        GeoPoint,
        Double,
        GeoPoint?,
        (GeoPoint) -> Unit,
        Modifier,
    ) -> Unit = { center, zoom, pin, onPin, mapModifier ->
        PinPickerMap(center = center, zoom = zoom, pin = pin, onPlacePin = onPin, modifier = mapModifier)
    },
) {
    Box(
        modifier
            .fillMaxSize()
            .background(FindoraTheme.colors.background)
            .testTag("listingWizard"),
    ) {
        when {
            state.loading ->
                CircularProgressIndicator(
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.align(Alignment.Center),
                )
            state.needsAccount -> WizardMessage(R.string.owner_guest_message, onClose, onCreateAccount)
            state.notFound -> WizardMessage(R.string.owner_listing_not_found, onClose, null)
            else -> WizardContent(state, onEvent, onPickPhotos, pinPicker)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun WizardContent(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    onPickPhotos: () -> Unit,
    pinPicker: @Composable (GeoPoint, Double, GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    Column(modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        WizardHeader(state = state, onBack = { onEvent(WizardEvent.Back) })
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .testTag("wizardStep_${state.step.name}"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            ) {
                Spacer(Modifier.height(FindoraTheme.spacing.xs))
                when (state.step) {
                    WizardStep.Basics -> BasicsStep(state, onEvent)
                    WizardStep.Location -> LocationStep(state, onEvent, pinPicker)
                    WizardStep.Details -> DetailsStep(state, onEvent)
                    WizardStep.Amenities -> AmenitiesStep(state, onEvent)
                    WizardStep.Photos -> PhotosStep(state, onEvent, onPickPhotos)
                    WizardStep.Price -> PriceStep(state, onEvent)
                    WizardStep.Preview -> PreviewStep(state, onEvent)
                }
                Spacer(Modifier.height(FindoraTheme.spacing.lg))
            }
        }
        WizardBottomBar(state = state, onEvent = onEvent)
    }
}

@Composable
private fun WizardHeader(
    state: WizardUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        stringResource(
            if (state.status ==
                ListingStatus.Draft
            ) {
                R.string.owner_wizard_title_new
            } else {
                R.string.owner_edit_listing
            },
        )
    val total = WizardStep.entries.size
    Column(modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("wizardBack")) {
                Icon(
                    imageVector = if (state.step == WizardStep.Basics) FindoraIcons.Close else FindoraIcons.ArrowBack,
                    contentDescription =
                        stringResource(if (state.step == WizardStep.Basics) R.string.action_close else R.string.back),
                    tint = FindoraTheme.colors.onBackground,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = FindoraTheme.typography.section,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text =
                        stringResource(
                            R.string.owner_step_of,
                            state.step.number,
                            total,
                            stringResource(state.step.titleRes()),
                        ),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("wizardStepLabel"),
                )
            }
            SaveStatus(state)
        }
        LinearProgressIndicator(
            progress = { state.step.number.toFloat() / total },
            color = FindoraTheme.colors.accent,
            trackColor = FindoraTheme.colors.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xs),
        )
    }
}

@Composable
private fun SaveStatus(
    state: WizardUiState,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            state.importingPhotos > 0 ->
                pluralStringResource(R.plurals.owner_photos_importing, state.importingPhotos, state.importingPhotos)
            else ->
                when (state.saveState) {
                    SaveState.Idle -> return
                    SaveState.Saving -> stringResource(R.string.owner_saving)
                    SaveState.Saved -> stringResource(R.string.owner_draft_saved)
                }
        }
    Text(
        text = text,
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(end = FindoraTheme.spacing.sm).testTag("wizardSaveStatus"),
    )
}

@Composable
private fun WizardBottomBar(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
    ) {
        if (state.step != WizardStep.Basics) {
            SecondaryPillButton(
                text = stringResource(R.string.back),
                onClick = { onEvent(WizardEvent.Back) },
                modifier = Modifier.weight(1f).testTag("wizardPrevious"),
            )
        }
        if (state.step == WizardStep.Preview) {
            PrimaryPillButton(
                text =
                    stringResource(
                        if (state.status ==
                            ListingStatus.Published
                        ) {
                            R.string.owner_publish_changes
                        } else {
                            R.string.owner_publish
                        },
                    ),
                onClick = { onEvent(WizardEvent.Publish) },
                enabled = !state.publishing && state.importingPhotos == 0,
                modifier = Modifier.weight(1f).testTag("wizardPublish"),
            )
        } else {
            PrimaryPillButton(
                text = stringResource(R.string.owner_next),
                onClick = { onEvent(WizardEvent.Next) },
                enabled = state.importingPhotos == 0 || state.step != WizardStep.Photos,
                modifier = Modifier.weight(1f).testTag("wizardNext"),
            )
        }
    }
}

@Composable
private fun WizardMessage(
    message: Int,
    onClose: () -> Unit,
    onCreateAccount: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(FindoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(message),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.testTag("wizardMessage"),
        )
        if (onCreateAccount != null) {
            PrimaryPillButton(text = stringResource(R.string.action_create_account), onClick = onCreateAccount)
        }
        SecondaryPillButton(text = stringResource(R.string.action_close), onClick = onClose)
    }
}

/** The step's name, shown after "Step n of 7". */
fun WizardStep.titleRes(): Int =
    when (this) {
        WizardStep.Basics -> R.string.owner_step_basics
        WizardStep.Location -> R.string.owner_step_location
        WizardStep.Details -> R.string.owner_step_details
        WizardStep.Amenities -> R.string.owner_step_amenities
        WizardStep.Photos -> R.string.owner_step_photos
        WizardStep.Price -> R.string.owner_step_price
        WizardStep.Preview -> R.string.owner_step_preview
    }
