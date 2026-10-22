package com.findora.app.feature.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.StateMessage
import java.time.ZoneId

private val RowIconSize = 22.dp

@Composable
fun ViewingsRoute(
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val cancelled = stringResource(R.string.viewings_cancelled)
    val failed = stringResource(R.string.viewings_cancel_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbarHostState.showSnackbar(
                when (effect) {
                    ViewingsUiEffect.Cancelled -> cancelled
                    ViewingsUiEffect.CancelFailed -> failed
                },
            )
        }
    }
    ViewingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenProperty = onOpenProperty,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Upcoming viewings (cancellable, with confirmation) and past ones; a row opens the listing. */
@Composable
fun ViewingsScreen(
    state: ViewingsUiState,
    onEvent: (ViewingsUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(FindoraTheme.colors.background).testTag("viewings")) {
        Column(Modifier.fillMaxSize()) {
            ViewingTopBar(stringResource(R.string.viewings_title), onBack)
            when (state) {
                ViewingsUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                    }
                is ViewingsUiState.Content ->
                    if (state.isEmpty) {
                        StateMessage(
                            icon = FindoraIcons.Calendar,
                            title = stringResource(R.string.viewings_empty_title),
                            body = stringResource(R.string.viewings_empty_body),
                            modifier = Modifier.testTag("viewingsEmpty"),
                        )
                    } else {
                        ViewingsList(state, onEvent, onOpenProperty)
                    }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    (state as? ViewingsUiState.Content)?.pendingCancel?.let { item ->
        CancelDialog(item = item, zone = state.zone, onEvent = onEvent)
    }
}

@Composable
private fun ViewingsList(
    state: ViewingsUiState.Content,
    onEvent: (ViewingsUiEvent) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        contentPadding =
            PaddingValues(
                start = FindoraTheme.spacing.screenHorizontal,
                end = FindoraTheme.spacing.screenHorizontal,
                bottom = bottomInset + FindoraTheme.spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier = modifier.fillMaxSize().testTag("viewingsList"),
    ) {
        if (state.upcoming.isNotEmpty()) {
            item(key = "upcomingHeader") {
                ViewingSectionTitle(
                    stringResource(R.string.viewings_upcoming),
                    Modifier.padding(top = FindoraTheme.spacing.xs),
                )
            }
            items(state.upcoming, key = { "u${it.viewing.id}" }) { item ->
                ViewingRow(
                    item = item,
                    zone = state.zone,
                    onOpen = { onOpenProperty(item.viewing.propertyId) },
                    onCancel = { onEvent(ViewingsUiEvent.RequestCancel(item.viewing.id)) },
                )
            }
        }
        if (state.past.isNotEmpty()) {
            item(key = "pastHeader") {
                ViewingSectionTitle(
                    stringResource(R.string.viewings_past),
                    Modifier.padding(top = FindoraTheme.spacing.md),
                )
            }
            items(state.past, key = { "p${it.viewing.id}" }) { item ->
                ViewingRow(
                    item = item,
                    zone = state.zone,
                    onOpen = { onOpenProperty(item.viewing.propertyId) },
                    onCancel = null,
                )
            }
        }
    }
}

@Composable
private fun ViewingRow(
    item: ViewingItem,
    zone: ZoneId,
    onOpen: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val title = item.property?.title ?: stringResource(R.string.viewings_listing_unavailable)
    val start = item.viewing.startAt
    val whenText =
        stringResource(
            R.string.booked_when,
            ViewingFormat.mediumDate(start, zone, locale),
            ViewingFormat.time(start, zone, locale),
        )
    val openLabel = stringResource(R.string.action_open_listing)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .clickable(
                    enabled = item.property != null,
                    role = Role.Button,
                    onClickLabel = openLabel,
                    onClick = onOpen,
                ).padding(
                    start = FindoraTheme.spacing.md,
                    top = FindoraTheme.spacing.sm,
                    bottom = FindoraTheme.spacing.sm,
                ).testTag("viewing_${item.viewing.id}"),
    ) {
        Icon(
            FindoraIcons.Calendar,
            contentDescription = null,
            tint = FindoraTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(RowIconSize),
        )
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = FindoraTheme.typography.section, color = FindoraTheme.colors.onBackground)
            Text(text = whenText, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            item.property?.locality?.let {
                Text(text = it, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            }
            if (item.viewing.note.isNotBlank()) {
                Text(
                    text = stringResource(R.string.viewings_note, item.viewing.note),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onBackground,
                )
            }
        }
        if (onCancel != null) {
            TextLink(
                text = stringResource(R.string.viewings_cancel),
                onClick = onCancel,
                color = FindoraTheme.colors.error,
                modifier = Modifier.testTag("cancel_${item.viewing.id}"),
            )
        } else {
            Spacer(Modifier.width(FindoraTheme.spacing.md))
        }
    }
}

@Composable
private fun CancelDialog(
    item: ViewingItem,
    zone: ZoneId,
    onEvent: (ViewingsUiEvent) -> Unit,
) {
    val locale = currentLocale()
    val title = item.property?.title ?: stringResource(R.string.viewings_listing_unavailable)
    val whenText =
        stringResource(
            R.string.booked_when,
            ViewingFormat.mediumDate(item.viewing.startAt, zone, locale),
            ViewingFormat.time(item.viewing.startAt, zone, locale),
        )
    AlertDialog(
        onDismissRequest = { onEvent(ViewingsUiEvent.DismissCancel) },
        title = { Text(stringResource(R.string.viewings_cancel_title)) },
        text = { Text(stringResource(R.string.viewings_cancel_message, title, whenText)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(ViewingsUiEvent.ConfirmCancel) },
                modifier = Modifier.testTag("confirmCancel"),
            ) {
                Text(stringResource(R.string.viewings_cancel), color = FindoraTheme.colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ViewingsUiEvent.DismissCancel) }) {
                Text(stringResource(R.string.viewings_keep), color = FindoraTheme.colors.onBackground)
            }
        },
        containerColor = FindoraTheme.colors.background,
        modifier = Modifier.testTag("cancelDialog"),
    )
}
