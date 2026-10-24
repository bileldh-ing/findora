package com.findora.app.feature.match.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.ui.match.labelRes
import com.findora.app.core.ui.rentText
import java.util.Locale
import kotlin.math.roundToLong

private val BEDROOM_OPTIONS = listOf(1, 2, 3, 4)
private val BATHROOM_OPTIONS = listOf(1, 2, 3)

/** The question body of one quiz step. */
@Composable
internal fun QuizStepContent(
    step: QuizStep,
    state: MatchQuizUiState,
    onEvent: (MatchQuizUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (step) {
        QuizStep.Budget -> BudgetStep(state, onEvent, modifier)
        QuizStep.Rooms -> RoomsStep(state.draft, onEvent, modifier)
        QuizStep.Types ->
            ChoiceGroup(
                options = PropertyType.entries,
                selected = state.draft.types,
                label = { stringResource(it.labelRes()) },
                tag = { "type_${it.id}" },
                onToggle = { onEvent(MatchQuizUiEvent.ToggleType(it)) },
                modifier = modifier,
                hint = stringResource(R.string.quiz_pick_many),
            )
        QuizStep.Locations -> LocationsStep(state, onEvent, modifier)
        QuizStep.Lifestyle ->
            ChoiceGroup(
                options = LifestyleTag.entries,
                selected = state.draft.lifestyle,
                label = { stringResource(it.labelRes()) },
                tag = { "lifestyle_${it.id}" },
                onToggle = { onEvent(MatchQuizUiEvent.ToggleLifestyle(it)) },
                modifier = modifier,
                hint = stringResource(R.string.quiz_pick_many),
            )
        QuizStep.MustHaves ->
            ChoiceGroup(
                options = MustHave.entries,
                selected = state.draft.mustHaves,
                label = { stringResource(it.labelRes()) },
                tag = { "mustHave_${it.id}" },
                onToggle = { onEvent(MatchQuizUiEvent.ToggleMustHave(it)) },
                modifier = modifier,
                hint = stringResource(R.string.quiz_must_haves_hint),
            )
    }
}

@Composable
private fun BudgetStep(
    state: MatchQuizUiState,
    onEvent: (MatchQuizUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = state.bounds
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    QuizSection(modifier) {
        Hint(stringResource(R.string.quiz_budget_hint))
        if (bounds == null || bounds.maxRent <= bounds.minRent) {
            Hint(stringResource(R.string.quiz_budget_any))
            return@QuizSection
        }
        val draft = state.draft
        val low = (draft.budgetMin ?: bounds.minRent).coerceIn(bounds.minRent, bounds.maxRent)
        val high = (draft.budgetMax ?: bounds.maxRent).coerceIn(low, bounds.maxRent)
        val untouched = draft.budgetMin == null && draft.budgetMax == null
        Text(
            text =
                if (untouched) {
                    stringResource(R.string.quiz_budget_any)
                } else {
                    stringResource(
                        R.string.filter_range_value,
                        rentText(Money(low, state.currency), locale),
                        rentText(Money(high, state.currency), locale),
                    )
                },
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.testTag("quizBudgetValue"),
        )
        RangeSlider(
            value = low.toFloat()..high.toFloat(),
            onValueChange = { range ->
                val min = range.start.roundToLong()
                val max = range.endInclusive.roundToLong()
                onEvent(
                    MatchQuizUiEvent.SetBudget(
                        min = min.takeIf { it > bounds.minRent },
                        max = max.takeIf { it < bounds.maxRent },
                    ),
                )
            },
            valueRange = bounds.minRent.toFloat()..bounds.maxRent.toFloat(),
            colors =
                SliderDefaults.colors(
                    thumbColor = FindoraTheme.colors.primaryAction,
                    activeTrackColor = FindoraTheme.colors.primaryAction,
                    inactiveTrackColor = FindoraTheme.colors.outline,
                ),
            modifier = Modifier.testTag("quizBudgetSlider"),
        )
    }
}

@Composable
private fun RoomsStep(
    draft: QuizDraft,
    onEvent: (MatchQuizUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuizSection(modifier) {
        SubHeading(stringResource(R.string.filter_bedrooms))
        CountChoices(BEDROOM_OPTIONS, draft.bedrooms, "quizBeds") { onEvent(MatchQuizUiEvent.SetBedrooms(it)) }
        SubHeading(stringResource(R.string.filter_bathrooms))
        CountChoices(BATHROOM_OPTIONS, draft.bathrooms, "quizBaths") { onEvent(MatchQuizUiEvent.SetBathrooms(it)) }
    }
}

@Composable
private fun LocationsStep(
    state: MatchQuizUiState,
    onEvent: (MatchQuizUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = state.localities.map { it.name }
    QuizSection(modifier) {
        ChoiceGroup(
            options = names,
            selected = state.draft.areas,
            label = { it },
            tag = { "area_$it" },
            onToggle = { onEvent(MatchQuizUiEvent.ToggleArea(it)) },
            title = stringResource(R.string.quiz_locations_areas),
        )
        SubHeading(stringResource(R.string.quiz_locations_commute))
        ChipFlow {
            CategoryChip(
                text = stringResource(R.string.quiz_commute_none),
                selected = state.draft.commute == null,
                onClick = { onEvent(MatchQuizUiEvent.SetCommute(null)) },
            )
            names.forEach { name ->
                CategoryChip(
                    text = name,
                    selected = state.draft.commute == name,
                    onClick = { onEvent(MatchQuizUiEvent.SetCommute(name)) },
                    modifier = Modifier.testTag("commute_$name"),
                )
            }
        }
    }
}

/** A multi-select chip group (types, neighbourhoods, lifestyle, must-haves). */
@Composable
private fun <T> ChoiceGroup(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    hint: String? = null,
) {
    QuizSection(modifier) {
        title?.let { SubHeading(it) }
        hint?.let { Hint(it) }
        ChipFlow {
            options.forEach { option ->
                CategoryChip(
                    text = label(option),
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    modifier = Modifier.testTag(tag(option)),
                )
            }
        }
    }
}

@Composable
private fun CountChoices(
    options: List<Int>,
    selected: Int?,
    tagPrefix: String,
    onSelect: (Int?) -> Unit,
) {
    ChipFlow {
        CategoryChip(
            text = stringResource(R.string.filter_any),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        options.forEach { count ->
            CategoryChip(
                text = stringResource(R.string.filter_at_least, count),
                selected = selected == count,
                onClick = { onSelect(count) },
                modifier = Modifier.testTag("${tagPrefix}_$count"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) { content() }
}

@Composable
private fun QuizSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
        modifier = modifier,
    ) { content() }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = Modifier.padding(top = FindoraTheme.spacing.xs).semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted)
}
