package com.findora.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the device can show a biometric (or device credential) prompt. Abstracted for JVM tests. */
fun interface BiometricAvailability {
    fun canAuthenticate(): Boolean
}

class AndroidBiometricAvailability
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BiometricAvailability {
        override fun canAuthenticate(): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

        companion object {
            /** Biometrics with a PIN/pattern/password fallback; supported together on every API level. */
            const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
    }
