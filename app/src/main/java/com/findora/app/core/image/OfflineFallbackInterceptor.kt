package com.findora.app.core.image

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult

/** Request extra naming a bundled asset (for example "offline/pexels_123.webp") to show if the remote load fails. */
val OfflineFallbackAsset = Extras.Key<String?>(default = null)

/** Attaches the offline fallback for this request. */
fun ImageRequest.Builder.offlineFallback(assetPath: String?): ImageRequest.Builder =
    apply { extras[OfflineFallbackAsset] = assetPath }

/** Coil URI of a file inside the APK's assets. */
fun assetUri(assetPath: String): String = "file:///android_asset/${assetPath.trimStart('/')}"

/**
 * When a request fails (offline, 404, timeout) and it carries an [OfflineFallbackAsset], retries it
 * once with the bundled WebP instead, so listing cards never render empty.
 */
class OfflineFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result !is ErrorResult) return result
        val fallback = chain.request.getExtra(OfflineFallbackAsset) ?: return result
        val fallbackUri = assetUri(fallback)
        if (chain.request.data == fallbackUri) return result
        val retry =
            chain.request
                .newBuilder()
                .data(fallbackUri)
                .offlineFallback(null)
                .build()
        return chain.withRequest(retry).proceed()
    }
}
