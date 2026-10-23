package com.findora.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.findora.app.R
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.theme.FindoraTheme

/** Shown on Home until the user has a preference profile: what the quiz gives them and the way in. */
@Composable
fun MatchQuizBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FindoraTheme.shapes.card)
                .background(FindoraTheme.colors.surface)
                .padding(FindoraTheme.spacing.md)
                .testTag("matchBanner"),
    ) {
        Text(
            text = stringResource(R.string.home_match_banner_title),
            style = FindoraTheme.typography.section,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_match_banner_body),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
        PrimaryPillButton(
            text = stringResource(R.string.home_match_banner_action),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag("matchBannerAction"),
        )
    }
}
