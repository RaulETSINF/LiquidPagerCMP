package com.raupime.liquidpage

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidWaveGeometryTest {

    private val density = Density(2f)
    private val size = Size(1000f, 2000f)
    private val spec = LiquidWaveSpec.Default

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun restGeometryUsesDensityIndependentSizes() {
        val rest = LiquidWaveGeometry.rest(size, spec, density)
        assertEquals(96f, rest.horizontalRadius) // 48dp * 2
        assertEquals(30f, rest.sideWidth) // 15dp * 2
        assertEquals(164f, rest.verticalRadius) // 82dp * 2
        assertClose(spec.restWaveCenterFraction * 2000f, rest.centerY)
        assertEquals(1000f - 30f - 96f, rest.tipX(1000f))
    }

    @Test
    fun forwardStartsAtRestAndBackwardEndsAtRest() {
        val rest = LiquidWaveGeometry.rest(size, spec, density)
        val forwardStart = LiquidWaveGeometry.forward(0f, size, spec, density)
        val backwardEnd = LiquidWaveGeometry.backward(1f, size, spec, density)
        assertEquals(rest, forwardStart)
        assertEquals(rest, backwardEnd)
    }

    @Test
    fun partiallyGrownRestKeepsForwardContinuous() {
        val rest = LiquidWaveGeometry.rest(size, spec, density, restAmount = 0.5f)
        val forward = LiquidWaveGeometry.forward(0f, size, spec, density, restAmount = 0.5f)
        assertEquals(rest, forward)
        assertEquals(48f, rest.horizontalRadius)
        assertEquals(15f, rest.sideWidth)
    }

    @Test
    fun fullyRevealedGeometryIsDetected() {
        val plain = LiquidWaveGeometry.rest(size, LiquidWaveSpec.Plain, density)
        assertTrue(plain.isFullyRevealed)
        assertTrue(!LiquidWaveGeometry.rest(size, spec, density).isFullyRevealed)
    }

    @Test
    fun forwardEndHidesEverything() {
        val end = LiquidWaveGeometry.forward(1f, size, spec, density)
        assertEquals(size.width, end.sideWidth)
        assertEquals(0f, end.horizontalRadius)
        assertEquals(0f, end.tipX(size.width))
    }
}
