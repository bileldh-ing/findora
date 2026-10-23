package com.findora.app.navigation

/**
 * The link to a conversation: `findora://chat/{conversationId}`, used by message notifications. The graph
 * registers the same pattern and the manifest routes the host to MainActivity. Like listing links, it is only
 * honoured for a session going straight to the app, and the thread opens only for the user who owns it.
 */
object ChatDeepLink {
    const val HOST = "chat"
    const val BASE_PATH = "${PropertyDeepLink.SCHEME}://$HOST"
    const val URI_PATTERN = "$BASE_PATH/{conversationId}"

    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    fun uriFor(conversationId: String): String = "$BASE_PATH/$conversationId"

    /** The conversation id in [uri], or null when it is not a well-formed chat link. */
    fun parse(uri: String): String? {
        val trimmed = uri.trim()
        val rest = trimmed.removePrefix("$BASE_PATH/").takeIf { it != trimmed } ?: return null
        val id = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return id.takeIf { idPattern.matches(it) }
    }
}
