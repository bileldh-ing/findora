package com.findora.app.core.designsystem

import com.findora.app.core.designsystem.component.resolveReducedMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {
    @Test
    fun systemFollowsTheAnimatorScale() {
        assertTrue(resolveReducedMotion(override = null, systemAnimatorScale = 0f))
        assertFalse(resolveReducedMotion(override = null, systemAnimatorScale = 1f))
    }

    @Test
    fun theInAppOverrideWinsBothWays() {
        assertTrue(resolveReducedMotion(override = true, systemAnimatorScale = 1f))
        assertFalse(resolveReducedMotion(override = false, systemAnimatorScale = 0f))
    }
}
