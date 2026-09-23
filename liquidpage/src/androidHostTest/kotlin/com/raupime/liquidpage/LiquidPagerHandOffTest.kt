package com.raupime.liquidpage

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the transition animations with a frame clock that advances 16 ms per frame, so the
 * hand-off between a committing transition and a new gesture can be exercised without a device.
 */
class LiquidPagerHandOffTest {

    private class AutoFrameClock : MonotonicFrameClock {
        private var timeNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            timeNanos += 16_000_000L
            return onFrame(timeNanos)
        }
    }

    private val spec = tween<Float>(durationMillis = 800, easing = LinearEasing)

    private fun state(current: Int = 0, count: Int = 5): LiquidPagerState =
        LiquidPagerState(current) { count }.apply {
            viewportSize = IntSize(1000, 2000)
            density = Density(1f)
        }

    /** Flings forward from [state] and returns the job running the commit animation. */
    private suspend fun kotlinx.coroutines.CoroutineScope.flingForward(state: LiquidPagerState): Job {
        state.dragStart()
        state.drag(forwardDeltaPx = 300f, pointerY = 500f)
        val job = launch {
            state.dragEnd(
                forwardVelocityPxPerSec = 3000f,
                positionalThreshold = 1f / 3f,
                velocityThresholdPxPerSec = 400f,
                animationSpec = spec,
            )
        }
        // Let the animation start and advance a few frames.
        repeat(10) { yield() }
        assertTrue(state.isAnimationRunning, "the commit animation should be running")
        assertTrue(state.progress > 0.135f, "progress should have advanced past the drag")
        return job
    }

    @Test
    fun secondSwipeInTheSameDirectionHandsOffAndStartsTheNextTransition() = runBlocking(AutoFrameClock()) {
        val state = state(current = 0)
        val commitJob = flingForward(state)
        val progressAtHandOff = state.progress

        // A new swipe forward while the wave is still finishing.
        state.dragStart()
        state.drag(forwardDeltaPx = 200f, pointerY = 500f)

        assertEquals(1, state.currentPage, "the pager advances right away")
        assertEquals(2, state.targetPage, "the new gesture drives the next page")
        assertEquals(0.09f, state.progress, 0.001f)
        val finishing = assertNotNull(state.finishing)
        assertEquals(0, finishing.from)
        assertEquals(1, finishing.to)
        assertTrue(finishing.forward)
        assertTrue(finishing.progress.value >= progressAtHandOff, "the old wave keeps its progress")

        // The old wave completes on its own without touching the new transition.
        commitJob.join()
        assertNull(state.finishing)
        assertEquals(1, state.currentPage)
        assertEquals(2, state.targetPage)
        assertEquals(0.09f, state.progress, 0.001f)
        assertTrue(state.isDragging)
    }

    @Test
    fun swipingBackDuringACommitGrabsAndReversesTheWave() = runBlocking(AutoFrameClock()) {
        val state = state(current = 0)
        val commitJob = flingForward(state)
        val progressBefore = state.progress

        state.dragStart()
        state.drag(forwardDeltaPx = -100f, pointerY = 500f)

        assertNull(state.finishing, "opposite direction grabs instead of handing off")
        assertEquals(0, state.currentPage)
        assertEquals(1, state.targetPage)
        assertTrue(state.progress < progressBefore, "the wave retracts with the finger")
        assertTrue(!state.isAnimationRunning, "the commit animation was stopped")
        commitJob.join()
        assertEquals(0, state.currentPage)
    }

    @Test
    fun animateToPageTwiceInARowChainsTheTransitions() = runBlocking(AutoFrameClock()) {
        val state = state(current = 0)
        val first = launch { state.animateToPage(1, spec) }
        repeat(10) { yield() }
        assertTrue(state.isAnimationRunning)

        val second = launch { state.animateToPage(2, spec) }
        repeat(2) { yield() }
        assertEquals(1, state.currentPage)
        assertEquals(2, state.targetPage)
        assertNotNull(state.finishing)

        first.join()
        second.join()
        assertNull(state.finishing)
        assertEquals(2, state.currentPage)
        assertEquals(2, state.targetPage)
        assertEquals(0f, state.progress)
    }

    @Test
    fun completedFlingSettlesOnTheNextPage() = runBlocking(AutoFrameClock()) {
        val state = state(current = 0)
        val commitJob = flingForward(state)
        commitJob.join()
        assertEquals(1, state.currentPage)
        assertEquals(1, state.targetPage)
        assertEquals(0f, state.progress)
        assertEquals(1f, state.restAmount.value, "the resting bulge has grown back")
    }

    @Test
    fun handedOffPageCannotBeRevealedAgainUntilItFinishes() = runBlocking(AutoFrameClock()) {
        val state = state(current = 1)
        val commitJob = flingForward(state)
        state.dragStart()
        state.drag(forwardDeltaPx = 200f, pointerY = 500f)
        assertEquals(2, state.currentPage)
        // Reversing past the anchor would target page 1, which is still finishing: clamp to idle.
        state.drag(forwardDeltaPx = -600f, pointerY = 500f)
        assertEquals(2, state.targetPage)
        assertEquals(0f, state.progress)
        commitJob.join()
        assertNull(state.finishing)
    }
}
