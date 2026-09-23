package com.raupime.liquidpage

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidPagerStateTest {

    private fun state(current: Int = 0, count: Int = 5): LiquidPagerState =
        LiquidPagerState(current) { count }.apply {
            viewportSize = IntSize(1000, 2000)
            density = Density(1f)
        }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }


    @Test
    fun forwardDragMovesCurrentOutAndTargetIn() = runBlocking {
        val state = state(current = 1)
        state.dragStart()
        state.drag(forwardDeltaPx = 400f, pointerY = 500f)
        // 40% of the width * forwardDragFraction (0.45) = 18% progress.
        assertClose(0.18f, state.progress)
        assertEquals(2, state.targetPage)
        assertEquals(1, state.currentPage)
        assertClose(0.18f, state.currentPageOffsetFraction)
    }

    @Test
    fun backwardDragRevealsPreviousFromTheLeadingEdge() = runBlocking {
        val state = state(current = 2)
        state.dragStart()
        state.drag(forwardDeltaPx = -300f, pointerY = 500f)
        assertClose(0.3f, state.progress)
        assertEquals(1, state.targetPage)
        assertClose(-0.3f, state.currentPageOffsetFraction)
    }

    @Test
    fun reversingWithinAGestureFlipsTheTarget() = runBlocking {
        val state = state(current = 2)
        state.dragStart()
        state.drag(forwardDeltaPx = 200f, pointerY = 0f)
        assertEquals(3, state.targetPage)
        state.drag(forwardDeltaPx = -500f, pointerY = 0f)
        assertEquals(1, state.targetPage)
        assertClose(0.3f, state.progress)
    }

    @Test
    fun edgesClampTheDrag() = runBlocking {
        val state = state(current = 0)
        state.dragStart()
        state.drag(forwardDeltaPx = -400f, pointerY = 0f)
        assertEquals(0, state.targetPage)
        assertEquals(0f, state.progress)

        val last = state(current = 4)
        last.dragStart()
        last.drag(forwardDeltaPx = 400f, pointerY = 0f)
        assertEquals(4, last.targetPage)
        assertEquals(0f, last.progress)
    }


    @Test
    fun scrollToPageResetsEverything() = runBlocking {
        val state = state(current = 0)
        state.dragStart()
        state.drag(forwardDeltaPx = 400f, pointerY = 0f)
        state.scrollToPage(3)
        assertEquals(3, state.currentPage)
        assertEquals(3, state.targetPage)
        assertEquals(0f, state.progress)
    }
}
