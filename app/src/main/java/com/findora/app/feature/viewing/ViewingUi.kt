package com.findora.app.feature.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

private val BackButtonSize = 48.dp

/** Back button (48dp, mirrored in RTL) and a heading, under the status bar. */
@Composable
internal fun ViewingTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backLabel = stringResource(R.string.back)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FindoraTheme.spacing.xs, vertical = FindoraTheme.spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .testTag("viewingBack"),
        ) {
            Icon(FindoraIcons.ArrowBack, contentDescription = null, tint = FindoraTheme.colors.onBackground)
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(
            text = title,
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun ViewingSectionTitle(
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
