package com.findora.app.feature.savedsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.theme.FindoraTheme

/** Applies a linked saved search (a brief spinner while it loads), then continues to the app with Home showing it. */
@Composable
fun SavedSearchLinkRoute(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedSearchLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(state) {
        if (state is SavedSearchLinkState.Done) currentOnFinish()
    }
    val loading = stringResource(R.string.saved_search_opening)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .semantics { contentDescription = loading }
                .testTag("savedSearchLink"),
    ) {
        CircularProgressIndicator(color = FindoraTheme.colors.accent)
    }
}
