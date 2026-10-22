package com.findora.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8 + kotlinx.serialization). */
sealed interface FindoraRoute {
    @Serializable
    data object Welcome : FindoraRoute

    @Serializable
    data object Login : FindoraRoute

    @Serializable
    data object SignUp : FindoraRoute

    @Serializable
    data object ForgotPassword : FindoraRoute

    /** Biometric lock shown before the app when a remembered session is restored and the lock is on. */
    @Serializable
    data object Unlock : FindoraRoute

    /** The signed-in (or guest) app. */
    @Serializable
    data object Main : FindoraRoute

    /** Debug-only catalogue of every design-system component. */
    @Serializable
    data object DesignSystem : FindoraRoute
}
