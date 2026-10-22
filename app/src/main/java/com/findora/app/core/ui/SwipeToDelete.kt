package com.findora.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

/**
 * A row that is removed by swiping it in either direction (red trash background). Swiping is not discoverable
 * with TalkBack, so the same removal is offered as a custom accessibility action labelled [deleteLabel].
 */
@Composable
fun SwipeToDeleteRow(
    deleteLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) currentOnDelete()
                value != SwipeToDismissBoxValue.Settled
            },
        )
    SwipeToDismissBox(
        state = state,
        modifier =
            modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            currentOnDelete()
                            true
                        },
                    )
            },
        backgroundContent = {
            val alignment =
                if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(FindoraTheme.shapes.chip)
                        .background(FindoraTheme.colors.error)
                        .padding(horizontal = FindoraTheme.spacing.md),
                horizontalArrangement = alignment,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FindoraIcons.Trash, contentDescription = null, tint = FindoraTheme.colors.onPhoto)
            }
        },
    ) {
        content()
    }
}
