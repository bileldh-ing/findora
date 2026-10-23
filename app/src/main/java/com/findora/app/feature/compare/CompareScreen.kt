package com.findora.app.feature.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.TextLink
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.compare.CompareMetric
import com.findora.app.core.domain.compare.CompareRow
import com.findora.app.core.domain.compare.CompareTable
import com.findora.app.core.domain.format.AreaFormatter
import com.findora.app.core.domain.format.CurrencyConverter
import com.findora.app.core.domain.format.DisplayMoney
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.ui.ListingCoverPhoto
import com.findora.app.core.ui.LocalDisplayPreferences
import com.findora.app.core.ui.StateMessage
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.rentText
import com.findora.app.feature.home.labelRes
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val LabelColumn = 92.dp
private val CellMinHeight = 48.dp
private val SmallButton = 40.dp
private val SmallIcon = 18.dp
private const val BEST_ALPHA = 0.16f
private const val DRAG_ALPHA = 0.85f
private const val HALF_TURN = 180f

@Composable
fun CompareRoute(
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompareScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenProperty = onOpenProperty,
        modifier = modifier,
    )
}

/**
 * Up to three homes side by side: photo, price, area, price per m², bedrooms, bathrooms, amenity count,
 * rating and (with a profile) match score, the best value of each row highlighted, then an amenity checklist.
 * Columns are removed with their close button and reordered by long-press dragging a column header, by the
 * arrow buttons or with TalkBack actions.
 */
@Composable
fun CompareScreen(
    state: CompareUiState,
    onEvent: (CompareUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("compare"),
    ) {
        CompareTopBar(count = state.count, onBack = onBack, onClear = { onEvent(CompareUiEvent.Clear) })
        when {
            state.isLoading -> Unit
            state.isEmpty ->
                StateMessage(
                    icon = FindoraIcons.Compare,
                    title = stringResource(R.string.compare_empty_title),
                    body = stringResource(R.string.compare_empty_body),
                    modifier = Modifier.weight(1f).testTag("compareEmpty"),
                )
            else ->
                CompareTableView(
                    table = state.table,
                    maxItems = state.maxItems,
                    onEvent = onEvent,
                    onOpenProperty = onOpenProperty,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun CompareTopBar(
    count: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs),
    ) {
        SmallIconButton(FindoraIcons.ArrowBack, stringResource(R.string.back), onBack, "compareBack")
        Spacer(Modifier.width(FindoraTheme.spacing.sm))
        Text(
            text = stringResource(R.string.compare_title),
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (count > 0) {
            TextLink(
                text = stringResource(R.string.compare_clear),
                onClick = onClear,
                modifier = Modifier.testTag("compareClear"),
            )
        }
    }
}

@Composable
private fun CompareTableView(
    table: CompareTable,
    maxItems: Int,
    onEvent: (CompareUiEvent) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FindoraTheme.spacing.md)
                .testTag("compareTable"),
    ) {
        CompareHeaderRow(table.properties, onEvent, onOpenProperty)
        if (table.properties.size < 2) {
            Text(
                text = stringResource(R.string.compare_add_more, maxItems),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(vertical = FindoraTheme.spacing.sm).testTag("compareAddMore"),
            )
        } else {
            Text(
                text = stringResource(R.string.compare_reorder_hint),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(vertical = FindoraTheme.spacing.sm),
            )
        }
        table.rows.forEach { row ->
            HorizontalDivider(color = FindoraTheme.colors.outline)
            MetricRow(row = row, properties = table.properties)
        }
        if (table.amenities.isNotEmpty()) {
            HorizontalDivider(color = FindoraTheme.colors.outline)
            Text(
                text = stringResource(R.string.compare_amenities_checklist),
                style = FindoraTheme.typography.section,
                color = FindoraTheme.colors.onBackground,
                modifier = Modifier.padding(vertical = FindoraTheme.spacing.sm).semantics { heading() },
            )
            table.amenities.forEach { amenity ->
                val name = stringResource(amenity.labelRes())
                TableRow(label = name) {
                    table.properties.forEachIndexed { index, property ->
                        val has = table.hasAmenity(index, amenity)
                        val spoken =
                            stringResource(
                                if (has) R.string.compare_amenity_yes else R.string.compare_amenity_no,
                                name,
                                property.title,
                            )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = CellMinHeight)
                                    .testTag("amenity_${amenity.id}_$index")
                                    .clearAndSetSemantics { contentDescription = spoken },
                        ) {
                            if (has) {
                                Icon(
                                    FindoraIcons.Check,
                                    contentDescription = null,
                                    tint = FindoraTheme.colors.success,
                                    modifier = Modifier.size(SmallIcon),
                                )
                            } else {
                                Text("–", color = FindoraTheme.colors.onSurfaceMuted)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.xl))
    }
}

/** Photo, name and controls per column; long-press and drag a header sideways to move its column. */
@Composable
private fun CompareHeaderRow(
    properties: List<Property>,
    onEvent: (CompareUiEvent) -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var columnWidth by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentProperties by rememberUpdatedState(properties)
    TableRow(label = null, modifier = modifier) {
        properties.forEachIndexed { index, property ->
            val earlier = stringResource(R.string.compare_move_earlier)
            val later = stringResource(R.string.compare_move_later)
            val remove = stringResource(R.string.compare_remove)
            val position =
                stringResource(R.string.compare_column_description, property.title, index + 1, properties.size)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = FindoraTheme.spacing.xxs)
                        .onSizeChanged { columnWidth = it.width.coerceAtLeast(1) }
                        .graphicsLayer {
                            if (dragging == index) {
                                translationX = dragOffset
                                alpha = DRAG_ALPHA
                            }
                        }.pointerInput(property.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = currentProperties.indexOfFirst { it.id == property.id }
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    val steps = (dragOffset / columnWidth).roundToInt() * if (rtl) -1 else 1
                                    val from = dragging
                                    dragging = -1
                                    dragOffset = 0f
                                    if (from >= 0 && steps != 0) onEvent(CompareUiEvent.Move(from, from + steps))
                                },
                                onDragCancel = {
                                    dragging = -1
                                    dragOffset = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                            }
                        }.semantics(mergeDescendants = true) {
                            contentDescription = position
                            customActions =
                                buildList {
                                    if (index > 0) {
                                        add(
                                            CustomAccessibilityAction(earlier) {
                                                true.also {
                                                    onEvent(
                                                        CompareUiEvent.Move(
                                                            index,
                                                            index - 1,
                                                        ),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    if (index < properties.lastIndex) {
                                        add(
                                            CustomAccessibilityAction(later) {
                                                true.also {
                                                    onEvent(
                                                        CompareUiEvent.Move(
                                                            index,
                                                            index + 1,
                                                        ),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    add(
                                        CustomAccessibilityAction(
                                            remove,
                                        ) { true.also { onEvent(CompareUiEvent.Remove(property.id)) } },
                                    )
                                }
                        }.testTag("compareColumn_$index"),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(FindoraTheme.shapes.chip)
                        .clickable(
                            onClickLabel = stringResource(R.string.action_open_listing),
                        ) { onOpenProperty(property.id) },
                ) {
                    ListingCoverPhoto(property, Modifier.fillMaxSize())
                }
                Text(
                    text = property.title,
                    style = FindoraTheme.typography.label,
                    color = FindoraTheme.colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = FindoraTheme.spacing.xxs),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        SmallIconButton(FindoraIcons.ChevronEnd, earlier, {
                            onEvent(CompareUiEvent.Move(index, index - 1))
                        }, "compareEarlier_$index", flip = true)
                    }
                    SmallIconButton(
                        FindoraIcons.Close,
                        remove,
                        { onEvent(CompareUiEvent.Remove(property.id)) },
                        "compareRemove_$index",
                    )
                    if (index < properties.lastIndex) {
                        SmallIconButton(FindoraIcons.ChevronEnd, later, {
                            onEvent(CompareUiEvent.Move(index, index + 1))
                        }, "compareLater_$index")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    row: CompareRow,
    properties: List<Property>,
    modifier: Modifier = Modifier,
) {
    val label = row.metric.label()
    val bestLabel = stringResource(R.string.compare_best)
    TableRow(label = label, modifier = modifier.testTag("compareRow_${row.metric.name}")) {
        row.values.forEachIndexed { index, value ->
            val text = formatValue(row.metric, value, properties[index])
            val isBest = index in row.best
            val spoken =
                buildString {
                    append(label)
                        .append(", ")
                        .append(properties[index].title)
                        .append(": ")
                        .append(text)
                    if (isBest) append(", ").append(bestLabel)
                }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = CellMinHeight)
                        .padding(FindoraTheme.spacing.xxs)
                        .clip(FindoraTheme.shapes.chip)
                        .background(
                            if (isBest) {
                                FindoraTheme.colors.success.copy(
                                    alpha = BEST_ALPHA,
                                )
                            } else {
                                FindoraTheme.colors.background
                            },
                        ).testTag(if (isBest) "best_${row.metric.name}_$index" else "cell_${row.metric.name}_$index")
                        .clearAndSetSemantics { contentDescription = spoken },
            ) {
                Text(
                    text = text,
                    style = if (isBest) FindoraTheme.typography.bodyMedium else FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    label: String?,
    modifier: Modifier = Modifier,
    cells: @Composable RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(Modifier.width(LabelColumn).padding(end = FindoraTheme.spacing.xs)) {
            if (label != null) {
                Text(
                    text = label,
                    style = FindoraTheme.typography.caption,
                    color = FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
        cells()
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    flip: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(SmallButton)
                .clip(CircleShape)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FindoraTheme.colors.onBackground,
            modifier = Modifier.size(SmallIcon).graphicsLayer { rotationZ = if (flip) HALF_TURN else 0f },
        )
    }
}

@Composable
private fun formatValue(
    metric: CompareMetric,
    value: Double?,
    property: Property,
): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    if (value == null) return stringResource(R.string.compare_unknown)
    return when (metric) {
        CompareMetric.Price -> rentText(property.rent, PriceStyle.Localized, locale)
        CompareMetric.Area -> areaText(value.roundToInt(), locale = locale)
        CompareMetric.PricePerSqm -> pricePerAreaText(value, property, locale)
        CompareMetric.Rating -> String.format(locale, "%.1f", value)
        CompareMetric.Match -> stringResource(R.string.compare_match_value, value.roundToInt())
        CompareMetric.Bedrooms, CompareMetric.Bathrooms, CompareMetric.Amenities ->
            String.format(locale, "%d", value.roundToInt())
    }
}

/**
 * Price per area in the display unit and currency: the per-m² figure is converted with the same rates as the
 * rent (unrounded, so the "≈" figure matches the rent's conversion) and divided by about 10.76 for sq ft.
 */
@Composable
private fun pricePerAreaText(
    pricePerSqm: Double,
    property: Property,
    locale: Locale,
): String {
    val display = LocalDisplayPreferences.current
    val unit = display.areaUnit
    val perUnit = AreaFormatter.perArea(pricePerSqm, unit)
    val source = property.rent.currency
    val target = display.currency?.takeIf { display.converts && it != source }
    val converted =
        target?.let { currency ->
            display.rates
                ?.let(::CurrencyConverter)
                ?.convertExact(BigDecimal.valueOf(perUnit), source, currency)
                ?.let {
                    DisplayMoney(
                        Money(it.setScale(0, RoundingMode.HALF_UP).toLong(), currency),
                        approximate = true,
                    )
                }
        }
    val exact = converted ?: DisplayMoney(Money(perUnit.roundToLong().coerceAtLeast(0L), source), approximate = false)
    val format =
        when (unit) {
            AreaUnit.Metric -> R.string.compare_price_sqm_value
            AreaUnit.Imperial -> R.string.compare_price_sqft_value
        }
    return stringResource(format, PriceFormatter.formatDisplay(exact, locale, PriceStyle.Localized))
}

/** The row label; the price-per-area row follows the unit chosen in Settings. */
@Composable
fun CompareMetric.label(): String =
    when (this) {
        CompareMetric.PricePerSqm ->
            stringResource(
                when (LocalDisplayPreferences.current.areaUnit) {
                    AreaUnit.Metric -> R.string.compare_row_price_sqm
                    AreaUnit.Imperial -> R.string.compare_row_price_sqft
                },
            )
        else -> stringResource(labelRes())
    }

fun CompareMetric.labelRes(): Int =
    when (this) {
        CompareMetric.Price -> R.string.compare_row_price
        CompareMetric.Area -> R.string.compare_row_area
        CompareMetric.PricePerSqm -> R.string.compare_row_price_sqm
        CompareMetric.Bedrooms -> R.string.compare_row_beds
        CompareMetric.Bathrooms -> R.string.compare_row_baths
        CompareMetric.Amenities -> R.string.compare_row_amenities
        CompareMetric.Rating -> R.string.compare_row_rating
        CompareMetric.Match -> R.string.compare_row_match
    }
