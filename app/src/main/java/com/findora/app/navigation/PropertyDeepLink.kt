package com.findora.app.navigation

/**
 * The shareable link for a listing: `findora://property/{propertyId}`. The navigation graph registers the
 * same pattern (see [FindoraNavHost]) and the manifest routes the scheme to MainActivity, so a shared link
 * opens the listing's detail page.
 */
object PropertyDeepLink {
    const val SCHEME = "findora"
    const val HOST = "property"
    const val BASE_PATH = "$SCHEME://$HOST"

    private val idPattern = Regex("[A-Za-z0-9._-]+")

    fun uriFor(propertyId: String): String = "$BASE_PATH/$propertyId"

    /** The listing id in [uri], or null when it is not a well-formed property link. */
    fun parse(uri: String): String? {
        val rest = uri.trim().removePrefix("$BASE_PATH/").takeIf { it != uri.trim() } ?: return null
        val id = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return id.takeIf { idPattern.matches(it) }
    }
}
