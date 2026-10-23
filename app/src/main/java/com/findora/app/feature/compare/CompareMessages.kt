package com.findora.app.feature.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.compare.CompareSelection

/** Snackbar texts for "Add to compare", resolved once per screen so effects can show them. */
@Immutable
data class CompareMessages(
    val added: String,
    val already: String,
    val full: String,
    val view: String,
) {
    fun text(result: CompareAddResult): String =
        when (result) {
            CompareAddResult.Added -> added
            CompareAddResult.AlreadyAdded -> already
            CompareAddResult.Full -> full
        }
}

@Composable
fun compareMessages(): CompareMessages =
    CompareMessages(
        added = stringResource(R.string.compare_added),
        already = stringResource(R.string.compare_already),
        full = pluralStringResource(R.plurals.compare_full, CompareSelection.MAX_ITEMS, CompareSelection.MAX_ITEMS),
        view = stringResource(R.string.compare_view),
    )
