package com.findora.app.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.chat.QuickReply
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.ListingCoverPhoto
import com.findora.app.core.ui.TimeLabels
import com.findora.app.core.ui.appLocale
import com.findora.app.core.ui.rentPerMonthText
import java.time.ZoneId

private val BubbleMaxWidth = 300.dp
private val BubbleRadius = 20.dp
private val BubbleTail = 6.dp
private val StatusIconSize = 14.dp
private val CardPhotoSize = 56.dp
private val TypingDotSize = 7.dp
private const val TYPING_DOT_MILLIS = 450
private const val TYPING_DIM_ALPHA = 0.3f

/** The agent's initials on their colour (never a photo of a person). */
@Composable
fun AgentAvatar(
    agent: ChatAgent,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(Color(agent.avatar.backgroundArgb)),
    ) {
        Text(agent.avatar.initials, style = FindoraTheme.typography.bodyMedium, color = Color.White)
    }
}

/** The small "Demo agent — automated replies" label, shown wherever the agent speaks. */
@Composable
fun DemoAgentLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.chat_demo_label),
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.onSurfaceMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag("demoAgentLabel"),
    )
}

@Composable
internal fun statusLabel(status: DeliveryStatus): String =
    stringResource(
        when (status) {
            DeliveryStatus.Sending -> R.string.chat_status_sending
            DeliveryStatus.Sent -> R.string.chat_status_sent
            DeliveryStatus.Read -> R.string.chat_status_read
        },
    )

/**
 * One message. The user's sit at the end side in the primary colour, the agent's at the start side on the
 * surface colour; start / end and the tail corner follow the layout direction, so Arabic mirrors them. TalkBack
 * reads one sentence: sender, time, text and (for the user's) delivery state.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    agentName: String,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val time = TimeLabels.time(message.sentAt, zone, appLocale())
    val status = statusLabel(message.status)
    val description =
        if (message.fromUser) {
            stringResource(R.string.chat_bubble_you_cd, time, message.body, status)
        } else {
            stringResource(R.string.chat_bubble_agent_cd, agentName, time, message.body)
        }
    val background = if (message.fromUser) FindoraTheme.colors.primaryAction else FindoraTheme.colors.surface
    val content = if (message.fromUser) FindoraTheme.colors.onPrimaryAction else FindoraTheme.colors.onBackground
    val shape =
        RoundedCornerShape(
            topStart = BubbleRadius,
            topEnd = BubbleRadius,
            bottomStart = if (message.fromUser) BubbleRadius else BubbleTail,
            bottomEnd = if (message.fromUser) BubbleTail else BubbleRadius,
        )
    Row(
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier =
                Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .clip(shape)
                    .background(background)
                    .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xs)
                    .testTag("bubble_${message.id}")
                    .clearAndSetSemantics { contentDescription = description },
        ) {
            Text(
                text = message.body,
                // Direction follows the message's own script, so an English message keeps its punctuation in an
                // Arabic UI (and vice versa) instead of inheriting the layout direction.
                style = FindoraTheme.typography.body.copy(textDirection = TextDirection.Content),
                color = content,
                modifier = Modifier.align(Alignment.Start),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = FindoraTheme.typography.label, color = content.copy(alpha = META_ALPHA))
                if (message.fromUser) {
                    Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                    DeliveryIcon(message.status, content)
                }
            }
        }
    }
}

private const val META_ALPHA = 0.7f

@Composable
private fun DeliveryIcon(
    status: DeliveryStatus,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val (icon, color) =
        when (status) {
            DeliveryStatus.Sending -> FindoraIcons.Clock to tint.copy(alpha = META_ALPHA)
            DeliveryStatus.Sent -> FindoraIcons.Check to tint.copy(alpha = META_ALPHA)
            DeliveryStatus.Read -> FindoraIcons.CheckDouble to FindoraTheme.colors.accent
        }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(StatusIconSize),
    )
}

/** Centered day header ("Today", "Yesterday", or the date). */
@Composable
fun DaySeparator(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(vertical = FindoraTheme.spacing.xs), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = FindoraTheme.typography.label,
            color = FindoraTheme.colors.onSurfaceMuted,
            modifier =
                Modifier
                    .clip(FindoraTheme.shapes.chip)
                    .background(FindoraTheme.colors.surface)
                    .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs)
                    .semantics { heading() },
        )
    }
}

/** Three dots in an agent bubble, pulsing unless animations are off; announced politely once. */
@Composable
fun TypingIndicator(
    agentName: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.chat_typing_cd, agentName)
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = DOT_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(tween(TYPING_DOT_MILLIS * DOT_COUNT), RepeatMode.Restart),
        label = "typingPhase",
    )
    Row(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(BubbleRadius, BubbleRadius, BubbleRadius, BubbleTail))
                    .background(FindoraTheme.colors.surface)
                    .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.sm)
                    .testTag("typingIndicator")
                    .clearAndSetSemantics {
                        contentDescription = description
                        liveRegion = LiveRegionMode.Polite
                    },
        ) {
            repeat(DOT_COUNT) { index ->
                val lit = reduceMotion || phase.toInt() == index
                Box(
                    Modifier
                        .size(TypingDotSize)
                        .alpha(if (lit) 1f else TYPING_DIM_ALPHA)
                        .clip(CircleShape)
                        .background(FindoraTheme.colors.onSurfaceMuted),
                )
            }
        }
    }
}

private const val DOT_COUNT = 3

/** The listing the thread is about, pinned under the top bar; tapping it opens the listing. */
@Composable
fun ListingAttachmentCard(
    title: String,
    property: Property?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.chat_open_listing)
    val locale = appLocale()
    val price = property?.let { rentPerMonthText(it.rent, locale) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.surface)
                .then(
                    if (property != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                    } else {
                        Modifier
                    },
                ).semantics(mergeDescendants = true) {}
                .padding(FindoraTheme.spacing.xs)
                .testTag("chatListingCard"),
    ) {
        if (property != null) {
            Box(Modifier.size(CardPhotoSize).clip(RoundedCornerShape(14.dp))) { ListingCoverPhoto(property) }
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = FindoraTheme.typography.bodyMedium,
                color = FindoraTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            price?.let {
                Text(it, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            }
        }
        if (property != null) {
            Icon(FindoraIcons.ChevronEnd, contentDescription = null, tint = FindoraTheme.colors.onSurfaceMuted)
        }
    }
}

@Composable
internal fun quickReplyText(reply: QuickReply): String =
    stringResource(
        when (reply) {
            QuickReply.StillAvailable -> R.string.chat_quick_still_available
            QuickReply.VisitThisWeek -> R.string.chat_quick_visit_week
            QuickReply.PriceNegotiable -> R.string.chat_quick_negotiable
        },
    )

/** An outlined pill that sends its text as a message. */
@Composable
fun QuickReplyChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .defaultMinSize(minHeight = MinTouch)
                .clip(FindoraTheme.shapes.chip)
                .border(1.dp, FindoraTheme.colors.outline, FindoraTheme.shapes.chip)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.md),
    ) {
        Text(text, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onBackground, maxLines = 1)
    }
}

internal val MinTouch = 48.dp
