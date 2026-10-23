package com.findora.app.feature.owner.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.ui.adaptive.LocalAdaptiveLayout
import com.findora.app.feature.owner.wizard.LocalPhoto

private val ContentMaxWidth = 840.dp
private val CoverWidth = 112.dp

@Composable
fun OwnerDashboardRoute(
    onBack: () -> Unit,
    onCreateListing: () -> Unit,
    onEditListing: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OwnerDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val currentOnEditListing by rememberUpdatedState(onEditListing)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OwnerDashboardEffect.Show ->
                    snackbarHostState.showSnackbar(
                        resources.getString(effect.message.textRes()),
                    )
                is OwnerDashboardEffect.FinishListing -> currentOnEditListing(effect.listingId)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        OwnerDashboardScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            onCreateListing = onCreateListing,
            onEditListing = onEditListing,
            onOpenListing = onOpenListing,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private fun OwnerMessage.textRes(): Int =
    when (this) {
        OwnerMessage.Paused -> R.string.owner_msg_paused
        OwnerMessage.Unpublished -> R.string.owner_msg_unpublished
        OwnerMessage.Deleted -> R.string.owner_msg_deleted
        OwnerMessage.Published -> R.string.owner_msg_published
    }

/**
 * Owner dashboard: "List a property", totals and the views chart, "My listings" (status, stats and actions) and the
 * enquiries received. Pause, unpublish and delete open a confirmation dialog.
 */
@Composable
fun OwnerDashboardScreen(
    state: OwnerDashboardUiState,
    onEvent: (OwnerDashboardEvent) -> Unit,
    onBack: () -> Unit,
    onCreateListing: () -> Unit,
    onEditListing: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(FindoraTheme.colors.background)
            .statusBarsPadding()
            .testTag("ownerDashboard"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = FindoraTheme.spacing.xs),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    FindoraIcons.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = FindoraTheme.colors.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.owner_dashboard_title),
                style = FindoraTheme.typography.headline,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        when {
            state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                }
            state.needsAccount ->
                Text(
                    text = stringResource(R.string.owner_guest_message),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onBackground,
                    modifier = Modifier.padding(FindoraTheme.spacing.lg),
                )
            else -> DashboardContent(state, onEvent, onCreateListing, onEditListing, onOpenListing)
        }
    }
    state.pending?.let { pending -> ConfirmDialog(pending, onEvent) }
}

@Composable
private fun DashboardContent(
    state: OwnerDashboardUiState,
    onEvent: (OwnerDashboardEvent) -> Unit,
    onCreateListing: () -> Unit,
    onEditListing: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val twoColumns = LocalAdaptiveLayout.current.useTwoPanes
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("ownerDashboardList"),
        contentPadding =
            PaddingValues(
                start = FindoraTheme.spacing.screenHorizontal,
                end = FindoraTheme.spacing.screenHorizontal,
                bottom = FindoraTheme.spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "create") {
            PrimaryPillButton(
                text = stringResource(R.string.owner_list_property),
                onClick = onCreateListing,
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().testTag("ownerCreateListing"),
            )
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.owner_empty),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().testTag("ownerEmpty"),
                )
            }
            return@LazyColumn
        }
        item(key = "stats") {
            val stats: @Composable (Modifier) -> Unit = { m -> TotalsRow(state.totals, m) }
            val chart: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    SectionHeading(stringResource(R.string.owner_chart_title))
                    Spacer(Modifier.height(FindoraTheme.spacing.xs))
                    ViewsChart(state.dailyViews)
                }
            }
            if (twoColumns) {
                Row(
                    Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.lg),
                ) {
                    stats(Modifier.weight(1f))
                    chart(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                    stats(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(FindoraTheme.spacing.md))
                    chart(Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "listingsTitle") {
            SectionHeading(
                stringResource(R.string.owner_my_listings),
                Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            )
        }
        items(state.items, key = { it.listing.id }) { item ->
            ListingRow(
                item = item,
                onEvent = onEvent,
                onEdit = { onEditListing(item.listing.id) },
                onOpen = { onOpenListing(item.listing.id) },
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            )
        }
        item(key = "enquiriesTitle") {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                SectionHeading(stringResource(R.string.owner_enquiries))
                Text(
                    text =
                        if (state.enquiries.isEmpty()) {
                            stringResource(R.string.owner_enquiries_empty)
                        } else {
                            stringResource(R.string.owner_enquiries_note)
                        },
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        items(state.enquiries, key = { "enquiry_${it.conversationId}" }) { enquiry ->
            EnquiryRow(enquiry, Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingRow(
    item: OwnerListingItem,
    onEvent: (OwnerDashboardEvent) -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listing = item.listing
    val id = listing.id
    Column(
        modifier
            .clip(FindoraTheme.shapes.card)
            .background(FindoraTheme.colors.surface)
            .padding(FindoraTheme.spacing.sm)
            .testTag("ownerListing_$id"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cover = listing.draft.cover
            Box(
                Modifier
                    .width(CoverWidth)
                    .aspectRatio(COVER_ASPECT)
                    .clip(FindoraTheme.shapes.chip)
                    .background(FindoraTheme.colors.outline),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    LocalPhoto(cover, Modifier.fillMaxWidth())
                } else {
                    Icon(FindoraIcons.Image, contentDescription = null, tint = FindoraTheme.colors.onSurfaceMuted)
                }
            }
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = listing.draft.title.ifBlank { stringResource(R.string.owner_untitled) },
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(listing.status.labelRes()),
                    style = FindoraTheme.typography.label,
                    color = if (listing.isLive) FindoraTheme.colors.success else FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.testTag("ownerStatus_$id"),
                )
                if (listing.hasUnpublishedChanges) {
                    Text(
                        text = stringResource(R.string.owner_unpublished_changes),
                        style = FindoraTheme.typography.caption,
                        color = FindoraTheme.colors.accent,
                    )
                }
                Text(
                    text =
                        listOf(
                            pluralStringResource(R.plurals.owner_stat_views, item.stats.views, item.stats.views),
                            pluralStringResource(R.plurals.owner_stat_saves, item.stats.saves, item.stats.saves),
                            pluralStringResource(
                                R.plurals.owner_stat_enquiries,
                                item.stats.enquiries,
                                item.stats.enquiries,
                            ),
                        ).joinToString(" · "),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.testTag("ownerStats_$id"),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs)) {
            RowAction(R.string.owner_edit, "ownerEdit_$id", onClick = onEdit)
            if (listing.isLive) RowAction(R.string.owner_view_listing, "ownerOpen_$id", onClick = onOpen)
            when (listing.status) {
                ListingStatus.Published ->
                    RowAction(
                        R.string.owner_pause,
                        "ownerPause_$id",
                        onClick = { onEvent(OwnerDashboardEvent.RequestPause(id)) },
                    )
                ListingStatus.Paused ->
                    RowAction(
                        R.string.owner_resume,
                        "ownerResume_$id",
                        onClick = { onEvent(OwnerDashboardEvent.Resume(id)) },
                    )
                ListingStatus.Draft -> Unit
            }
            if (listing.status != ListingStatus.Draft) {
                RowAction(
                    R.string.owner_unpublish,
                    "ownerUnpublish_$id",
                    onClick = { onEvent(OwnerDashboardEvent.RequestUnpublish(id)) },
                )
            }
            RowAction(
                R.string.action_delete,
                "ownerDelete_$id",
                onClick = { onEvent(OwnerDashboardEvent.RequestDelete(id)) },
                destructive = true,
            )
        }
    }
}

private const val COVER_ASPECT = 4f / 3f

@Composable
private fun ConfirmDialog(
    pending: PendingAction,
    onEvent: (OwnerDashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body, confirm) =
        when (pending.kind) {
            PendingAction.Kind.Pause ->
                Triple(
                    R.string.owner_confirm_pause_title,
                    R.string.owner_confirm_pause_body,
                    R.string.owner_pause,
                )
            PendingAction.Kind.Unpublish ->
                Triple(
                    R.string.owner_confirm_unpublish_title,
                    R.string.owner_confirm_unpublish_body,
                    R.string.owner_unpublish,
                )
            PendingAction.Kind.Delete ->
                Triple(R.string.owner_confirm_delete_title, R.string.owner_confirm_delete_body, R.string.action_delete)
        }
    AlertDialog(
        onDismissRequest = { onEvent(OwnerDashboardEvent.Dismiss) },
        title = { Text(stringResource(title, pending.title.ifBlank { stringResource(R.string.owner_untitled) })) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(OwnerDashboardEvent.Confirm) },
                modifier = Modifier.testTag("ownerConfirm"),
            ) {
                Text(
                    stringResource(confirm),
                    color =
                        if (pending.kind ==
                            PendingAction.Kind.Delete
                        ) {
                            FindoraTheme.colors.error
                        } else {
                            FindoraTheme.colors.accent
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(OwnerDashboardEvent.Dismiss) }) {
                Text(stringResource(R.string.action_cancel), color = FindoraTheme.colors.onBackground)
            }
        },
        containerColor = FindoraTheme.colors.background,
        modifier = modifier.testTag("ownerConfirmDialog"),
    )
}
