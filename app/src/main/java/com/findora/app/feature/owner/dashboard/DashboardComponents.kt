package com.findora.app.feature.owner.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.findora.app.R
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.owner.ListingStats
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerEnquiry

@Composable
internal fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun TotalsRow(
    totals: ListingStats,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        StatTile(
            pluralStringResource(R.plurals.owner_stat_views, totals.views, totals.views),
            Modifier.weight(1f).testTag("totalViews"),
        )
        StatTile(
            pluralStringResource(R.plurals.owner_stat_saves, totals.saves, totals.saves),
            Modifier.weight(1f).testTag("totalSaves"),
        )
        StatTile(
            pluralStringResource(R.plurals.owner_stat_enquiries, totals.enquiries, totals.enquiries),
            Modifier.weight(1f).testTag("totalEnquiries"),
        )
    }
}

@Composable
internal fun StatTile(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(FindoraTheme.shapes.card)
            .background(FindoraTheme.colors.surface)
            .padding(FindoraTheme.spacing.sm),
    ) {
        Text(text = text, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
    }
}

@Composable
internal fun RowAction(
    label: Int,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    TextButton(onClick = onClick, modifier = modifier.testTag(tag)) {
        Text(
            text = stringResource(label),
            color = if (destructive) FindoraTheme.colors.error else FindoraTheme.colors.accent,
            style = FindoraTheme.typography.label,
        )
    }
}

@Composable
internal fun EnquiryRow(
    enquiry: OwnerEnquiry,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(FindoraTheme.shapes.card)
            .background(FindoraTheme.colors.surface)
            .padding(FindoraTheme.spacing.sm)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(enquiry.listingTitle, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.onBackground)
        enquiry.lastMessage?.let {
            Text(
                it,
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The listing status as a word ("Published", "Paused", "Draft"). */
fun ListingStatus.labelRes(): Int =
    when (this) {
        ListingStatus.Draft -> R.string.owner_status_draft
        ListingStatus.Published -> R.string.owner_status_published
        ListingStatus.Paused -> R.string.owner_status_paused
    }
