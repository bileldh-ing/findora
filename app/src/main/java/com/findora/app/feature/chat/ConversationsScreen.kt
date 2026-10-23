package com.findora.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.ui.StateMessage
import com.findora.app.core.ui.TimeLabels
import com.findora.app.core.ui.appLocale
import java.time.LocalDate
import java.time.ZoneId

private val RowAvatarSize = 52.dp
private val BadgeMinSize = 22.dp
private const val MAX_BADGE = 99

@Composable
fun ConversationsRoute(
    onOpenConversation: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConversationsScreen(
        state = state,
        onOpenConversation = onOpenConversation,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/** The Chat tab: one row per thread with the agent, the listing, the last message, its time and unread count. */
@Composable
fun ConversationsScreen(
    state: ConversationsUiState,
    onOpenConversation: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val horizontal = FindoraTheme.spacing.screenHorizontal
    Column(
        modifier
            .fillMaxSize()
            .background(FindoraTheme.colors.background)
            .padding(top = contentPadding.calculateTopPadding())
            .testTag("conversations"),
    ) {
        Column(Modifier.padding(horizontal = horizontal, vertical = FindoraTheme.spacing.md)) {
            Text(
                text = stringResource(R.string.chat_title),
                style = FindoraTheme.typography.headline,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            DemoAgentLabel()
        }
        when (state) {
            ConversationsUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                }
            is ConversationsUiState.Content ->
                if (state.conversations.isEmpty()) {
                    StateMessage(
                        icon = FindoraIcons.Chat,
                        title = stringResource(R.string.chat_empty_title),
                        body = stringResource(R.string.chat_empty_body),
                        modifier =
                            Modifier.padding(bottom = contentPadding.calculateBottomPadding()).testTag("chatEmpty"),
                    )
                } else {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                start = horizontal,
                                end = horizontal,
                                bottom = contentPadding.calculateBottomPadding() + FindoraTheme.spacing.md,
                            ),
                        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
                        modifier = Modifier.fillMaxSize().testTag("conversationList"),
                    ) {
                        items(state.conversations, key = { it.conversation.id }) { summary ->
                            ConversationRow(
                                summary = summary,
                                typing = summary.conversation.id in state.typing,
                                zone = state.zone,
                                today = state.today,
                                onClick = { onOpenConversation(summary.conversation.id) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun ConversationRow(
    summary: ConversationSummary,
    typing: Boolean,
    zone: ZoneId,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val conversation = summary.conversation
    val last = summary.lastMessage
    val preview =
        when {
            typing -> stringResource(R.string.chat_typing)
            last.fromUser -> stringResource(R.string.chat_you_prefix, last.body)
            else -> last.body
        }
    val time = TimeLabels.listLabel(last.sentAt, zone, today, appLocale())
    val unread = summary.unreadCount
    val unreadText = if (unread > 0) pluralStringResource(R.plurals.chat_unread_cd, unread, unread) else ""
    val description =
        listOf(conversation.agent.name, conversation.propertyTitle, preview, time, unreadText)
            .filter { it.isNotEmpty() }
            .joinToString(". ")
    val openLabel = stringResource(R.string.chat_open_conversation)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
                .clearAndSetSemantics { contentDescription = description }
                .padding(vertical = FindoraTheme.spacing.sm, horizontal = FindoraTheme.spacing.xxs)
                .testTag("conversation_${conversation.id}"),
    ) {
        AgentAvatar(conversation.agent, RowAvatarSize)
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.agent.name,
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = time,
                    style = FindoraTheme.typography.label,
                    color = if (unread > 0) FindoraTheme.colors.accent else FindoraTheme.colors.onSurfaceMuted,
                )
            }
            Text(
                text = conversation.propertyTitle,
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    style =
                        if (unread > 0) {
                            FindoraTheme.typography.body.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            FindoraTheme.typography.body
                        },
                    color = if (typing) FindoraTheme.colors.accent else FindoraTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(FindoraTheme.spacing.xs))
                    UnreadBadge(unread, Modifier.testTag("unread_${conversation.id}"))
                }
            }
        }
    }
}

/** A blue pill with the unread count (99+ beyond that). */
@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .defaultMinSize(minWidth = BadgeMinSize, minHeight = BadgeMinSize)
                .clip(CircleShape)
                .background(FindoraTheme.colors.accent)
                .padding(horizontal = 6.dp),
    ) {
        Text(
            text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
            style = FindoraTheme.typography.label,
            color = FindoraTheme.colors.onPhoto,
        )
    }
}
