package com.findora.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8 + kotlinx.serialization). */
sealed interface FindoraRoute {
    /** Phase 0 themed landing; replaced by Welcome in Phase 2. */
    @Serializable
    data object Bootstrap : FindoraRoute

    /** Debug-only catalogue of every design-system component. */
    @Serializable
    data object DesignSystem : FindoraRoute
}
