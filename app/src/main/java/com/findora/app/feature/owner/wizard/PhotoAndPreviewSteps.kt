package com.findora.app.feature.owner.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.findora.app.R
import com.findora.app.core.designsystem.component.GlassChip
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.ListingPhoto
import com.findora.app.core.domain.owner.ListingPublisher
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.ui.PropertyCard
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.match.labelRes
import com.findora.app.core.ui.rentPerMonthText
import com.findora.app.core.ui.rentText
import java.io.File
import java.time.Instant

private val ThumbWidth = 132.dp
private val PreviewCardWidth = 280.dp
private const val THUMB_ASPECT = 4f / 3f
private val SummaryIconSize = 22.dp
private val SummaryRowMinHeight = 56.dp

/**
 * Photos: the Photo Picker (no storage permission), the imported photos with the cover first, and per photo Move
 * earlier / later, Make cover and Remove, as buttons and as TalkBack actions.
 */
@Composable
internal fun PhotosStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    onPickPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photos = state.draft.photos
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        Text(
            text = stringResource(R.string.owner_step_photos),
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.owner_photos_hint, ListingValidator.PHOTOS_MAX),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        PrimaryPillButton(
            text =
                if (state.canAddPhotos) {
                    pluralStringResource(R.plurals.owner_add_photos, state.photoSlotsLeft, state.photoSlotsLeft)
                } else {
                    stringResource(R.string.owner_photos_full)
                },
            onClick = onPickPhotos,
            enabled = state.canAddPhotos,
            modifier = Modifier.fillMaxWidth().testTag("addPhotos"),
        )
        errorText(state, ListingField.Photos)?.let {
            Text(
                it,
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.error,
                modifier = Modifier.testTag("error_photos"),
            )
        }
        photos.forEachIndexed { index, photo ->
            PhotoRow(
                photo = photo,
                index = index,
                count = photos.size,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun PhotoRow(
    photo: ListingPhoto,
    index: Int,
    count: Int,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val earlier = stringResource(R.string.owner_photo_move_earlier)
    val later = stringResource(R.string.owner_photo_move_later)
    val cover = stringResource(R.string.owner_photo_make_cover)
    val remove = stringResource(R.string.owner_photo_remove)
    val description =
        if (index == 0) {
            stringResource(R.string.owner_photo_cd_cover, index + 1, count)
        } else {
            stringResource(R.string.owner_photo_cd, index + 1, count)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.xs)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    customActions =
                        buildList {
                            if (index > 0) add(action(earlier) { onEvent(WizardEvent.PhotoMoved(index, index - 1)) })
                            if (index <
                                count - 1
                            ) {
                                add(action(later) { onEvent(WizardEvent.PhotoMoved(index, index + 1)) })
                            }
                            if (index > 0) add(action(cover) { onEvent(WizardEvent.CoverChosen(index)) })
                            add(action(remove) { onEvent(WizardEvent.PhotoRemoved(index)) })
                        }
                }.testTag("photo_$index"),
    ) {
        Box(Modifier.width(ThumbWidth).aspectRatio(THUMB_ASPECT).clip(FindoraTheme.shapes.chip)) {
            LocalPhoto(photo, Modifier.fillMaxWidth())
            if (index == 0) {
                GlassChip(Modifier.align(Alignment.TopStart).padding(FindoraTheme.spacing.xxs)) {
                    Text(
                        stringResource(R.string.owner_photo_cover),
                        style = FindoraTheme.typography.label,
                        color = FindoraTheme.colors.onPhoto,
                    )
                }
            }
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            PhotoAction(
                icon = FindoraIcons.ChevronStart,
                label = earlier,
                enabled = index > 0,
                tag = "photoEarlier_$index",
                onClick = { onEvent(WizardEvent.PhotoMoved(index, index - 1)) },
            )
            PhotoAction(
                icon = FindoraIcons.ChevronEnd,
                label = later,
                enabled = index < count - 1,
                tag = "photoLater_$index",
                onClick = { onEvent(WizardEvent.PhotoMoved(index, index + 1)) },
            )
            PhotoAction(
                icon = FindoraIcons.Star,
                label = cover,
                enabled = index > 0,
                tag = "photoCover_$index",
                onClick = { onEvent(WizardEvent.CoverChosen(index)) },
            )
            PhotoAction(
                icon = FindoraIcons.Trash,
                label = remove,
                enabled = true,
                tag = "photoRemove_$index",
                onClick = { onEvent(WizardEvent.PhotoRemoved(index)) },
            )
        }
    }
}

private fun action(
    label: String,
    block: () -> Unit,
) = CustomAccessibilityAction(label) {
    block()
    true
}

@Composable
private fun PhotoAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.testTag(tag)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) FindoraTheme.colors.onBackground else FindoraTheme.colors.outline,
        )
    }
}

/** A photo from app-private storage, its average colour painted while it loads. */
@Composable
internal fun LocalPhoto(
    photo: ListingPhoto,
    modifier: Modifier = Modifier,
) {
    val file = remember(photo.path) { File(photo.path) }
    AsyncImage(
        model = file,
        contentDescription = null,
        placeholder = ColorPainter(Color(photo.dominantColorArgb)),
        error = ColorPainter(Color(photo.dominantColorArgb)),
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(THUMB_ASPECT),
    )
}

/** The listing as others will see its card, then a summary of every step with an Edit link. */
@Composable
internal fun PreviewStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val owner = state.owner
    val preview =
        remember(draft, owner, state.listingId) {
            val id = state.listingId
            if (owner == null || id == null) null else ListingPublisher.toProperty(id, owner, draft, Instant.EPOCH)
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        Text(
            text = stringResource(R.string.owner_step_preview),
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.owner_preview_hint),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        if (preview != null) {
            PropertyCard(
                property = preview,
                isFavorite = false,
                onToggleFavorite = {},
                onClick = {},
                modifier =
                    Modifier
                        .widthIn(
                            max = PreviewCardWidth,
                        ).align(Alignment.CenterHorizontally)
                        .testTag("previewCard"),
            )
        }
        val type = draft.type?.let { stringResource(it.labelRes()) }.orEmpty()
        SummaryRow(WizardStep.Basics, listOf(draft.title, type).filter(String::isNotBlank).joinToString(" · "), onEvent)
        SummaryRow(
            WizardStep.Location,
            listOfNotNull(draft.locality.ifBlank { null }, state.region?.name).joinToString(", "),
            onEvent,
        )
        SummaryRow(
            WizardStep.Details,
            listOfNotNull(
                draft.bedrooms?.let { pluralStringResource(R.plurals.owner_bedrooms, it, it) },
                draft.bathrooms?.let { pluralStringResource(R.plurals.owner_bathrooms, it, it) },
                draft.areaSqm?.let { areaText(it) },
            ).joinToString(" · "),
            onEvent,
        )
        SummaryRow(
            WizardStep.Amenities,
            pluralStringResource(R.plurals.owner_amenities_count, draft.amenities.size, draft.amenities.size),
            onEvent,
        )
        SummaryRow(
            WizardStep.Photos,
            pluralStringResource(R.plurals.owner_photos_count, draft.photos.size, draft.photos.size),
            onEvent,
        )
        SummaryRow(WizardStep.Price, priceSummary(state), onEvent)
    }
}

@Composable
private fun priceSummary(state: WizardUiState): String {
    val draft = state.draft
    val currency = draft.currency
    val rent = draft.rent
    val rentLine = if (currency != null && rent != null) rentPerMonthText(Money(rent, currency)) else ""
    val deposit = draft.deposit
    val depositLine =
        if (currency != null &&
            deposit != null
        ) {
            stringResource(R.string.detail_deposit, rentText(Money(deposit, currency)))
        } else {
            ""
        }
    return listOf(rentLine, depositLine).filter(String::isNotEmpty).joinToString(" · ")
}

@Composable
private fun SummaryRow(
    step: WizardStep,
    value: String,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(step.titleRes())
    val editLabel = stringResource(R.string.owner_edit_step, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = SummaryRowMinHeight)
                .clip(FindoraTheme.shapes.chip)
                .clickable(role = Role.Button, onClickLabel = editLabel) { onEvent(WizardEvent.GoTo(step)) }
                .padding(horizontal = FindoraTheme.spacing.xs)
                .testTag("summary_${step.name}"),
    ) {
        Icon(
            stepIcon(step),
            contentDescription = null,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(SummaryIconSize),
        )
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
            if (value.isNotBlank()) {
                Text(value, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            }
        }
        Text(
            stringResource(R.string.owner_edit),
            style = FindoraTheme.typography.label,
            color = FindoraTheme.colors.accent,
        )
    }
}
