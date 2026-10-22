package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

/**
 * A listing photo with its credit. [dominantColorArgb] is computed at build time and painted as an
 * instant placeholder. [fallbackAsset] is a bundled offline WebP (path inside assets) shown when the
 * remote image cannot be loaded, so cards never render empty.
 */
@Immutable
data class PropertyImage(
    val url: String,
    val width: Int,
    val height: Int,
    val dominantColorArgb: Long,
    val altText: String,
    val credit: PhotoCredit,
    val offlineAsset: String?,
    val fallbackAsset: String,
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

@Immutable
data class PhotoCredit(
    val photographer: String,
    val photographerUrl: String,
    val sourceUrl: String,
    val licence: String,
    val licenceUrl: String,
)
