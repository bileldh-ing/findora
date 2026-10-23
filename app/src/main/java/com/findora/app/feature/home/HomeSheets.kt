package com.findora.app.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.InboxMessage
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.ui.SwipeToDeleteRow

private val RowMinHeight = 56.dp
private val RowIconSize = 22.dp

/** Hosts the filter, region and notification sheets; [sheet] says which one is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSheets(
    sheet: HomeSheet,
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    if (sheet == HomeSheet.None) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet == HomeSheet.Filters),
        shape = FindoraTheme.shapes.bottomSheetTop,
        containerColor = FindoraTheme.colors.background,
        contentColor = FindoraTheme.colors.onBackground,
    ) {
        when (sheet) {
            HomeSheet.Filters ->
                FilterSheetContent(
                    initial = state.filter,
                    bounds = state.bounds,
                    currency = state.region?.currency ?: Currency.INR,
                    showBestMatch = state.hasMatchProfile == true,
                    onApply = { filter ->
                        onEvent(HomeUiEvent.ApplyFilter(filter))
                        onDismiss()
                    },
                )
            HomeSheet.Regions ->
                RegionSheetContent(
                    regions = state.regions,
                    currentRegionId = state.region?.id,
                    onSelect = { id ->
                        onEvent(HomeUiEvent.SelectRegion(id))
                        onDismiss()
                    },
                )
            HomeSheet.Notifications ->
                NotificationsSheetContent(
                    messages = state.unread,
                    onDismissMessage = { onEvent(HomeUiEvent.DismissNotification(it)) },
                )
            HomeSheet.None -> Unit
        }
    }
}

@Composable
fun RegionSheetContent(
    regions: List<RegionPack>,
    currentRegionId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .navigationBarsPadding()
                .testTag("regionSheet"),
    ) {
        SheetTitle(stringResource(R.string.region_picker_title))
        regions.forEach { region ->
            val selected = region.id == currentRegionId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .clip(FindoraTheme.shapes.chip)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(region.id) })
                        .padding(horizontal = FindoraTheme.spacing.xs)
                        .testTag("region_${region.id}"),
            ) {
                Icon(
                    FindoraIcons.MapPin,
                    null,
                    tint = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.size(RowIconSize),
                )
                Spacer(Modifier.width(FindoraTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        region.name,
                        style = FindoraTheme.typography.bodyMedium,
                        color = FindoraTheme.colors.onBackground,
                    )
                    Text(
                        text = region.localities.take(PREVIEW_LOCALITIES).joinToString(" · ") { it.name },
                        style = FindoraTheme.typography.caption,
                        color = FindoraTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
                if (selected) {
                    Icon(
                        FindoraIcons.Check,
                        null,
                        tint = FindoraTheme.colors.accent,
                        modifier = Modifier.size(RowIconSize),
                    )
                }
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
    }
}

private const val PREVIEW_LOCALITIES = 3

@Composable
fun NotificationsSheetContent(
    messages: List<InboxMessage>,
    onDismissMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .navigationBarsPadding()
                .testTag("notificationsSheet"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        SheetTitle(stringResource(R.string.notifications_title))
        if (messages.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = RowMinHeight)) {
                Icon(
                    FindoraIcons.Check,
                    null,
                    tint = FindoraTheme.colors.success,
                    modifier = Modifier.size(RowIconSize),
                )
                Spacer(Modifier.width(FindoraTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.notifications_empty),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        val deleteLabel = stringResource(R.string.notifications_mark_read)
        messages.forEach { message ->
            SwipeToDeleteRow(
                deleteLabel = deleteLabel,
                onDelete = { onDismissMessage(message.id) },
                modifier = Modifier.testTag("notification_${message.id}"),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.surface)
                        .padding(FindoraTheme.spacing.sm),
                ) {
                    Text(message.body, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onBackground)
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(message.sentAt.toEpochMilli()).toString(),
                        style = FindoraTheme.typography.caption,
                        color = FindoraTheme.colors.onSurfaceMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
    }
}

@Composable
private fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.title,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.padding(bottom = FindoraTheme.spacing.sm).semantics { heading() },
    )
}
