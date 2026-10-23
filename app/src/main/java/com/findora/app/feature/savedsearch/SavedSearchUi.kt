package com.findora.app.feature.savedsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.SegmentedControl
import com.findora.app.core.designsystem.component.SwitchRow
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.HomeCategory
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.feature.home.labelRes

private val IconSize = 20.dp

/**
 * The name being typed in [SaveSearchDialog]: capped at [SavedSearch.MAX_NAME_LENGTH] characters while typing, and
 * saved trimmed. [nameToSave] is null while the name is blank, which disables Save.
 */
@Stable
class SearchNameState(
    initialName: String,
) {
    var name: String by mutableStateOf(initialName.take(SavedSearch.MAX_NAME_LENGTH))
        private set

    val nameToSave: String?
        get() = name.trim().takeIf { it.isNotEmpty() }

    fun onNameChange(value: String) {
        name = value.take(SavedSearch.MAX_NAME_LENGTH)
    }

    companion object {
        val Saver: Saver<SearchNameState, String> = Saver(save = { it.name }, restore = { SearchNameState(it) })
    }
}

/**
 * Names a search before saving it (and renames a saved one). The name is trimmed; Save is disabled while it is
 * blank, and the field is limited to [SavedSearch.MAX_NAME_LENGTH] characters.
 */
@Composable
fun SaveSearchDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.saved_search_save_title),
    confirmLabel: String = stringResource(R.string.saved_search_save),
) {
    val nameState = rememberSaveable(saver = SearchNameState.Saver) { SearchNameState(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("saveSearchDialog"),
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = { SaveSearchNameField(state = nameState, onSave = onConfirm) },
        confirmButton = {
            TextButton(
                onClick = { nameState.nameToSave?.let(onConfirm) },
                enabled = nameState.nameToSave != null,
                modifier = Modifier.testTag("saveSearchConfirm"),
            ) {
                Text(confirmLabel, color = FindoraTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = FindoraTheme.colors.onBackground)
            }
        },
    )
}

/** The name field and hint of [SaveSearchDialog]; the keyboard's Done saves the trimmed name unless it is blank. */
@Composable
fun SaveSearchNameField(
    state: SearchNameState,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        FindoraTextField(
            value = state.name,
            onValueChange = state::onNameChange,
            label = stringResource(R.string.saved_search_name_label),
            imeAction = ImeAction.Done,
            onImeAction = { state.nameToSave?.let(onSave) },
            fieldTag = "saveSearchName",
        )
        Text(
            text = stringResource(R.string.saved_search_save_hint),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
    }
}

/** A default name from what the filter narrows: "Kakkanad · Apartment · 2+ bedrooms". */
@Composable
fun suggestedSearchName(filter: PropertyFilter): String {
    val type = filter.type?.let { stringResource(HomeCategory.of(it).labelRes()) }
    val bedrooms = filter.minBedrooms?.let { pluralStringResource(R.plurals.saved_search_bedrooms, it, it) }
    return listOfNotNull(filter.query.trim().takeIf { it.isNotEmpty() }, type, bedrooms)
        .joinToString(" · ")
        .ifEmpty { stringResource(R.string.saved_search_default_name) }
}

/** One saved search: tap to see its results; alert switch with its frequency; rename and delete. */
@Composable
fun SavedSearchRow(
    item: SavedSearchItem,
    onEvent: (SavedSearchesUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val search = item.search
    var renaming by rememberSaveable(search.id) { mutableStateOf(false) }
    val showResults = stringResource(R.string.saved_search_run)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(FindoraTheme.colors.surface, FindoraTheme.shapes.card)
                .padding(vertical = FindoraTheme.spacing.xs)
                .testTag("savedSearch-${search.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = showResults) { onEvent(SavedSearchesUiEvent.Run(search.id)) }
                    .padding(start = FindoraTheme.spacing.md, end = FindoraTheme.spacing.xs)
                    .testTag("savedSearchRun-${search.id}"),
        ) {
            Icon(FindoraIcons.Bookmark, contentDescription = null, modifier = Modifier.size(IconSize))
            Spacer(Modifier.width(FindoraTheme.spacing.sm))
            Column(Modifier.weight(1f).padding(vertical = FindoraTheme.spacing.xs)) {
                Text(
                    text = search.name,
                    style = FindoraTheme.typography.bodyMedium,
                    color = FindoraTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = listOf(search.query, item.regionName).filter { it.isNotBlank() }.joinToString(" · ")
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = FindoraTheme.typography.caption,
                        color = FindoraTheme.colors.onSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { renaming = true }, modifier = Modifier.testTag("savedSearchRename-${search.id}")) {
                Icon(FindoraIcons.Pencil, contentDescription = stringResource(R.string.saved_search_rename))
            }
            IconButton(
                onClick = { onEvent(SavedSearchesUiEvent.Delete(search.id)) },
                modifier = Modifier.testTag("savedSearchDelete-${search.id}"),
            ) {
                Icon(FindoraIcons.Trash, contentDescription = stringResource(R.string.saved_search_delete))
            }
        }
        SwitchRow(
            title = stringResource(R.string.saved_search_alerts),
            summary = stringResource(R.string.saved_search_alerts_summary),
            icon = FindoraIcons.Bell,
            checked = search.alertsEnabled,
            onCheckedChange = { onEvent(SavedSearchesUiEvent.SetAlerts(search.id, it)) },
            modifier = Modifier.testTag("savedSearchAlerts-${search.id}"),
        )
        if (search.alertsEnabled) {
            SegmentedControl(
                options =
                    listOf(
                        AlertFrequency.Daily to stringResource(R.string.saved_search_frequency_daily),
                        AlertFrequency.Instant to stringResource(R.string.saved_search_frequency_instant),
                    ),
                selected = search.frequency,
                onSelect = { onEvent(SavedSearchesUiEvent.SetFrequency(search.id, it)) },
                modifier =
                    Modifier
                        .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs)
                        .testTag("savedSearchFrequency-${search.id}"),
            )
        }
    }
    if (renaming) {
        SaveSearchDialog(
            initialName = search.name,
            title = stringResource(R.string.saved_search_rename),
            confirmLabel = stringResource(R.string.saved_search_rename_confirm),
            onConfirm = { name ->
                onEvent(SavedSearchesUiEvent.Rename(search.id, name))
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}
