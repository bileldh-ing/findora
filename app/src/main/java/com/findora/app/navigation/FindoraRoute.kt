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

    /** The signed-in (or guest) app: Home, Locations, Saved, Chat and Settings tabs under the floating nav pill. */
    @Serializable
    data object Main : FindoraRoute

    /** Full-screen search over the tabs (suggestions and recent searches); applies its text to Home. */
    @Serializable
    data object Search : FindoraRoute

    /** A listing's detail page, opened from a card or from a `findora://property/{propertyId}` link. */
    @Serializable
    data class PropertyDetail(
        val propertyId: String,
    ) : FindoraRoute

    /** Book a viewing of a listing (opened from its detail page). */
    @Serializable
    data class ScheduleViewing(
        val propertyId: String,
    ) : FindoraRoute

    /** The user's upcoming and past viewings (from Settings and from a confirmed booking). */
    @Serializable
    data object Viewings : FindoraRoute

    /** The six-step preference quiz behind the match score (from the Home banner and Settings). */
    @Serializable
    data object MatchQuiz : FindoraRoute

    /** Dream Match: swipe through the region's homes, best match first (from Home and Saved). */
    @Serializable
    data object DreamMatch : FindoraRoute

    /** The homes kept in Dream Match ("See my matches" at the end of the deck). */
    @Serializable
    data object Matches : FindoraRoute

    /** Up to three homes side by side (from the Home compare tray and the detail page). */
    @Serializable
    data object Compare : FindoraRoute

    /** A thread with a listing's demo agent, from the Chat tab, a notification or `findora://chat/{id}`. */
    @Serializable
    data class Chat(
        val conversationId: String,
    ) : FindoraRoute

    /** "Message agent" on a listing: opens the user's thread about it (created by the first message). */
    @Serializable
    data class ChatWithAgent(
        val propertyId: String,
    ) : FindoraRoute

    /**
     * A saved search's results, opened from a new-match notification or `findora://search/{id}`: applies the search
     * to Home (owner only) and continues to [Main].
     */
    @Serializable
    data class SavedSearchResults(
        val searchId: Long,
    ) : FindoraRoute

    /** The in-app notifications center (from the Home bell). */
    @Serializable
    data object Notifications : FindoraRoute

    /** Owner mode: the signed-in user's listings with their stats (from Settings once owner mode is on). */
    @Serializable
    data object OwnerDashboard : FindoraRoute

    /** The create / edit listing wizard; a null [listingId] starts a new draft. */
    @Serializable
    data class ListingEditor(
        val listingId: String? = null,
    ) : FindoraRoute

    /** Version, licences, photo credits and map attribution (from Settings). */
    @Serializable
    data object About : FindoraRoute

    /** Debug-only catalogue of every design-system component. */
    @Serializable
    data object DesignSystem : FindoraRoute
}
