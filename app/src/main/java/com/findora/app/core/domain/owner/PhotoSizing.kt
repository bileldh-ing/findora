package com.findora.app.core.domain.owner

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure maths of the photo import pipeline: how much to subsample while decoding and the final size. */
object PhotoSizing {
    /** Long edge of a stored listing photo, in pixels. */
    const val MAX_EDGE = 1600

    /** JPEG quality of stored photos. */
    const val JPEG_QUALITY = 82

    /** Largest power of two that keeps both edges at or above [maxEdge] after subsampling (BitmapFactory inSampleSize). */
    fun sampleSize(
        width: Int,
        height: Int,
        maxEdge: Int = MAX_EDGE,
    ): Int {
        require(width > 0 && height > 0) { "Image size must be positive" }
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** The size that fits [width] x [height] inside a [maxEdge] square, keeping the aspect ratio; never upscales. */
    fun targetSize(
        width: Int,
        height: Int,
        maxEdge: Int = MAX_EDGE,
    ): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Image size must be positive" }
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
