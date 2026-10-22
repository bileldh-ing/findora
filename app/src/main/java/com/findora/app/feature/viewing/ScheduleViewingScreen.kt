package com.findora.app.feature.viewing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.SuccessCheck
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.viewing.SlotStatus
import com.findora.app.core.domain.viewing.ViewingDay
import com.findora.app.core.domain.viewing.ViewingSlot
import com.findora.app.core.ui.InlineBanner
import com.findora.app.core.ui.StateMessage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val DayChipWidth = 64.dp
private val DayChipHeight = 80.dp
private val SlotHeight = 48.dp
private const val SLOT_COLUMNS = 3

@Composable
fun ScheduleViewingRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenViewings: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val noApp = stringResource(R.string.detail_no_app)
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScheduleUiEffect.AddToCalendar ->
                    if (!context.launchAddToCalendar(effect)) launch { snackbarHostState.showSnackbar(noApp) }
            }
        }
    }
    // Reminders need POST_NOTIFICATIONS on Android 13+: asked once at confirm time; denial still books.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onEvent(ScheduleUiEvent.Confirm)
        }
    val confirm: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(ScheduleUiEvent.Confirm)
        }
    }
    ScheduleViewingScreen(
        state = state,
        onEvent = { event -> if (event == ScheduleUiEvent.Confirm) confirm() else viewModel.onEvent(event) },
        onBack = onBack,
        onDone = onDone,
        onOpenViewings = onOpenViewings,
        onCreateAccount = onCreateAccount,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Pick a day from the two-week strip, then a free time; optionally leave a note for the agent and confirm.
 * Booked shows an animated check with "Add to calendar", "See my viewings" and "Done".
 */
@Composable
fun ScheduleViewingScreen(
    state: ScheduleUiState,
    onEvent: (ScheduleUiEvent) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenViewings: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(FindoraTheme.colors.background).testTag("scheduleViewing")) {
        when (state) {
            ScheduleUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                }
            ScheduleUiState.NotFound ->
                Column(Modifier.fillMaxSize()) {
                    ViewingTopBar(stringResource(R.string.schedule_title), onBack)
                    StateMessage(
                        icon = FindoraIcons.Search,
                        title = stringResource(R.string.detail_not_found_title),
                        body = stringResource(R.string.detail_not_found_body),
                        actionLabel = stringResource(R.string.back),
                        onAction = onBack,
                    )
                }
            ScheduleUiState.Unavailable ->
                Column(Modifier.fillMaxSize()) {
                    ViewingTopBar(stringResource(R.string.schedule_title), onBack)
                    StateMessage(
                        icon = FindoraIcons.AlertCircle,
                        title = stringResource(R.string.schedule_error_failed),
                        body = stringResource(R.string.error_body),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { onEvent(ScheduleUiEvent.Retry) },
                    )
                }
            is ScheduleUiState.Picking -> PickingContent(state, onEvent, onBack, onCreateAccount)
            is ScheduleUiState.Booked ->
                BookedContent(
                    state = state,
                    onAddToCalendar = { onEvent(ScheduleUiEvent.AddToCalendar) },
                    onOpenViewings = onOpenViewings,
                    onDone = onDone,
                )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun PickingContent(
    state: ScheduleUiState.Picking,
    onEvent: (ScheduleUiEvent) -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val horizontal = FindoraTheme.spacing.screenHorizontal
    Column(modifier.fillMaxSize().imePadding()) {
        ViewingTopBar(stringResource(R.string.schedule_title), onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md),
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).verticalScroll(rememberScrollState())
                    .padding(bottom = FindoraTheme.spacing.md),
        ) {
            Column(Modifier.padding(horizontal = horizontal)) {
                Text(
                    text = state.property.title,
                    style = FindoraTheme.typography.section,
                    color = FindoraTheme.colors.onBackground,
                )
                Text(
                    text = state.property.locality,
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
            if (state.isGuest) {
                InlineBanner(
                    text = stringResource(R.string.schedule_guest_note),
                    actionLabel = stringResource(R.string.action_sign_up),
                    onAction = onCreateAccount,
                    modifier = Modifier.padding(horizontal = horizontal).testTag("scheduleGuestNote"),
                )
            }
            ViewingSectionTitle(stringResource(R.string.schedule_pick_day), Modifier.padding(horizontal = horizontal))
            DayStrip(
                days = state.days,
                selected = state.selectedDate,
                onSelect = { onEvent(ScheduleUiEvent.SelectDate(it)) },
            )
            ViewingSectionTitle(stringResource(R.string.schedule_pick_time), Modifier.padding(horizontal = horizontal))
            SlotGrid(
                slots = state.slots,
                selected = state.selectedSlot,
                zone = state.zone,
                onSelect = { onEvent(ScheduleUiEvent.SelectSlot(it)) },
                modifier = Modifier.padding(horizontal = horizontal),
            )
            FindoraTextField(
                value = state.note,
                onValueChange = { onEvent(ScheduleUiEvent.NoteChanged(it)) },
                label = stringResource(R.string.schedule_note_label),
                imeAction = ImeAction.Done,
                fieldTag = "scheduleNote",
                modifier = Modifier.padding(horizontal = horizontal),
            )
            Text(
                text =
                    stringResource(
                        R.string.schedule_note_counter,
                        state.note.length,
                        ScheduleViewingViewModel.MAX_NOTE_LENGTH,
                    ),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal),
            )
            state.error?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.error,
                    modifier =
                        Modifier
                            .padding(horizontal = horizontal)
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("scheduleError"),
                )
            }
        }
        val selected = state.selectedSlot
        val label =
            when {
                state.submitting -> stringResource(R.string.schedule_confirming)
                selected == null -> stringResource(R.string.schedule_select_time)
                else -> stringResource(R.string.schedule_confirm_at, ViewingFormat.time(selected, state.zone, locale))
            }
        PrimaryPillButton(
            text = label,
            onClick = { onEvent(ScheduleUiEvent.Confirm) },
            enabled = state.canConfirm,
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = horizontal, vertical = FindoraTheme.spacing.sm)
                    .testTag("scheduleConfirm"),
        )
    }
}

@Composable
private fun DayStrip(
    days: List<ViewingDay>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    LazyRow(
        contentPadding = PaddingValues(horizontal = FindoraTheme.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.fillMaxWidth().testTag("dayStrip"),
    ) {
        itemsIndexed(days, key = { _, day -> day.date.toEpochDay() }) { index, day ->
            val isSelected = day.date == selected
            val longDate = ViewingFormat.longDate(day.date, locale)
            val description =
                if (day.availableCount == 0) {
                    stringResource(R.string.schedule_day_full_cd, longDate)
                } else {
                    pluralStringResource(R.plurals.schedule_day_cd, day.availableCount, longDate, day.availableCount)
                }
            val colors = FindoraTheme.colors
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .width(DayChipWidth)
                        .height(DayChipHeight)
                        .clip(FindoraTheme.shapes.chip)
                        .background(if (isSelected) colors.primaryAction else colors.surface)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(day.date) })
                        .clearAndSetSemantics {
                            contentDescription = description
                        }.testTag("day_$index"),
            ) {
                val content = if (isSelected) colors.onPrimaryAction else colors.onBackground
                Text(
                    text =
                        if (day.isToday) {
                            stringResource(R.string.schedule_today)
                        } else {
                            ViewingFormat.weekday(day.date, locale)
                        },
                    style = FindoraTheme.typography.label,
                    color = content,
                    maxLines = 1,
                )
                Text(
                    text = ViewingFormat.dayOfMonth(day.date, locale),
                    style = FindoraTheme.typography.section,
                    color = if (day.availableCount == 0 && !isSelected) colors.onSurfaceMuted else content,
                )
                Text(
                    text = ViewingFormat.month(day.date, locale),
                    style = FindoraTheme.typography.caption,
                    color = if (isSelected) colors.onPrimaryAction else colors.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SlotGrid(
    slots: List<ViewingSlot>,
    selected: Instant?,
    zone: ZoneId,
    onSelect: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slots.none { it.isAvailable }) {
        Text(
            text = stringResource(R.string.schedule_no_slots),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            modifier = modifier.testTag("scheduleNoSlots"),
        )
        return
    }
    val locale = currentLocale()
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier = modifier.testTag("slotGrid"),
    ) {
        slots.chunked(SLOT_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                row.forEachIndexed { columnIndex, slot ->
                    SlotChip(
                        slot = slot,
                        label = ViewingFormat.time(slot.start, zone, locale),
                        isSelected = slot.start == selected,
                        onSelect = { onSelect(slot.start) },
                        tag = "slot_${rowIndex * SLOT_COLUMNS + columnIndex}",
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(SLOT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SlotChip(
    slot: ViewingSlot,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = FindoraTheme.colors
    val description =
        when (slot.status) {
            SlotStatus.Available -> label
            SlotStatus.Taken -> stringResource(R.string.schedule_slot_taken_cd, label)
            SlotStatus.Busy -> stringResource(R.string.schedule_slot_busy_cd, label)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = SlotHeight)
                .clip(FindoraTheme.shapes.chip)
                .background(if (isSelected) colors.primaryAction else colors.background)
                .border(
                    BorderStroke(1.dp, if (isSelected) colors.primaryAction else colors.outline),
                    FindoraTheme.shapes.chip,
                ).selectable(
                    selected = isSelected,
                    enabled = slot.isAvailable,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ).semantics { contentDescription = description }
                .testTag(tag),
    ) {
        Text(
            text = label,
            style = FindoraTheme.typography.label,
            color =
                when {
                    isSelected -> colors.onPrimaryAction
                    slot.isAvailable -> colors.onBackground
                    else -> colors.onSurfaceMuted
                },
            textDecoration = if (slot.isAvailable) null else TextDecoration.LineThrough,
            maxLines = 1,
        )
    }
}

@Composable
private fun BookedContent(
    state: ScheduleUiState.Booked,
    onAddToCalendar: () -> Unit,
    onOpenViewings: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val start = state.viewing.startAt
    val date = ViewingFormat.longDate(start.atZone(state.zone).toLocalDate(), locale)
    val time = ViewingFormat.time(start, state.zone, locale)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md, Alignment.CenterVertically),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(FindoraTheme.spacing.screenHorizontal)
                .testTag("scheduleBooked"),
    ) {
        SuccessCheck(color = FindoraTheme.colors.success)
        Text(
            text = stringResource(R.string.booked_title),
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = state.property.title,
            style = FindoraTheme.typography.section,
            color = FindoraTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.booked_when, date, time),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        if (state.reminderScheduled) {
            Text(
                text = stringResource(R.string.booked_reminder),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        SecondaryPillButton(
            text = stringResource(R.string.booked_add_calendar),
            onClick = onAddToCalendar,
            modifier = Modifier.testTag("bookedAddToCalendar"),
        )
        PrimaryPillButton(
            text = stringResource(R.string.action_done),
            onClick = onDone,
            modifier = Modifier.testTag("bookedDone"),
        )
        TextLink(
            text = stringResource(R.string.booked_my_viewings),
            onClick = onOpenViewings,
            color = FindoraTheme.colors.accent,
            modifier = Modifier.testTag("bookedMyViewings"),
        )
    }
}

private fun ScheduleError.messageRes(): Int =
    when (this) {
        ScheduleError.SlotTaken -> R.string.schedule_error_taken
        ScheduleError.Overlaps -> R.string.schedule_error_overlap
        ScheduleError.InThePast -> R.string.schedule_error_past
        ScheduleError.Failed -> R.string.schedule_error_failed
    }
