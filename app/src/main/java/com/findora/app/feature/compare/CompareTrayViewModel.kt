package com.findora.app.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.compare.CompareSelection
import com.findora.app.core.domain.compare.ObserveComparedProperties
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.repository.CompareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareTrayState(
    val items: List<Property> = emptyList(),
    val maxItems: Int = CompareSelection.MAX_ITEMS,
) {
    val isVisible: Boolean get() = items.isNotEmpty()
}

sealed interface CompareTrayEvent {
    data class Add(
        val propertyId: String,
    ) : CompareTrayEvent

    data class Remove(
        val propertyId: String,
    ) : CompareTrayEvent

    data object Clear : CompareTrayEvent
}

/** The outcome of an add, for the snackbar. */
data class CompareTrayEffect(
    val result: CompareAddResult,
)

/** The floating compare tray on Home and the "Add to compare" long-press on its cards. */
@HiltViewModel
class CompareTrayViewModel
    @Inject
    constructor(
        private val compared: ObserveComparedProperties,
        private val compare: CompareRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<CompareTrayEffect>(Channel.BUFFERED)
        val effects: Flow<CompareTrayEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<CompareTrayState> =
            compared()
                .map { CompareTrayState(items = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CompareTrayState())

        fun onEvent(event: CompareTrayEvent) {
            viewModelScope.launch {
                val user = compared.userId.first()
                when (event) {
                    is CompareTrayEvent.Add ->
                        effectChannel.send(
                            CompareTrayEffect(compare.add(user, event.propertyId)),
                        )
                    is CompareTrayEvent.Remove -> compare.remove(user, event.propertyId)
                    CompareTrayEvent.Clear -> compare.clear(user)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
