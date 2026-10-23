package com.findora.app.core.domain.owner

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizingTest {
    @Test
    fun sampleSizeIsTheLargestPowerOfTwoKeepingTheLongEdgeAboveTheTarget() {
        assertEquals(1, PhotoSizing.sampleSize(1600, 1200))
        assertEquals(1, PhotoSizing.sampleSize(3199, 2000))
        assertEquals(2, PhotoSizing.sampleSize(3200, 2400))
        assertEquals(4, PhotoSizing.sampleSize(8000, 6000))
        assertEquals(4, PhotoSizing.sampleSize(6000, 8000))
    }

    @Test
    fun targetSizeFitsTheLongEdgeAndKeepsTheAspectRatioWithoutUpscaling() {
        assertEquals(1600 to 1200, PhotoSizing.targetSize(4000, 3000))
        assertEquals(900 to 1600, PhotoSizing.targetSize(2250, 4000))
        assertEquals(800 to 600, PhotoSizing.targetSize(800, 600))
        assertEquals(1600 to 1, PhotoSizing.targetSize(10_000, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyImagesAreRejected() {
        PhotoSizing.targetSize(0, 10)
    }
}
