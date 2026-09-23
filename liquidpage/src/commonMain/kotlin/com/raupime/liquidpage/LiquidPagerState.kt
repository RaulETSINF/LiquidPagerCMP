package com.raupime.liquidpage

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Creates and remembers a [LiquidPagerState].
 *
 * @param initialPage Page shown initially.
 * @param pageCount Provides the number of pages. It is a lambda so the count can change without
 * recreating the state.
 */
@Composable
fun rememberLiquidPagerState(
    initialPage: Int = 0,
    pageCount: () -> Int,
): LiquidPagerState {
    return rememberSaveable(saver = LiquidPagerState.Saver(pageCount)) {
        LiquidPagerState(initialPage, pageCount)
    }.apply {
        pageCountState.value = pageCount
    }
}

/**
 * State of a [LiquidPager].
 *
 * A transition always happens between [currentPage] (the settled page) and [targetPage], with
 * [progress] going from `0` (fully on the current page) to `1` (fully on the target page). While
 * idle, [targetPage] equals [currentPage] and [progress] is `0`.
 *
 * When the user swipes again while a transition is still animating towards its target, the pager
 * does not restart that animation: the transition is handed off to a finishing layer that keeps
 * playing on its own, [currentPage] advances right away and the new gesture drives a fresh
 * transition. Swiping in the opposite direction grabs the running wave instead and reverses it.
 *
 * @param currentPage Page shown initially.
 * @param pageCount Provides the number of pages.
 */
@Stable
class LiquidPagerState(
    currentPage: Int = 0,
    pageCount: () -> Int,
) {
    internal var pageCountState = mutableStateOf(pageCount)

    /** Number of pages. */
    val pageCount: Int get() = pageCountState.value().coerceAtLeast(0)

    /** The settled page. It only changes once a transition completes or is handed off. */
    var currentPage: Int by mutableIntStateOf(currentPage.coerceAtLeast(0))
        private set

    /** The page being revealed, or [currentPage] when idle. */
    var targetPage: Int by mutableIntStateOf(currentPage.coerceAtLeast(0))
        private set

    /** Progress of the active transition. Replaced when a transition is handed off or detached. */
    private var activeProgress: AnimatedFloat by mutableStateOf(AnimatedFloat(0f))

    /**
     * Progress of the transition from [currentPage] to [targetPage], in `0..1`. It changes every
     * frame while swiping: read it inside a `graphicsLayer` or draw block rather than in
     * composition to avoid recomposing on each frame.
     */
    val progress: Float get() = activeProgress.value

    /** `true` while a page is being revealed, either by the finger or by an animation. */
    val isTransitioning: Boolean get() = targetPage != currentPage

    /**
     * Signed offset from [currentPage] in pages: positive while moving forward, negative while
     * moving backward. For transitions between adjacent pages it is in `-1..1`, so
     * `currentPage + currentPageOffsetFraction` is the visual position, handy for indicators.
     */
    val currentPageOffsetFraction: Float get() = (targetPage - currentPage) * progress

    /** `true` while the user is dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /** `true` while a transition animation is running. */
    val isAnimationRunning: Boolean get() = activeProgress.isAnimating

    /** `true` when there is a page after [currentPage]. */
    val canScrollForward: Boolean get() = currentPage < pageCount - 1

    /** `true` when there is a page before [currentPage]. */
    val canScrollBackward: Boolean get() = currentPage > 0

    // region Internals used by LiquidPager

    /** Wave spec in use, updated by [LiquidPager] on every composition. */
    internal var spec: LiquidWaveSpec by mutableStateOf(LiquidWaveSpec.Default)

    /** Density in use, updated by [LiquidPager] on every composition. */
    internal var density: Density = Density(1f)

    /** Size of the pager, updated on every measure pass. */
    internal var viewportSize: IntSize = IntSize.Zero

    /** How much of the resting bulge of the current page is grown, `0..1`. */
    internal val restAmount = AnimatedFloat(1f)

    /** Presence (alpha and scale) of the "next" button, `0..1`. */
    internal val buttonPresence = AnimatedFloat(1f)

    /** Vertical wave center while it follows the finger; only meaningful when [waveCenterActive]. */
    internal val waveCenter = AnimatedFloat(0f)
    internal var waveCenterActive: Boolean by mutableStateOf(false)

    /** Where the wave center is heading while dragging (the finger's y), chased frame by frame. */
    private var waveCenterTarget = 0f

    /**
     * A transition handed off by a new gesture. It keeps animating to completion on its own while
     * the active transition is already driven by the finger. At most one exists at a time.
     */
    internal var finishing: FinishingTransition? by mutableStateOf(null)
        private set

    /** Accumulated drag in viewport widths, positive when moving forward. */
    private var dragFraction = 0f

    /**
     * Set when a drag starts over a committing animation: the first horizontal movement decides
     * whether the running transition is handed off (same direction) or grabbed (opposite).
     */
    private var pendingHandOffDecision = false

    internal val isBackwardTransition: Boolean get() = targetPage < currentPage

    private val restCenterY: Float get() = spec.restWaveCenterFraction * viewportSize.height

    /**
     * Fraction of the resting bulge to show. While swiping back, the bulge of the current page
     * collapses during the first [BACKWARD_COLLAPSE_END] of the reveal, tied to the finger so the
     * gesture stays fully reversible.
     */
    internal val effectiveRestAmount: Float
        get() {
            val base = restAmount.value
            if (!isBackwardTransition) return base
            return base * (1f - (progress / BACKWARD_COLLAPSE_END).coerceIn(0f, 1f))
        }

    internal val effectiveButtonPresence: Float
        get() {
            val base = buttonPresence.value
            if (!isBackwardTransition) return base
            return base * (1f - (progress / BACKWARD_COLLAPSE_END).coerceIn(0f, 1f))
        }

    private fun waveCenterY(restCenter: Float): Float =
        if (waveCenterActive) waveCenter.value else restCenter

    /**
     * Geometry of the mask of the current page (resting bulge, forward wave or an arriving wave
     * still finishing), also used to place the "next" button.
     */
    internal fun currentPageGeometry(size: Size, density: Density): LiquidWaveGeometry {
        val restCenter = spec.restWaveCenterFraction * size.height
        val finishing = finishing
        if (finishing != null && !finishing.forward && finishing.to == currentPage) {
            return LiquidWaveGeometry.backward(
                progress = finishing.progress.value,
                size = size,
                spec = spec,
                density = density,
                centerY = finishing.centerY,
                restAmount = effectiveRestAmount,
            )
        }
        return when {
            targetPage > currentPage -> LiquidWaveGeometry.forward(
                progress = progress,
                size = size,
                spec = spec,
                density = density,
                centerY = waveCenterY(restCenter),
                restAmount = restAmount.value,
            )

            targetPage < currentPage -> LiquidWaveGeometry.rest(
                size = size,
                spec = spec,
                density = density,
                centerY = restCenter,
                restAmount = effectiveRestAmount,
            )

            else -> LiquidWaveGeometry.rest(
                size = size,
                spec = spec,
                density = density,
                centerY = waveCenterY(restCenter),
                restAmount = restAmount.value,
            )
        }
    }

    /** Geometry used to clip [page], or `null` when the page must not be clipped. */
    internal fun maskGeometry(page: Int, size: Size, density: Density): LiquidWaveGeometry? {
        val current = currentPage
        val target = targetPage
        val finishing = finishing
        if (finishing != null && finishing.forward && page == finishing.from) {
            return LiquidWaveGeometry.forward(
                progress = finishing.progress.value,
                size = size,
                spec = spec,
                density = density,
                centerY = finishing.centerY,
                restAmount = finishing.restAmount,
            )
        }
        return when {
            page == current -> if (canScrollForward) currentPageGeometry(size, density) else null

            page == target && target < current -> LiquidWaveGeometry.backward(
                progress = progress,
                size = size,
                spec = spec,
                density = density,
                centerY = waveCenterY(spec.restWaveCenterFraction * size.height),
            )

            else -> null
        }
    }

    /**
     * Width from the leading edge that is fully hidden behind the page drawn on top of [page]
     * (assuming opaque pages), or `0` when nothing covers it. Used to skip drawing hidden content.
     */
    internal fun coveredWidth(page: Int, size: Size, density: Density): Float {
        val current = currentPage
        val finishing = finishing
        val pageAbove = when {
            // The next page sits under the current page (its resting bulge or forward wave).
            page == current + 1 -> current
            // The current page is covered by an old page still finishing its wave...
            page == current && finishing != null && finishing.forward -> finishing.from
            // ...or by the previous page pouring in from the leading edge.
            page == current && targetPage < current -> targetPage
            else -> return 0f
        }
        val above = maskGeometry(pageAbove, size, density) ?: return 0f
        val maskWidth = size.width - above.sideWidth
        return floor(min(maskWidth - above.horizontalRadius, maskWidth)).coerceIn(0f, size.width)
    }

    /**
     * The drag phase is synchronous on purpose: it is applied right when the pointer event is
     * dispatched, so every frame renders the latest finger position on every platform.
     */
    internal fun dragStart() {
        if (isTransitioning && abs(targetPage - currentPage) > 1) {
            // A jump between distant pages was interrupted: settle it, the finger can't drive it.
            if (progress >= 0.5f) currentPage = targetPage else targetPage = currentPage
            activeProgress = AnimatedFloat(0f)
            restAmount.snapTo(1f)
            buttonPresence.snapTo(1f)
        }
        val active = activeProgress
        val committing = isTransitioning && active.isAnimating && active.animationTarget >= 1f
        if (committing) {
            // Keep the animation running until the first movement tells us what the user wants.
            pendingHandOffDecision = true
        } else {
            // Detach a running cancel animation: it finishes on a value nobody reads anymore.
            if (active.isAnimating) activeProgress = AnimatedFloat(active.value)
            pendingHandOffDecision = false
        }
        dragFraction = dragFractionFor(progress)
        if (spec.waveCenterFollowsPointer && !waveCenterActive) {
            waveCenter.snapTo(restCenterY)
            waveCenterActive = true
        }
        waveCenterTarget = waveCenter.value
        isDragging = true
    }

    private fun dragFractionFor(progress: Float): Float = when {
        targetPage > currentPage -> progress / spec.forwardDragFraction
        targetPage < currentPage -> -progress / spec.backwardDragFraction
        else -> 0f
    }

    /**
     * @param forwardDeltaPx Finger movement in the forward direction, in pixels.
     * @param pointerY Vertical finger position, in pixels; the wave center chases it in
     * [chaseWaveCenter], once per frame.
     */
    internal fun drag(forwardDeltaPx: Float, pointerY: Float) {
        waveCenterTarget = pointerY
        val width = viewportSize.width.toFloat()
        if (width <= 0f) return

        if (pendingHandOffDecision && forwardDeltaPx != 0f) {
            pendingHandOffDecision = false
            val fingerForward = forwardDeltaPx > 0f
            val transitionForward = targetPage > currentPage
            if (fingerForward == transitionForward) {
                handOffActiveTransition()
            } else {
                // Grab the running wave: from here on the finger drives it.
                activeProgress = AnimatedFloat(activeProgress.value)
                dragFraction = dragFractionFor(progress)
            }
        }

        var fraction = dragFraction + forwardDeltaPx / width
        if (!canScrollForward) fraction = min(fraction, 0f)
        if (!canScrollBackward) fraction = max(fraction, 0f)
        finishing?.let { f ->
            // The page still finishing can't be revealed again until it is done.
            if (f.forward) fraction = max(fraction, 0f) else fraction = min(fraction, 0f)
        }
        fraction = fraction.coerceIn(-1f / spec.backwardDragFraction, 1f / spec.forwardDragFraction)
        dragFraction = fraction

        targetPage = when {
            fraction > 0f -> currentPage + 1
            fraction < 0f -> currentPage - 1
            else -> currentPage
        }
        val newProgress = when {
            fraction > 0f -> fraction * spec.forwardDragFraction
            fraction < 0f -> -fraction * spec.backwardDragFraction
            else -> 0f
        }.coerceIn(0f, 1f)
        activeProgress.snapTo(newProgress)
    }

    /**
     * Moves the wave center towards the finger at [LiquidWaveSpec.waveCenterSpeed]. Called once per
     * frame while dragging, so the motion is smooth regardless of how pointer events arrive.
     */
    internal fun chaseWaveCenter(frameSeconds: Float) {
        if (!isDragging || !spec.waveCenterFollowsPointer || !waveCenterActive) return
        val speed = with(density) { spec.waveCenterSpeed.toPx() }
        val maxStep = speed * frameSeconds
        val current = waveCenter.value
        val delta = (waveCenterTarget - current).coerceIn(-maxStep, maxStep)
        if (delta != 0f) waveCenter.snapTo(current + delta)
    }

    /**
     * Hands the active (committing) transition off to the finishing layer: its animation keeps
     * running untouched on its own value, the target page becomes the current page right away and
     * a fresh value takes over for the gesture that is starting.
     */
    private fun handOffActiveTransition() {
        val from = currentPage
        val to = targetPage
        finishing = FinishingTransition(
            from = from,
            to = to,
            progress = activeProgress,
            centerY = waveCenterY(restCenterY),
            restAmount = restAmount.value,
        )
        activeProgress = AnimatedFloat(0f)
        currentPage = to
        targetPage = to
        dragFraction = 0f
        if (to > from) {
            // The new current page grows its bulge once the old wave is gone.
            restAmount.snapTo(0f)
        } else {
            // The arriving page already carries its bulge in the finishing mask.
            restAmount.snapTo(1f)
        }
        buttonPresence.snapTo(0f)
    }

    /**
     * @param forwardVelocityPxPerSec Finger velocity in the forward direction.
     * @param positionalThreshold Fraction of the width that must be dragged to commit.
     * @param velocityThresholdPxPerSec Velocity above which the direction of the fling decides.
     */
    internal suspend fun dragEnd(
        forwardVelocityPxPerSec: Float,
        positionalThreshold: Float,
        velocityThresholdPxPerSec: Float,
        animationSpec: AnimationSpec<Float>,
    ) {
        isDragging = false
        dragFraction = 0f
        if (pendingHandOffDecision) {
            // The finger never moved: the committing animation was left running, let it finish.
            pendingHandOffDecision = false
            return
        }
        if (!isTransitioning) {
            glideWaveCenterToRest()
            return
        }
        val forward = targetPage > currentPage
        val towardTarget = if (forward) forwardVelocityPxPerSec > 0f else forwardVelocityPxPerSec < 0f
        val commit = if (abs(forwardVelocityPxPerSec) >= velocityThresholdPxPerSec) {
            towardTarget
        } else {
            val dragFractionForFullTransition =
                if (forward) spec.forwardDragFraction else spec.backwardDragFraction
            progress >= positionalThreshold * dragFractionForFullTransition
        }
        val target = if (commit) 1f else 0f
        // Unless the current page is being swiped away, the wave center eases back to its resting
        // position while the release animation plays, so the bulge (and the button on it) forms
        // where it belongs instead of jumping there afterwards.
        val glideDuringRelease = !(forward && commit)
        coroutineScope {
            if (glideDuringRelease) {
                val duration = remainingMillis(target, animationSpec)
                    .coerceIn(MIN_GLIDE_MILLIS, MAX_GLIDE_MILLIS)
                launch { glideWaveCenterToRest(duration) }
            }
            runTransition(target, animationSpec, glideAfter = !glideDuringRelease)
        }
    }

    /**
     * Clears the drag flags without settling anything. Used when the pager leaves composition
     * mid-gesture and no coroutine scope is left to animate with; the next interaction resumes
     * from the current progress.
     */
    internal fun abandonDrag() {
        isDragging = false
        pendingHandOffDecision = false
        dragFraction = 0f
    }

    // endregion

    /**
     * Animates to [page] with the liquid transition. Pages further than one position away are
     * revealed directly, without going through the pages in between. Ignored while the user is
     * dragging.
     *
     * Tween specs are interpreted as the duration of a full transition, so a partially revealed
     * page takes proportionally less time.
     */
    suspend fun animateToPage(
        page: Int,
        animationSpec: AnimationSpec<Float> = LiquidPagerDefaults.AnimationSpec,
    ) {
        val count = pageCount
        if (count == 0 || isDragging) return
        val target = page.coerceIn(0, count - 1)
        if (target == currentPage) {
            if (!isTransitioning) return
            // Going back to the settled page: reverse the current transition.
            runTransition(0f, animationSpec)
            return
        }
        val active = activeProgress
        if (isTransitioning && target != targetPage) {
            val committing = active.isAnimating && active.animationTarget >= 1f
            val sameDirection = (target > currentPage) == (targetPage > currentPage)
            if (committing && sameDirection && abs(targetPage - currentPage) == 1) {
                handOffActiveTransition()
            } else {
                // The current reveal can't be reused for another target.
                active.snapTo(0f)
            }
        }
        finishing?.let { f ->
            if (f.from == target) completeFinishingNow(f)
        }
        if (target == currentPage) return
        targetPage = target
        runTransition(1f, animationSpec)
    }

    /** Jumps to [page] without any animation. */
    suspend fun scrollToPage(page: Int) {
        val count = pageCount
        if (count == 0) return
        finishing?.let { completeFinishingNow(it) }
        val target = page.coerceIn(0, count - 1)
        currentPage = target
        targetPage = target
        activeProgress.snapTo(0f)
        restAmount.snapTo(1f)
        buttonPresence.snapTo(1f)
        waveCenterActive = false
        pendingHandOffDecision = false
    }

    internal suspend fun onPageCountChanged() {
        val count = pageCount
        if (currentPage >= count || targetPage >= count) {
            scrollToPage(max(count - 1, 0))
        }
    }

    /**
     * Animates the active transition to [target] (`1` commits, `0` cancels) and applies the
     * outcome. If the value was taken over meanwhile (a new drag, a snap, another animation)
     * nothing else happens; if the transition was handed off while animating, only the finishing
     * bookkeeping runs because the pages were already updated by the hand-off.
     */
    private suspend fun runTransition(
        target: Float,
        animationSpec: AnimationSpec<Float>,
        glideAfter: Boolean = true,
    ) {
        val value = activeProgress
        if (!animateProgress(value, target, animationSpec)) return
        if (value === activeProgress) {
            completeTransition(completed = target >= 1f, glideAfter = glideAfter)
        } else {
            val f = finishing
            if (f != null && f.progress === value) {
                finishing = null
                if (f.forward) growRest() else buttonPresence.animateTo(1f, REST_GROW_SPEC)
            }
        }
    }

    /** Time the release animation will take to reach [target], for tween specs. */
    private fun remainingMillis(target: Float, animationSpec: AnimationSpec<Float>): Int =
        if (animationSpec is TweenSpec<Float>) {
            (animationSpec.durationMillis * abs(target - progress)).roundToInt()
        } else {
            DEFAULT_GLIDE_MILLIS
        }

    /** Returns `false` if the animation was superseded before reaching [target]. */
    private suspend fun animateProgress(
        value: AnimatedFloat,
        target: Float,
        animationSpec: AnimationSpec<Float>,
    ): Boolean {
        val distance = abs(target - value.value)
        if (distance == 0f) return true
        val effectiveSpec = if (animationSpec is TweenSpec<Float>) {
            tween(
                durationMillis = (animationSpec.durationMillis * distance).roundToInt().coerceAtLeast(1),
                delayMillis = animationSpec.delay,
                easing = animationSpec.easing,
            )
        } else {
            animationSpec
        }
        return value.animateTo(target, effectiveSpec)
    }

    private suspend fun completeTransition(completed: Boolean, glideAfter: Boolean) {
        val from = currentPage
        val to = targetPage
        if (completed) {
            currentPage = to
            activeProgress.snapTo(0f)
            if (to > from) {
                // The new page grows its own resting bulge (and button) at the resting center.
                restAmount.snapTo(0f)
                buttonPresence.snapTo(0f)
                waveCenterActive = false
                growRest()
            } else {
                // The revealed page already ends with its resting bulge; only the button appears.
                restAmount.snapTo(1f)
                buttonPresence.snapTo(0f)
                coroutineScope {
                    launch { buttonPresence.animateTo(1f, REST_GROW_SPEC) }
                    if (glideAfter) launch { glideWaveCenterToRest() }
                }
            }
        } else {
            targetPage = from
            activeProgress.snapTo(0f)
            if (glideAfter) glideWaveCenterToRest()
        }
    }

    /** Ends a finishing transition immediately; its own coroutine then finds nothing to do. */
    private fun completeFinishingNow(f: FinishingTransition) {
        f.progress.snapTo(1f)
        if (finishing === f) finishing = null
    }

    private suspend fun growRest() = coroutineScope {
        launch { restAmount.animateTo(1f, REST_GROW_SPEC) }
        launch { buttonPresence.animateTo(1f, REST_GROW_SPEC) }
    }

    /** Eases the wave center back to its resting position over [durationMillis]. */
    private suspend fun glideWaveCenterToRest(durationMillis: Int = DEFAULT_GLIDE_MILLIS) {
        if (!waveCenterActive) return
        val target = restCenterY
        if (abs(target - waveCenter.value) > 0.5f) {
            val completed = waveCenter.animateTo(target, tween(durationMillis, easing = FastOutSlowInEasing))
            // A new drag started chasing the finger meanwhile: the center stays active.
            if (!completed) return
        }
        waveCenterActive = false
    }

    companion object {
        /** Fraction of a backward reveal during which the resting bulge of the current page collapses. */
        private const val BACKWARD_COLLAPSE_END = 0.15f

        private val REST_GROW_SPEC: AnimationSpec<Float> = tween(durationMillis = 300)

        /** Duration of the wave center's return to rest when it can't be matched to a release animation. */
        private const val DEFAULT_GLIDE_MILLIS = 400
        private const val MIN_GLIDE_MILLIS = 250
        private const val MAX_GLIDE_MILLIS = 600

        /** Saver that restores the settled page. */
        fun Saver(pageCount: () -> Int): Saver<LiquidPagerState, *> = listSaver(
            save = {
                listOf(if (it.progress >= 0.5f) it.targetPage else it.currentPage)
            },
            restore = { LiquidPagerState(currentPage = it[0], pageCount = pageCount) },
        )
    }
}

/**
 * A float that can be set immediately from any thread of execution (no coroutine needed) or
 * animated. A snap or a newer animation supersedes a running animation: the old one keeps its
 * frames to itself and reports that it did not complete.
 */
internal class AnimatedFloat(initial: Float) {
    var value: Float by mutableFloatStateOf(initial)
        private set

    /** `true` while an [animateTo] that has not been superseded is running. */
    var isAnimating: Boolean by mutableStateOf(false)
        private set

    /** Target of the running animation; meaningless when not animating. */
    var animationTarget: Float = initial
        private set

    private var generation = 0

    fun snapTo(newValue: Float) {
        generation++
        isAnimating = false
        value = newValue
    }

    /** Animates from the current value to [target]. Returns `false` if superseded meanwhile. */
    suspend fun animateTo(target: Float, animationSpec: AnimationSpec<Float>): Boolean {
        val myGeneration = ++generation
        animationTarget = target
        isAnimating = true
        try {
            animate(initialValue = value, targetValue = target, animationSpec = animationSpec) { v, _ ->
                if (generation == myGeneration) value = v
            }
        } finally {
            if (generation == myGeneration) isAnimating = false
        }
        return generation == myGeneration
    }
}

/**
 * A transition that was handed off by a new gesture and keeps animating to completion.
 *
 * @param from Page that was current when the transition started.
 * @param to Page being revealed (already the current page once handed off).
 * @param progress The value that is still animating towards `1`.
 * @param centerY Wave center captured at hand-off.
 * @param restAmount Resting bulge amount captured at hand-off, keeps the mask continuous.
 */
internal class FinishingTransition(
    val from: Int,
    val to: Int,
    val progress: AnimatedFloat,
    val centerY: Float,
    val restAmount: Float,
) {
    val forward: Boolean get() = to > from
}
