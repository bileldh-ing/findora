package com.findora.app.navigation

/** Navigation out of the tabs, handled by the root graph. */
data class MainShellActions(
    val onOpenSearch: () -> Unit,
    val onOpenProperty: (String) -> Unit,
    val onSignedOut: () -> Unit,
    val onCreateAccount: () -> Unit,
    val onOpenViewings: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenMatchQuiz: () -> Unit,
    val onOpenDreamMatch: () -> Unit,
    val onOpenCompare: () -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenDesignSystem: (() -> Unit)?,
    val onScheduleViewing: (String) -> Unit,
    val onMessageAgent: (String) -> Unit,
    val onOpenOwnerDashboard: () -> Unit,
    val onEditListing: (String) -> Unit,
)
