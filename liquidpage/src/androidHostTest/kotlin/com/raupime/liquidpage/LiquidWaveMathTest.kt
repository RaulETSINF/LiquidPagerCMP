package com.raupime.liquidpage

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidWaveMathTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun forwardRadiusStartsAtRestAndEndsAtZero() {
        assertEquals(48f, LiquidWaveMath.forwardHorizontalRadius(0f, initial = 48f, max = 800f))
        assertEquals(0f, LiquidWaveMath.forwardHorizontalRadius(1f, initial = 48f, max = 800f))
    }

    @Test
    fun forwardRadiusGrowsLinearlyUntilFortyPercent() {
        assertClose(424f, LiquidWaveMath.forwardHorizontalRadius(0.2f, initial = 48f, max = 800f))
        assertClose(800f, LiquidWaveMath.forwardHorizontalRadius(0.4f, initial = 48f, max = 800f))
    }

    @Test
    fun forwardRadiusOvershootsIntoNegativeValuesAndDiesOut() {
        // Damped oscillation: crosses zero around 60% and bulges back around 80%.
        val at60 = LiquidWaveMath.forwardHorizontalRadius(0.6f, initial = 0f, max = 800f)
        val at80 = LiquidWaveMath.forwardHorizontalRadius(0.8f, initial = 0f, max = 800f)
        val at99 = LiquidWaveMath.forwardHorizontalRadius(0.99f, initial = 0f, max = 800f)
        assertTrue(abs(at60) < 20f, "expected ~0 at 60% but was $at60")
        assertTrue(at80 < -150f && at80 > -220f, "expected ~-20% overshoot at 80% but was $at80")
        assertTrue(abs(at99) < 15f, "expected the wave to die out but was $at99")
    }

    @Test
    fun backwardRadiusMatchesCubertoCurve() {
        // hidden = 0 coincides with the resting bulge, so the revealed page can settle without a jump.
        assertEquals(48f, LiquidWaveMath.backwardHorizontalRadius(0f, rest = 48f, amplitude = 48f))
        assertClose(96f, LiquidWaveMath.backwardHorizontalRadius(0.4f, rest = 48f, amplitude = 48f))
        assertEquals(0f, LiquidWaveMath.backwardHorizontalRadius(1f, rest = 48f, amplitude = 48f))
        // Between 40% and 100% the same damped oscillation is used with twice the amplitude.
        val expected = LiquidWaveMath.forwardHorizontalRadius(0.7f, initial = 0f, max = 96f)
        assertClose(expected, LiquidWaveMath.backwardHorizontalRadius(0.7f, rest = 48f, amplitude = 48f))
    }

    @Test
    fun verticalRadiusSaturatesAtFortyPercent() {
        assertEquals(82f, LiquidWaveMath.verticalRadius(0f, initial = 82f, max = 900f))
        assertClose(491f, LiquidWaveMath.verticalRadius(0.2f, initial = 82f, max = 900f))
        assertEquals(900f, LiquidWaveMath.verticalRadius(0.4f, initial = 82f, max = 900f))
        assertEquals(900f, LiquidWaveMath.verticalRadius(0.9f, initial = 82f, max = 900f))
    }

    @Test
    fun sideWidthSweepsBetweenTwentyAndEightyPercent() {
        assertEquals(15f, LiquidWaveMath.sideWidth(0.1f, initial = 15f, width = 1000f))
        assertEquals(15f, LiquidWaveMath.sideWidth(0.2f, initial = 15f, width = 1000f))
        assertClose(507.5f, LiquidWaveMath.sideWidth(0.5f, initial = 15f, width = 1000f))
        assertEquals(1000f, LiquidWaveMath.sideWidth(0.8f, initial = 15f, width = 1000f))
        assertEquals(1000f, LiquidWaveMath.sideWidth(1f, initial = 15f, width = 1000f))
    }

    @Test
    fun buttonFadesBetweenTenAndThirtyPercent() {
        assertEquals(1f, LiquidWaveMath.buttonAlpha(0f))
        assertEquals(1f, LiquidWaveMath.buttonAlpha(0.1f))
        assertClose(0.5f, LiquidWaveMath.buttonAlpha(0.2f))
        assertEquals(0f, LiquidWaveMath.buttonAlpha(0.3f))
        assertEquals(0f, LiquidWaveMath.buttonAlpha(1f))
    }
}
