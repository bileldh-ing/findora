package com.findora.app.core.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighbourhoodScoreTest {
    @Test
    fun sameLocalityAlwaysGivesTheSameScores() {
        val first = NeighbourhoodScore.sample("kochi", "Kakkanad")
        repeat(5) { assertEquals(first, NeighbourhoodScore.sample("kochi", "Kakkanad")) }
        // Case and surrounding spaces do not change the scorecard.
        assertEquals(first.scores, NeighbourhoodScore.sample(" KOCHI ", "kakkanad  ").scores)
    }

    @Test
    fun scoresArePinnedSoTheyNeverDriftBetweenReleases() {
        // Golden values: a change here means every user would suddenly see different sample scores.
        val score = NeighbourhoodScore.sample("kochi", "Kakkanad")
        assertEquals(GOLDEN, score.scores.values.toList())
    }

    @Test
    fun everyAxisIsScoredWithinTheSampleRange() {
        val names = listOf("Kakkanad", "Edappally", "La Marsa", "Les Berges du Lac", "Jumeirah", "Marais", "حي النصر")
        names.forEach { name ->
            val score = NeighbourhoodScore.sample("region", name)
            assertEquals(NeighbourhoodAxis.entries, score.scores.keys.toList())
            assertTrue(score.scores.values.all { it in NeighbourhoodScore.SAMPLE_MIN..NeighbourhoodScore.SAMPLE_MAX })
            assertTrue(score.isSampleData)
            assertEquals(name, score.locality)
        }
    }

    @Test
    fun differentLocalitiesDiffer() {
        assertNotEquals(
            NeighbourhoodScore.sample("kochi", "Kakkanad").scores,
            NeighbourhoodScore.sample("kochi", "Edappally").scores,
        )
        assertNotEquals(
            NeighbourhoodScore.sample("kochi", "Centre").scores,
            NeighbourhoodScore.sample("tunis", "Centre").scores,
        )
    }

    @Test
    fun overallIsTheRoundedMean() {
        val scores =
            mapOf(
                NeighbourhoodAxis.Walkability to 80,
                NeighbourhoodAxis.Transport to 70,
                NeighbourhoodAxis.Safety to 60,
                NeighbourhoodAxis.Noise to 50,
                NeighbourhoodAxis.Amenities to 43,
            )
        // (80 + 70 + 60 + 50 + 43) / 5 = 60.6 -> 61.
        assertEquals(61, NeighbourhoodScore("X", scores, isSampleData = false).overall)
    }

    @Test
    fun rejectsIncompleteOrOutOfRangeScores() {
        assertThrows(IllegalArgumentException::class.java) {
            NeighbourhoodScore("X", mapOf(NeighbourhoodAxis.Safety to 50), isSampleData = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NeighbourhoodScore("X", NeighbourhoodAxis.entries.associateWith { 101 }, isSampleData = false)
        }
    }

    private companion object {
        val GOLDEN: List<Int> = listOf(62, 44, 83, 58, 69)
    }
}
