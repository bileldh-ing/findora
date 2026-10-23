package com.findora.app.core.domain.insight

import androidx.compose.runtime.Immutable

/** The five radar axes. Every score is 0-100 and higher is better (for [Noise], higher means quieter). */
enum class NeighbourhoodAxis { Walkability, Transport, Safety, Noise, Amenities }

/**
 * A locality's scorecard. Findora has no neighbourhood data source yet, so [isSampleData] scorecards are
 * derived deterministically from the locality name and must be labelled "Sample data" wherever shown.
 */
@Immutable
data class NeighbourhoodScore(
    val locality: String,
    /** One entry per [NeighbourhoodAxis], in axis order. */
    val scores: Map<NeighbourhoodAxis, Int>,
    val isSampleData: Boolean,
) {
    init {
        require(scores.keys == NeighbourhoodAxis.entries.toSet()) { "Every axis needs a score" }
        require(scores.values.all { it in 0..MAX_SCORE }) { "Scores are 0-$MAX_SCORE" }
    }

    fun score(axis: NeighbourhoodAxis): Int = scores.getValue(axis)

    /** Rounded mean of the five axes. */
    val overall: Int get() = (scores.values.sum() + NeighbourhoodAxis.entries.size / 2) / NeighbourhoodAxis.entries.size

    companion object {
        const val MAX_SCORE = 100

        /** Sample scores never go below this, so no fictional locality is painted as unliveable. */
        const val SAMPLE_MIN = 35
        const val SAMPLE_MAX = 95

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
        private const val BITS_PER_AXIS = 8
        private const val BYTE_MASK = 0xFFL

        /**
         * The sample scorecard for [locality] in [regionId]: a 64-bit FNV-1a hash of the normalised names,
         * one byte per axis scaled into [SAMPLE_MIN]..[SAMPLE_MAX]. Same input, same scores, on every device
         * and every run (no Random, no String.hashCode reliance).
         */
        fun sample(
            regionId: String,
            locality: String,
        ): NeighbourhoodScore {
            val key = "${regionId.trim().lowercase()}|${locality.trim().lowercase()}"
            var hash = FNV_OFFSET
            key.encodeToByteArray().forEach { byte ->
                hash = hash xor (byte.toLong() and BYTE_MASK)
                hash *= FNV_PRIME
            }
            val span = SAMPLE_MAX - SAMPLE_MIN
            val scores =
                NeighbourhoodAxis.entries.associateWith { axis ->
                    val byte = (hash ushr (axis.ordinal * BITS_PER_AXIS)) and BYTE_MASK
                    SAMPLE_MIN + (byte * span / BYTE_MASK).toInt()
                }
            return NeighbourhoodScore(locality = locality.trim(), scores = scores, isSampleData = true)
        }
    }
}
