package com.findora.app.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.compare.CompareMetric
import com.findora.app.core.domain.compare.CompareSelection
import com.findora.app.core.domain.compare.CompareTable
import com.findora.app.core.domain.compare.ObserveComparedProperties
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.repository.CompareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class CompareUiState(
    val isLoading: Boolean = true,
    val table: CompareTable = CompareTable.build(emptyList()),
    val maxItems: Int = CompareSelection.MAX_ITEMS,
) {
    val count: Int get() = table.properties.size
    val isEmpty: Boolean get() = !isLoading && count == 0
    val hasMatchRow: Boolean get() = table.row(CompareMetric.Match) != null
}

sealed interface CompareUiEvent {
    data class Remove(
        val propertyId: String,
    ) : CompareUiEvent

    /** A column moved from [fromIndex] to [toIndex] (drag, buttons or TalkBack actions). */
    data class Move(
        val fromIndex: Int,
        val toIndex: Int,
    ) : CompareUiEvent

    data object Clear : CompareUiEvent
}

/** The compare screen: the selected homes as a side-by-side table with the best value per row. */
@HiltViewModel
class CompareViewModel
    @Inject
    constructor(
        private val compared: ObserveComparedProperties,
        private val compare: CompareRepository,
        observeMatchContext: ObserveMatchContext,
    ) : ViewModel() {
        /** Without a usable profile the match row is simply left out. */
        private val matchContext: Flow<MatchContext?> =
            observeMatchContext().catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            }

        val state: StateFlow<CompareUiState> =
            combine(compared(), matchContext) { homes, context ->
                CompareUiState(
                    isLoading = false,
                    table =
                        CompareTable.build(
                            properties = homes,
                            matchScores = context?.let { ctx -> homes.associate { it.id to ctx.score(it).score } },
                        ),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CompareUiState())

        fun onEvent(event: CompareUiEvent) {
            viewModelScope.launch {
                val user = compared.userId.first()
                when (event) {
                    is CompareUiEvent.Remove -> compare.remove(user, event.propertyId)
                    is CompareUiEvent.Move -> compare.move(user, event.fromIndex, event.toIndex)
                    CompareUiEvent.Clear -> compare.clear(user)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
