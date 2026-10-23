package com.findora.app.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Agent
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.PropertyCard
import kotlinx.coroutines.launch

private const val MINUTES_PER_HOUR = 60
private val AvatarSize = 48.dp
private val SmallIconSize = 16.dp
private val MapIconSize = 22.dp
private val SheetBadgeSize = 28.dp
private val SimilarCardWidth = 200.dp

/** Explains what the verified badge checks (and what it does not). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FindoraTheme.shapes.bottomSheetTop,
        containerColor = FindoraTheme.colors.background,
        modifier = modifier.testTag("verificationSheet"),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .padding(bottom = FindoraTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    rememberVectorPainter(FindoraIcons.VerifiedBadge),
                    contentDescription = null,
                    modifier = Modifier.size(SheetBadgeSize),
                )
                Spacer(Modifier.width(FindoraTheme.spacing.xs))
                SectionTitle(stringResource(R.string.detail_verified_title))
            }
            Text(
                text = stringResource(R.string.detail_verified_body),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onBackground,
            )
            PrimaryPillButton(
                text = stringResource(R.string.detail_verified_ok),
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Initials avatar, name, typical response time, "Message agent" (in-app chat with the demo agent) and Call
 * (dialer) / Email (mail app) actions.
 */
@Composable
fun AgentCard(
    agent: Agent,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: () -> Unit = {},
) {
    val response =
        if (agent.responseTimeMinutes < MINUTES_PER_HOUR) {
            stringResource(R.string.detail_agent_response_minutes, agent.responseTimeMinutes)
        } else {
            stringResource(R.string.detail_agent_response_hours, agent.responseTimeMinutes / MINUTES_PER_HOUR)
        }
    val callLabel = stringResource(R.string.detail_call_cd, agent.name)
    val emailLabel = stringResource(R.string.detail_email_cd, agent.name)
    val messageLabel = stringResource(R.string.detail_message_cd, agent.name)
    Column(modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal)) {
        SectionTitle(stringResource(R.string.detail_agent))
        Spacer(Modifier.size(FindoraTheme.spacing.sm))
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FindoraTheme.shapes.card)
                    .background(FindoraTheme.colors.surface)
                    .padding(FindoraTheme.spacing.md)
                    .testTag("detailAgent"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(AvatarSize)
                            .clip(CircleShape)
                            .background(Color(agent.avatar.backgroundArgb)),
                ) {
                    Text(agent.avatar.initials, style = FindoraTheme.typography.bodyMedium, color = Color.White)
                }
                Spacer(Modifier.width(FindoraTheme.spacing.sm))
                Column {
                    Text(
                        agent.name,
                        style = FindoraTheme.typography.bodyMedium,
                        color = FindoraTheme.colors.onBackground,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            FindoraIcons.Clock,
                            contentDescription = null,
                            tint = FindoraTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(SmallIconSize),
                        )
                        Spacer(Modifier.width(FindoraTheme.spacing.xxs))
                        Text(
                            response,
                            style = FindoraTheme.typography.caption,
                            color = FindoraTheme.colors.onSurfaceMuted,
                        )
                    }
                }
            }
            PrimaryPillButton(
                text = stringResource(R.string.detail_message_agent),
                onClick = onMessage,
                modifier =
                    Modifier.fillMaxWidth().semantics { contentDescription = messageLabel }.testTag("detailMessage"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                SecondaryPillButton(
                    text = stringResource(R.string.detail_call),
                    onClick = onCall,
                    modifier = Modifier.weight(1f).semantics { contentDescription = callLabel }.testTag("detailCall"),
                )
                SecondaryPillButton(
                    text = stringResource(R.string.detail_email),
                    onClick = onEmail,
                    modifier = Modifier.weight(1f).semantics { contentDescription = emailLabel }.testTag("detailEmail"),
                )
            }
        }
    }
}

/** Locality and coordinates with a button that hands the point to any installed maps app (no API key). */
@Composable
fun LocationSection(
    property: Property,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FindoraTheme.spacing.screenHorizontal,
                ).testTag("detailLocation"),
    ) {
        SectionTitle(stringResource(R.string.detail_location))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(
                FindoraIcons.FoldedMap,
                contentDescription = null,
                tint = FindoraTheme.colors.onBackground,
                modifier = Modifier.size(MapIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Column {
                Text(
                    property.locality,
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onBackground,
                )
                Text(
                    text = stringResource(R.string.detail_coordinates, formatCoordinates(property.location)),
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        SecondaryPillButton(
            text = stringResource(R.string.detail_open_maps),
            onClick = onOpenMap,
            modifier = Modifier.fillMaxWidth().testTag("detailOpenMap"),
        )
    }
}

/** Horizontal row of homes of the same type in the same region; each opens its own detail page. */
@Composable
fun SimilarSection(
    homes: List<Property>,
    favoriteIds: Set<String>,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(
            stringResource(R.string.detail_similar),
            Modifier.padding(horizontal = FindoraTheme.spacing.screenHorizontal),
        )
        Spacer(Modifier.size(FindoraTheme.spacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = FindoraTheme.spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
            modifier = Modifier.fillMaxWidth().testTag("detailSimilar"),
        ) {
            items(homes, key = { it.id }) { home ->
                PropertyCard(
                    property = home,
                    isFavorite = home.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(home.id) },
                    onClick = { onOpen(home.id) },
                    modifier = Modifier.width(SimilarCardWidth),
                )
            }
        }
    }
}
