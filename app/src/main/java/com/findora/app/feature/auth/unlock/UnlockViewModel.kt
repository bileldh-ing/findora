package com.findora.app.feature.auth.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findora.app.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UnlockUiEffect {
    data object SignedOut : UnlockUiEffect
}

/** The lock screen only needs one repository action: giving up on biometrics and signing out. */
@HiltViewModel
class UnlockViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<UnlockUiEffect>(Channel.BUFFERED)
        val effects: Flow<UnlockUiEffect> = effectChannel.receiveAsFlow()

        fun signOut() {
            viewModelScope.launch {
                auth.logout()
                effectChannel.send(UnlockUiEffect.SignedOut)
            }
        }
    }
