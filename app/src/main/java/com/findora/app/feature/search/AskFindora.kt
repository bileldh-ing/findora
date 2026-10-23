package com.findora.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.feature.home.labelRes
import com.findora.app.feature.savedsearch.SaveSearchDialog

private val ChipMinHeight = 40.dp
private val SmallIcon = 16.dp
private val RowMinHeight = 48.dp

/** The Ask Findora body under the search field: examples when empty, then understood chips, notes and Apply. */
internal fun LazyListScope.askItems(
    query: String,
    ask: AskUiState,
    onEvent: (SearchUiEvent) -> Unit,
) {
    item(key = "askOnDevice") { OnDeviceNote() }
    val intent = ask.intent
    when {
        intent == null -> examples(onEvent)
        intent.isEmpty -> item(key = "askNothing") { NothingUnderstood(query, intent.unrecognized, onEvent) }
        else -> {
            item(key = "askChips") { Understood(ask, onEvent) }
            item(key = "askApply") {
                PrimaryPillButton(
                    text = stringResource(R.string.ask_apply),
                    onClick = { onEvent(SearchUiEvent.ApplyAsk) },
                    modifier =
                        Modifier
                            .padding(
                                horizontal = FindoraTheme.spacing.screenHorizontal,
                                vertical = FindoraTheme.spacing.md,
                            ).testTag("askApply"),
                )
            }
            item(key = "askSave") { SaveAskButton(query, onEvent) }
        }
    }
}

/** "Save this search": names the understood search, saves it with alerts and applies it. */
@Composable
private fun SaveAskButton(
    query: String,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    SecondaryPillButton(
        text = stringResource(R.string.saved_search_save_this),
        onClick = { naming = true },
        modifier =
            modifier
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .padding(bottom = FindoraTheme.spacing.md)
                .testTag("askSave"),
    )
    if (naming) {
        SaveSearchDialog(
            initialName = query.trim(),
            onConfirm = { name ->
                naming = false
                onEvent(SearchUiEvent.SaveAsk(name))
            },
            onDismiss = { naming = false },
        )
    }
}

private fun LazyListScope.examples(onEvent: (SearchUiEvent) -> Unit) {
    item(key = "askTry") { AskLabel(stringResource(R.string.ask_try)) }
    val prompts = listOf(R.string.ask_example_1, R.string.ask_example_2, R.string.ask_example_3, R.string.ask_example_4)
    prompts.forEachIndexed { index, res ->
        item(key = "askExample_$index") {
            val text = stringResource(res)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                        .clip(FindoraTheme.shapes.chip)
                        .clickable(role = Role.Button) { onEvent(SearchUiEvent.QueryChanged(text)) }
                        .padding(horizontal = FindoraTheme.spacing.xs)
                        .testTag("askExample_$index"),
            ) {
                Icon(
                    FindoraIcons.Sparkles,
                    null,
                    tint = FindoraTheme.colors.accent,
                    modifier = Modifier.size(SmallIcon),
                )
                Spacer(Modifier.width(FindoraTheme.spacing.sm))
                Text(text, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onBackground)
            }
        }
    }
}

@Composable
private fun OnDeviceNote(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.xs)
                .testTag("askOnDevice"),
    ) {
        Icon(FindoraIcons.Lock, null, tint = FindoraTheme.colors.onSurfaceMuted, modifier = Modifier.size(SmallIcon))
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(
            text = stringResource(R.string.ask_on_device),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Understood(
    ask: AskUiState,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val intent = ask.intent ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier = modifier.fillMaxWidth().padding(horizontal = FindoraTheme.spacing.screenHorizontal),
    ) {
        AskLabel(stringResource(R.string.ask_understood), horizontalPadding = false)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.testTag("askChips"),
        ) {
            intent.meanings.forEach { meaning ->
                MeaningChip(
                    label = meaningLabel(meaning, intent.budget?.currency ?: ask.regionCurrency),
                    onRemove = { onEvent(SearchUiEvent.RemoveMeaning(meaning)) },
                )
            }
        }
        if (intent.unrecognized.isNotEmpty()) {
            Note(stringResource(R.string.ask_not_understood, intent.unrecognized.joinToString(", ")), "askUnrecognized")
        }
        if (ask.switchesRegion) Note(stringResource(R.string.ask_switches_region, ask.regionName), "askRegion")
        if (ask.currencyMismatch) {
            Note(stringResource(R.string.ask_currency_note, ask.regionName, ask.regionCurrency.code), "askCurrency")
        }
        Note(stringResource(R.string.ask_order, stringResource(ask.appliedSort.labelRes())), "askOrder")
        if (ask.lifestyleNeedsProfile) Note(stringResource(R.string.ask_lifestyle_quiz), "askQuiz")
    }
}

@Composable
private fun MeaningChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.ask_remove_chip, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .defaultMinSize(minHeight = ChipMinHeight)
                .clip(FindoraTheme.shapes.chip)
                .background(FindoraTheme.colors.primaryAction)
                .clickable(onClick = onRemove)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }.padding(start = FindoraTheme.spacing.md, end = FindoraTheme.spacing.sm)
                .testTag("askChip_$label"),
    ) {
        Text(label, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onPrimaryAction)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Icon(FindoraIcons.Close, null, tint = FindoraTheme.colors.onPrimaryAction, modifier = Modifier.size(SmallIcon))
    }
}

@Composable
private fun NothingUnderstood(
    query: String,
    unrecognized: List<String>,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.sm)
                .testTag("askNothing"),
    ) {
        Text(
            text = stringResource(R.string.ask_nothing, query.trim()),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onBackground,
        )
        if (unrecognized.isNotEmpty()) {
            Note(stringResource(R.string.ask_not_understood, unrecognized.joinToString(", ")), "askUnrecognized")
        }
        Note(stringResource(R.string.ask_example_1), "askNothingExample")
        TextLink(
            text = stringResource(R.string.ask_search_words),
            onClick = { onEvent(SearchUiEvent.Submit(query)) },
            color = FindoraTheme.colors.accent,
            modifier = Modifier.testTag("askSearchWords"),
        )
    }
}

@Composable
private fun Note(
    text: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.onSurfaceMuted,
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun AskLabel(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier =
            modifier
                .padding(horizontal = if (horizontalPadding) FindoraTheme.spacing.screenHorizontal else 0.dp)
                .padding(vertical = FindoraTheme.spacing.xs)
                .semantics { heading() },
    )
}
