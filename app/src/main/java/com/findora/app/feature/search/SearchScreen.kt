package com.findora.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.ui.SwipeToDeleteRow

private val FieldHeight = 56.dp
private val IconButtonSize = 48.dp
private val IconSize = 22.dp
private val RowMinHeight = 52.dp

@Composable
fun SearchRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SearchUiEffect.Applied -> currentOnClose()
            }
        }
    }
    SearchScreen(state = state, onEvent = viewModel::onEvent, onBack = onClose, modifier = modifier)
}

/**
 * Full-screen search: a focused field (debounced suggestions while typing), recent searches of the current
 * user when the field is empty (tap to search again, swipe to delete, clear all). Submitting applies the
 * text to the Home feed.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .statusBarsPadding()
                .imePadding()
                .testTag("search"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs),
    ) {
        item(key = "field") {
            SearchField(
                query = state.query,
                onQueryChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
                onSubmit = { onEvent(SearchUiEvent.Submit(state.query)) },
                onBack = onBack,
                fieldModifier = Modifier.focusRequester(focusRequester),
            )
        }
        if (state.showRecents) {
            recentItems(state.recents, onEvent)
        } else {
            suggestionItems(state, onEvent)
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(FindoraTheme.spacing.lg)) }
    }
}

private fun LazyListScope.recentItems(
    recents: List<RecentSearch>,
    onEvent: (SearchUiEvent) -> Unit,
) {
    if (recents.isEmpty()) return
    item(key = "recentHeader") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal),
        ) {
            SectionLabel(stringResource(R.string.search_recent), Modifier.weight(1f))
            TextLink(
                text = stringResource(R.string.action_clear_all),
                onClick = { onEvent(SearchUiEvent.ClearRecents) },
                color = FindoraTheme.colors.accent,
                modifier = Modifier.testTag("clearRecents"),
            )
        }
    }
    items(recents, key = { "recent_${it.query.lowercase()}" }) { recent ->
        SwipeToDeleteRow(
            deleteLabel = stringResource(R.string.action_delete_recent),
            onDelete = { onEvent(SearchUiEvent.DeleteRecent(recent.query)) },
            modifier =
                Modifier
                    .animateItem()
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .testTag("recent_${recent.query}"),
        ) {
            ResultRow(
                text = recent.query,
                icon = FindoraIcons.History,
                caption = null,
                onClick = { onEvent(SearchUiEvent.Submit(recent.query)) },
            )
        }
    }
}

private fun LazyListScope.suggestionItems(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
) {
    if (state.suggestions.isNotEmpty()) {
        item(key = "suggestionHeader") {
            SectionLabel(
                text = stringResource(R.string.search_suggestions),
                modifier = Modifier.padding(horizontal = FindoraTheme.spacing.screenHorizontal),
            )
        }
    }
    items(state.suggestions, key = { "suggestion_${it.kind}_${it.text}" }) { suggestion ->
        ResultRow(
            text = suggestion.text,
            icon = if (suggestion.kind == SearchSuggestion.Kind.Locality) FindoraIcons.MapPin else FindoraIcons.Home,
            caption =
                stringResource(
                    if (suggestion.kind == SearchSuggestion.Kind.Locality) R.string.suggestion_locality else R.string.suggestion_property,
                ),
            onClick = { onEvent(SearchUiEvent.Submit(suggestion.text)) },
            modifier =
                Modifier
                    .animateItem()
                    .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                    .testTag("suggestion_${suggestion.text}"),
        )
    }
    if (state.suggestionsLoaded && state.suggestions.isEmpty()) {
        item(key = "noSuggestions") {
            Text(
                text = stringResource(R.string.search_no_suggestions, state.query.trim()),
                style = FindoraTheme.typography.body,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier =
                    Modifier
                        .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.md)
                        .testTag("noSuggestions"),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.sm),
    ) {
        RoundIconButton(
            description = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.testTag("searchBack"),
        ) {
            Icon(FindoraIcons.ArrowBack, null, tint = FindoraTheme.colors.onBackground, modifier = Modifier.size(IconSize))
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .height(FieldHeight)
                    .clip(FindoraTheme.shapes.searchPill)
                    .background(FindoraTheme.colors.surface)
                    .padding(start = FindoraTheme.spacing.md),
        ) {
            Icon(FindoraIcons.Search, null, tint = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.size(IconSize))
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            val placeholder = stringResource(R.string.search_placeholder)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(placeholder, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = FindoraTheme.typography.body.copy(color = FindoraTheme.colors.onBackground),
                    cursorBrush = SolidColor(FindoraTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier =
                        fieldModifier
                            .fillMaxWidth()
                            .semantics { contentDescription = placeholder }
                            .testTag("searchField"),
                )
            }
            if (query.isNotEmpty()) {
                RoundIconButton(
                    description = stringResource(R.string.cd_clear_search),
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("searchClear"),
                ) {
                    Icon(FindoraIcons.Close, null, tint = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.size(IconSize))
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    text: String,
    icon: ImageVector,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.xs),
    ) {
        Icon(icon, null, tint = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.size(IconSize))
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Text(
            text = text,
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (caption != null) {
            Text(caption, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
        }
    }
}

@Composable
private fun RoundIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(IconButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
    ) { content() }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.padding(vertical = FindoraTheme.spacing.xs).semantics { heading() },
    )
}
