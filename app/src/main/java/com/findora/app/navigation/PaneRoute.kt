package com.findora.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import kotlinx.serialization.Serializable

/** Destinations of a detail pane that only exist in two-pane layouts. */
sealed interface PaneRoute {
    /** Nothing selected yet. */
    @Serializable
    data object Empty : PaneRoute

    enum class Kind { Listing, Conversation }
}

private val EmptyIconSize = 48.dp

/** The detail pane before anything is chosen: says what will appear here. */
@Composable
fun EmptyPane(
    kind: PaneRoute.Kind,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .padding(contentPadding)
                .padding(FindoraTheme.spacing.lg)
                .testTag("emptyPane"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (kind == PaneRoute.Kind.Listing) FindoraIcons.Home else FindoraIcons.Chat,
            contentDescription = null,
            tint = FindoraTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(EmptyIconSize),
        )
        Spacer(Modifier.height(FindoraTheme.spacing.sm))
        Text(
            text =
                stringResource(
                    if (kind ==
                        PaneRoute.Kind.Listing
                    ) {
                        R.string.pane_empty_listing
                    } else {
                        R.string.pane_empty_conversation
                    },
                ),
            style = FindoraTheme.typography.body,
            color = FindoraTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
