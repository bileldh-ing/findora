package com.findora.app.feature.notifications

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.notification.AppNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.notification.NotificationTarget
import com.findora.app.core.ui.StateMessage
import com.findora.app.core.ui.SwipeToDeleteRow
import com.findora.app.core.ui.TimeLabels
import com.findora.app.core.ui.appLocale
import java.time.LocalDate
import java.time.ZoneId

private val KindIconBox = 44.dp
private val UnreadDot = 9.dp

@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSavedSearch: (Long) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted = stringResource(R.string.notifications_deleted)
    val openConversation by rememberUpdatedState(onOpenConversation)
    val openProperty by rememberUpdatedState(onOpenProperty)
    val openSavedSearch by rememberUpdatedState(onOpenSavedSearch)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationsUiEffect.Navigate ->
                    when (val target = effect.target) {
                        is NotificationTarget.Chat -> openConversation(target.conversationId)
                        is NotificationTarget.Property -> openProperty(target.propertyId)
                        is NotificationTarget.SavedSearch -> openSavedSearch(target.searchId)
                    }
                NotificationsUiEffect.Deleted -> snackbarHostState.showSnackbar(deleted)
            }
        }
    }
    NotificationsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** The notifications center: newest first, unread ones bold with a dot; tap opens, swipe deletes. */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onEvent: (NotificationsUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? NotificationsUiState.Content
    Box(modifier.fillMaxSize().background(FindoraTheme.colors.background).testTag("notifications")) {
        Column(Modifier.fillMaxSize()) {
            NotificationsTopBar(
                canMarkAll = (content?.unreadCount ?: 0) > 0,
                onMarkAll = { onEvent(NotificationsUiEvent.MarkAllRead) },
                onBack = onBack,
            )
            when {
                content == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                    }
                content.notifications.isEmpty() ->
                    StateMessage(
                        icon = FindoraIcons.Bell,
                        title = stringResource(R.string.notifications_empty),
                        body = stringResource(R.string.notifications_empty_body),
                        modifier = Modifier.testTag("notificationsEmpty"),
                    )
                else -> NotificationList(content, onEvent)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun NotificationsTopBar(
    canMarkAll: Boolean,
    onMarkAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backLabel = stringResource(R.string.back)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FindoraTheme.spacing.xs, vertical = FindoraTheme.spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .testTag("notificationsBack"),
        ) {
            Icon(FindoraIcons.ArrowBack, contentDescription = null, tint = FindoraTheme.colors.onBackground)
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(
            text = stringResource(R.string.notifications_title),
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextLink(
            text = stringResource(R.string.notifications_mark_all_read),
            onClick = onMarkAll,
            enabled = canMarkAll,
            color = if (canMarkAll) FindoraTheme.colors.accent else FindoraTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(end = FindoraTheme.spacing.sm).testTag("markAllRead"),
        )
    }
}

@Composable
private fun NotificationList(
    state: NotificationsUiState.Content,
    onEvent: (NotificationsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val deleteLabel = stringResource(R.string.notifications_delete)
    LazyColumn(
        contentPadding =
            PaddingValues(
                start = FindoraTheme.spacing.screenHorizontal,
                end = FindoraTheme.spacing.screenHorizontal,
                bottom = bottomInset + FindoraTheme.spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.fillMaxSize().testTag("notificationList"),
    ) {
        items(state.notifications, key = { it.id }) { notification ->
            SwipeToDeleteRow(
                deleteLabel = deleteLabel,
                onDelete = { onEvent(NotificationsUiEvent.Delete(notification.id)) },
                modifier = Modifier.animateItem(),
            ) {
                NotificationRow(
                    notification = notification,
                    zone = state.zone,
                    today = state.today,
                    onClick = { onEvent(NotificationsUiEvent.Open(notification)) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    zone: ZoneId,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    val title =
        when (notification.kind) {
            NotificationKind.NewMessage -> stringResource(R.string.notification_message_title, notification.subject)
            NotificationKind.ViewingReminder -> stringResource(R.string.notification_reminder_title)
            NotificationKind.ViewingBooked -> stringResource(R.string.notification_booked_title)
            NotificationKind.NewMatches ->
                pluralStringResource(
                    R.plurals.search_alert_title,
                    notification.count.coerceAtLeast(1),
                    notification.count.coerceAtLeast(1),
                    notification.subject,
                )
            NotificationKind.PriceDrop -> stringResource(R.string.price_drop_title, notification.subject)
        }
    val body =
        when (notification.kind) {
            NotificationKind.NewMessage, NotificationKind.PriceDrop -> notification.preview
            NotificationKind.NewMatches -> stringResource(R.string.search_alert_body, notification.preview)
            NotificationKind.ViewingReminder, NotificationKind.ViewingBooked ->
                notification.eventAt
                    ?.let {
                        stringResource(
                            R.string.notification_viewing_body,
                            notification.subject,
                            TimeLabels.dateTime(it, zone, locale),
                        )
                    }
                    ?: notification.subject
        }
    val time = TimeLabels.listLabel(notification.createdAt, zone, today, locale)
    val unreadLabel = stringResource(R.string.notification_unread)
    val description =
        stringResource(R.string.notification_cd, title, body, time).let {
            if (notification.isRead) it else "$unreadLabel. $it"
        }
    val openLabel =
        stringResource(
            when (notification.target) {
                is NotificationTarget.Chat -> R.string.chat_open_conversation
                is NotificationTarget.SavedSearch -> R.string.saved_search_run
                is NotificationTarget.Property -> R.string.chat_open_listing
            },
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(if (notification.isRead) FindoraTheme.colors.background else FindoraTheme.colors.surface)
                .testTag("notification_${notification.id}")
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
                .clearAndSetSemantics { contentDescription = description }
                .padding(FindoraTheme.spacing.sm),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(KindIconBox).clip(CircleShape).background(FindoraTheme.colors.secondaryAction),
        ) {
            Icon(kindIcon(notification.kind), contentDescription = null, tint = FindoraTheme.colors.onBackground)
        }
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style =
                        FindoraTheme.typography.bodyMedium.copy(
                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        ),
                    color = FindoraTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(time, style = FindoraTheme.typography.label, color = FindoraTheme.colors.onSurfaceMuted)
            }
            Text(
                text = body,
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!notification.isRead) {
            Spacer(Modifier.width(FindoraTheme.spacing.xs))
            Box(
                Modifier
                    .size(UnreadDot)
                    .clip(CircleShape)
                    .background(FindoraTheme.colors.accent),
            )
        }
    }
}

private fun kindIcon(kind: NotificationKind): ImageVector =
    when (kind) {
        NotificationKind.NewMessage -> FindoraIcons.Chat
        NotificationKind.ViewingReminder -> FindoraIcons.Clock
        NotificationKind.ViewingBooked -> FindoraIcons.Calendar
        NotificationKind.NewMatches -> FindoraIcons.Bookmark
        NotificationKind.PriceDrop -> FindoraIcons.TrendingDown
    }
