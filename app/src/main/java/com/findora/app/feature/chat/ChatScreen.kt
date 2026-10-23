package com.findora.app.feature.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.QuickReply
import com.findora.app.core.ui.StateMessage
import com.findora.app.core.ui.dayLabel

private val TopAvatarSize = 40.dp
private val SendButtonSize = 48.dp
private val ComposerMaxHeight = 140.dp

@Composable
fun ChatRoute(
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(ChatUiEvent.VisibilityChanged(true))
        onPauseOrDispose { viewModel.onEvent(ChatUiEvent.VisibilityChanged(false)) }
    }
    // Replies can arrive after the user leaves, so POST_NOTIFICATIONS (Android 13+) is asked with the first
    // message of a thread; the message is sent whatever the answer.
    var pendingSend by remember { mutableStateOf<ChatUiEvent?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingSend?.let(viewModel::onEvent)
            pendingSend = null
        }
    val onEvent: (ChatUiEvent) -> Unit = { event ->
        val firstMessage =
            (event is ChatUiEvent.Send || event is ChatUiEvent.SendQuickReply) &&
                (state as? ChatUiState.Content)?.conversationId == null
        if (firstMessage && needsNotificationPermission(context)) {
            pendingSend = event
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(event)
        }
    }
    ChatScreen(
        state = state,
        onEvent = onEvent,
        onBack = onBack,
        onOpenProperty = onOpenProperty,
        modifier = modifier,
    )
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/**
 * A thread with a listing's demo agent: the agent and the "automated replies" label in the top bar, the listing
 * card, messages grouped by day with delivery ticks, the typing indicator, quick replies and the composer.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(FindoraTheme.colors.background).testTag("chat")) {
        when (state) {
            ChatUiState.Loading -> {
                ChatTopBar(title = "", agent = null, onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                }
            }
            ChatUiState.NotFound -> {
                ChatTopBar(title = stringResource(R.string.chat_title), agent = null, onBack = onBack)
                StateMessage(
                    icon = FindoraIcons.Chat,
                    title = stringResource(R.string.chat_not_found_title),
                    body = stringResource(R.string.chat_not_found_body),
                    actionLabel = stringResource(R.string.back),
                    onAction = onBack,
                    modifier = Modifier.testTag("chatNotFound"),
                )
            }
            is ChatUiState.Content -> ChatContent(state, onEvent, onBack, onOpenProperty)
        }
    }
}

@Composable
private fun ChatContent(
    state: ChatUiState.Content,
    onEvent: (ChatUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontal = FindoraTheme.spacing.screenHorizontal
    Column(modifier.fillMaxSize().imePadding()) {
        ChatTopBar(title = state.agent.name, agent = state.agent, onBack = onBack)
        ListingAttachmentCard(
            title = state.propertyTitle,
            property = state.property,
            onOpen = { onOpenProperty(state.propertyId) },
            modifier = Modifier.padding(horizontal = horizontal, vertical = FindoraTheme.spacing.xs),
        )
        MessageList(state, Modifier.weight(1f))
        QuickReplies(onSend = { onEvent(ChatUiEvent.SendQuickReply(it)) })
        Composer(
            draft = state.draft,
            canSend = state.canSend,
            onDraftChange = { onEvent(ChatUiEvent.DraftChanged(it)) },
            onSend = { onEvent(ChatUiEvent.Send) },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    agent: ChatAgent?,
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
                    .size(MinTouch)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .testTag("chatBack"),
        ) {
            Icon(FindoraIcons.ArrowBack, contentDescription = null, tint = FindoraTheme.colors.onBackground)
        }
        if (agent != null) {
            AgentAvatar(agent, TopAvatarSize)
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(
                text = title,
                style = FindoraTheme.typography.section,
                color = FindoraTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (agent != null) DemoAgentLabel()
        }
    }
}

@Composable
private fun MessageList(
    state: ChatUiState.Content,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reduceMotion = rememberReducedMotion()
    val count = state.items.size + if (state.agentTyping) 1 else 0
    LaunchedEffect(count) {
        if (count > 0) {
            if (reduceMotion) listState.scrollToItem(count - 1) else listState.animateScrollToItem(count - 1)
        }
    }
    if (state.isEmpty && !state.agentTyping) {
        Box(modifier.fillMaxWidth().padding(FindoraTheme.spacing.xl), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chat_start_hint, state.agent.name),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("chatStartHint"),
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        contentPadding =
            PaddingValues(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().testTag("chatMessages"),
    ) {
        items(state.items, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is ChatItem.DaySeparator -> DaySeparator(dayLabel(item.date, state.today))
                is ChatItem.Bubble -> MessageBubble(item.message, state.agent.name, state.zone)
            }
        }
        if (state.agentTyping) {
            item(key = "typing") { TypingIndicator(state.agent.name, reduceMotion) }
        }
    }
}

@Composable
private fun QuickReplies(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.chat_quick_replies_cd)
    LazyRow(
        contentPadding = PaddingValues(horizontal = FindoraTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = FindoraTheme.spacing.xxs)
                .semantics { contentDescription = label }
                .testTag("quickReplies"),
    ) {
        items(QuickReply.entries, key = { it.name }) { reply ->
            val text = quickReplyText(reply)
            QuickReplyChip(text = text, onClick = { onSend(text) }, modifier = Modifier.testTag("quick_${reply.name}"))
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.chat_input_hint)
    val sendLabel = stringResource(R.string.chat_send)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTouch, max = ComposerMaxHeight)
                    .clip(FindoraTheme.shapes.searchPill)
                    .background(FindoraTheme.colors.surface)
                    .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.sm),
        ) {
            if (draft.isEmpty()) {
                Text(hint, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted)
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = FindoraTheme.typography.body.copy(color = FindoraTheme.colors.onBackground),
                cursorBrush = SolidColor(FindoraTheme.colors.onBackground),
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = hint }
                        .testTag("chatInput"),
            )
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        val enabledColor = FindoraTheme.colors.primaryAction
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(SendButtonSize)
                    .clip(CircleShape)
                    .background(if (canSend) enabledColor else FindoraTheme.colors.surface)
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = sendLabel, onClick = onSend)
                    .semantics { contentDescription = sendLabel }
                    .testTag("chatSend"),
        ) {
            Icon(
                FindoraIcons.Send,
                contentDescription = null,
                tint = if (canSend) FindoraTheme.colors.onPrimaryAction else FindoraTheme.colors.onSurfaceMuted,
            )
        }
    }
}
