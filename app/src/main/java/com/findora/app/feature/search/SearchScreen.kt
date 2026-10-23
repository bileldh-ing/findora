package com.findora.app.feature.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.findora.app.core.designsystem.component.SegmentedControl
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
    val context = LocalContext.current
    val voiceAvailable = remember(context) { isSpeechRecognitionAvailable(context) }
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val prompt = stringResource(R.string.ask_voice_prompt)
    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
                viewModel.onEvent(SearchUiEvent.Dictated(text))
            }
        }
    SearchScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onClose,
        modifier = modifier,
        voiceAvailable = voiceAvailable,
        onVoice = {
            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            try {
                voice.launch(intent)
            } catch (_: ActivityNotFoundException) {
                // The recognizer was uninstalled after the check; the mic simply does nothing this time.
            }
        },
    )
}

/**
 * Voice search uses the system speech recognizer through [RecognizerIntent], so Findora itself needs no
 * microphone permission. The button is only shown when some app on the device answers that intent.
 */
private fun isSpeechRecognitionAvailable(context: Context): Boolean =
    context.packageManager
        .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        .isNotEmpty()

/**
 * Full-screen search with two modes. Places: a focused field (debounced suggestions while typing), recent
 * searches of the current user when the field is empty (tap to search again, swipe to delete, clear all);
 * submitting applies the text to the Home feed. Ask Findora: the text is read as a sentence by the on-device
 * parser and shown as removable chips that Apply turns into the Home filter.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    voiceAvailable: Boolean = false,
    onVoice: () -> Unit = {},
) {
    val asking = state.mode == SearchMode.Ask
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
                placeholder = stringResource(if (asking) R.string.ask_placeholder else R.string.search_placeholder),
                onQueryChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
                onSubmit = {
                    onEvent(
                        if (asking &&
                            state.ask.canApply
                        ) {
                            SearchUiEvent.ApplyAsk
                        } else {
                            SearchUiEvent.Submit(state.query)
                        },
                    )
                },
                onBack = onBack,
                autoFocus = autoFocus,
                onVoice = onVoice.takeIf { voiceAvailable },
            )
        }
        item(key = "mode") {
            SegmentedControl(
                options =
                    listOf(
                        SearchMode.Places to stringResource(R.string.ask_mode_places),
                        SearchMode.Ask to stringResource(R.string.ask_mode_ask),
                    ),
                selected = state.mode,
                onSelect = { onEvent(SearchUiEvent.SetMode(it)) },
                modifier =
                    Modifier
                        .padding(
                            horizontal = FindoraTheme.spacing.screenHorizontal,
                            vertical = FindoraTheme.spacing.xxs,
                        ).testTag("searchMode"),
            )
        }
        if (asking) {
            askItems(state.query, state.ask, onEvent)
        } else if (state.showRecents) {
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
                    if (suggestion.kind ==
                        SearchSuggestion.Kind.Locality
                    ) {
                        R.string.suggestion_locality
                    } else {
                        R.string.suggestion_property
                    },
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
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    autoFocus: Boolean,
    onVoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Requested from the field's own composition so the requester is always attached first.
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
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
            Icon(
                FindoraIcons.ArrowBack,
                null,
                tint = FindoraTheme.colors.onBackground,
                modifier = Modifier.size(IconSize),
            )
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
            Icon(
                FindoraIcons.Search,
                null,
                tint = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = FindoraTheme.typography.body.copy(color = FindoraTheme.colors.onBackground),
                    cursorBrush = SolidColor(FindoraTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    // The placeholder is part of the field (a contentDescription would be read instead of the query).
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = FindoraTheme.typography.body,
                                    color = FindoraTheme.colors.onSurfaceMuted,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester).fillMaxWidth().testTag("searchField"),
                )
            }
            if (query.isNotEmpty()) {
                RoundIconButton(
                    description = stringResource(R.string.cd_clear_search),
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("searchClear"),
                ) {
                    Icon(
                        FindoraIcons.Close,
                        null,
                        tint = FindoraTheme.colors.onSurfaceMuted,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
            if (onVoice != null) {
                RoundIconButton(
                    description = stringResource(R.string.ask_voice),
                    onClick = onVoice,
                    modifier = Modifier.testTag("searchVoice"),
                ) {
                    Icon(
                        FindoraIcons.Mic,
                        null,
                        tint = FindoraTheme.colors.accent,
                        modifier = Modifier.size(IconSize),
                    )
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
