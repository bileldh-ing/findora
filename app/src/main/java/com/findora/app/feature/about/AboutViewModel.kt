package com.findora.app.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.format.ExchangeRates
import com.findora.app.core.domain.model.AboutCredits
import com.findora.app.core.domain.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Named

sealed interface AboutUiState {
    data object Loading : AboutUiState

    data class Content(
        val versionName: String,
        val credits: AboutCredits,
        val rates: ExchangeRates?,
    ) : AboutUiState
}

/** Version, open-source licences, photo credits, map attribution and the exchange-rate source. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        referenceData: ReferenceDataRepository,
        @Named(VERSION_NAME) versionName: String,
    ) : ViewModel() {
        val state: StateFlow<AboutUiState> =
            flow<AboutUiState> {
                emit(AboutUiState.Content(versionName, referenceData.aboutCredits(), referenceData.exchangeRates()))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AboutUiState.Loading)

        companion object {
            const val VERSION_NAME = "versionName"
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
