package com.findora.app.navigation

/**
 * The link to a saved search: `findora://search/{searchId}`, used by new-match notifications. The graph registers
 * the same pattern and the manifest routes the host to MainActivity. Like the other links it is only honoured for
 * a session going straight to the app, and the search applies only for the user who saved it.
 */
object SearchDeepLink {
    const val HOST = "search"
    const val BASE_PATH = "${PropertyDeepLink.SCHEME}://$HOST"

    private val idPattern = Regex("[1-9][0-9]{0,17}")

    fun uriFor(searchId: Long): String = "$BASE_PATH/$searchId"

    /** The saved-search id in [uri], or null when it is not a well-formed search link. */
    fun parse(uri: String): Long? {
        val trimmed = uri.trim()
        val rest = trimmed.removePrefix("$BASE_PATH/").takeIf { it != trimmed } ?: return null
        val id = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return id.takeIf { idPattern.matches(it) }?.toLongOrNull()
    }
}
