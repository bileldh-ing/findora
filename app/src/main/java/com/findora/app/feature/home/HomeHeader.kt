package com.findora.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val HeaderButtonSize = 48.dp
private val HeaderIconSize = 22.dp
private val LocationIconSize = 30.dp
private val ClearIconSize = 18.dp
private val UnreadDotSize = 9.dp
private val SearchPillHeight = 56.dp
private val BadgeSize = 18.dp
private val DividerHeight = 24.dp
private const val BADGE_POP_SCALE = 1.25f

/** Location chip, bell with unread dot and avatar, as in the reference header. */
@Composable
fun HomeTopBar(
    locationLabel: String,
    unreadCount: Int,
    userInitials: String?,
    onLocationClick: () -> Unit,
    onBellClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val locationDescription = stringResource(R.string.cd_change_location, locationLabel)
        // Pin-in-a-house icon followed by the black location pill; the whole group is one button.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = HeaderButtonSize)
                    .clip(FindoraTheme.shapes.chip)
                    .clickable(onClick = onLocationClick)
                    .clearAndSetSemantics {
                        contentDescription = locationDescription
                        role = Role.Button
                    }.testTag("locationChip"),
        ) {
            Icon(
                FindoraIcons.MapPinHouse,
                contentDescription = null,
                tint = FindoraTheme.colors.onBackground,
                modifier = Modifier.size(LocationIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.xs))
            Text(
                text = locationLabel,
                style = FindoraTheme.typography.label,
                color = FindoraTheme.colors.onPrimaryAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.primaryAction)
                        .padding(horizontal = FindoraTheme.spacing.sm, vertical = FindoraTheme.spacing.xxs),
            )
        }
        Spacer(Modifier.weight(1f, fill = true).width(FindoraTheme.spacing.xs))
        val bellDescription =
            if (unreadCount > 0) {
                pluralStringResource(R.plurals.cd_notifications_unread, unreadCount, unreadCount)
            } else {
                stringResource(R.string.cd_notifications)
            }
        CircleButton(onClick = onBellClick, description = bellDescription, modifier = Modifier.testTag("bell")) {
            Box {
                Icon(
                    FindoraIcons.Bell,
                    null,
                    tint = FindoraTheme.colors.onBackground,
                    modifier = Modifier.size(HeaderIconSize),
                )
                if (unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .size(UnreadDotSize)
                            .clip(CircleShape)
                            .background(FindoraTheme.colors.accent)
                            .border(BorderStroke(1.5.dp, FindoraTheme.colors.background), CircleShape)
                            .testTag("unreadDot"),
                    )
                }
            }
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        CircleButton(
            onClick = onAvatarClick,
            description = stringResource(R.string.cd_profile),
            filled = true,
            modifier = Modifier.testTag("avatar"),
        ) {
            if (userInitials != null) {
                Text(
                    userInitials,
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onPrimaryAction,
                )
            } else {
                Icon(
                    FindoraIcons.User,
                    null,
                    tint = FindoraTheme.colors.onPrimaryAction,
                    modifier = Modifier.size(HeaderIconSize),
                )
            }
        }
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(HeaderButtonSize)
                .clip(CircleShape)
                .then(
                    Modifier.background(if (filled) FindoraTheme.colors.primaryAction else FindoraTheme.colors.surface),
                ).clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
    ) { content() }
}

/** "Discover" over "Your New House"; [collapse] (0 to 1) fades and shrinks it as the feed scrolls. */
@Composable
fun HomeTitle(
    collapse: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = 1f - collapse
                    val scale = 1f - collapse * TITLE_SHRINK
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }.semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            text = stringResource(R.string.home_discover),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        Text(
            text = stringResource(R.string.home_new_house),
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
        )
    }
}

private const val TITLE_SHRINK = 0.12f

/** Search pill with the query (or placeholder), a clear button and the filter icon with its count badge. */
@Composable
fun SearchPill(
    query: String,
    activeFilters: Int,
    onSearchClick: () -> Unit,
    onClearQuery: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SearchPillHeight)
                .clip(FindoraTheme.shapes.searchPill)
                .background(FindoraTheme.colors.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .height(SearchPillHeight)
                    .clickable(role = Role.Button, onClick = onSearchClick)
                    .padding(start = FindoraTheme.spacing.md)
                    .testTag("searchPill"),
        ) {
            Icon(
                FindoraIcons.Search,
                null,
                tint = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(HeaderIconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Text(
                text = query.ifBlank { stringResource(R.string.search_placeholder) },
                style = FindoraTheme.typography.body,
                color = if (query.isBlank()) FindoraTheme.colors.onSurfaceMuted else FindoraTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (query.isNotBlank()) {
            val clearDescription = stringResource(R.string.cd_clear_search)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(HeaderButtonSize)
                        .clip(CircleShape)
                        .clickable(onClick = onClearQuery)
                        .clearAndSetSemantics {
                            contentDescription = clearDescription
                            role = Role.Button
                        }.testTag("clearQuery"),
            ) {
                Icon(
                    FindoraIcons.Close,
                    null,
                    tint = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.size(ClearIconSize),
                )
            }
        }
        Box(Modifier.width(1.dp).height(DividerHeight).background(FindoraTheme.colors.outline))
        FilterButton(activeFilters = activeFilters, onClick = onFilterClick)
    }
}

@Composable
private fun FilterButton(
    activeFilters: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        if (activeFilters >
            0
        ) {
            pluralStringResource(R.plurals.cd_filters_active, activeFilters, activeFilters)
        } else {
            stringResource(R.string.cd_filters)
        }
    // The badge pops slightly every time the count changes.
    val pop = remember { Animatable(1f) }
    val bouncy = FindoraTheme.motion.bouncy<Float>()
    LaunchedEffect(activeFilters) {
        if (activeFilters > 0) {
            pop.snapTo(BADGE_POP_SCALE)
            pop.animateTo(1f, bouncy)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(SearchPillHeight)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }.testTag("filterButton"),
    ) {
        Icon(
            FindoraIcons.Sliders,
            null,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(HeaderIconSize),
        )
        AnimatedVisibility(
            visible = activeFilters > 0,
            enter = scaleIn(FindoraTheme.motion.bouncy()),
            exit = scaleOut(),
            modifier =
                Modifier
                    .align(
                        Alignment.TopEnd,
                    ).padding(top = FindoraTheme.spacing.xs, end = FindoraTheme.spacing.xs),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(BadgeSize)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }.clip(CircleShape)
                        .background(FindoraTheme.colors.primaryAction)
                        .testTag("filterBadge"),
            ) {
                Text(
                    text = activeFilters.toString(),
                    style = FindoraTheme.typography.label,
                    color = FindoraTheme.colors.onPrimaryAction,
                )
            }
        }
    }
}

/** Compact search bar pinned to the top once the big header has scrolled away. */
@Composable
fun CompactSearchBar(
    query: String,
    activeFilters: Int,
    onSearchClick: () -> Unit,
    onClearQuery: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(FindoraTheme.colors.background)
            .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.xs),
    ) {
        SearchPill(
            query = query,
            activeFilters = activeFilters,
            onSearchClick = onSearchClick,
            onClearQuery = onClearQuery,
            onFilterClick = onFilterClick,
        )
    }
}

/** Section heading row ("Nearby Rental Houses"). */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(
            text = text,
            style = FindoraTheme.typography.section,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}
