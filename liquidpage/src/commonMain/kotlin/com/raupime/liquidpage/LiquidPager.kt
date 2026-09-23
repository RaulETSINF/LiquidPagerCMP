package com.raupime.liquidpage

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A pager whose pages are revealed with a liquid wave, in the style of Cuberto's liquid-swipe.
 *
 * Swiping forward carves a wave into the current page through which the next page appears;
 * swiping backward pours the previous page in from the leading edge. Pages never move: they are
 * clipped in place, so the effect works with any content.
 *
 * Only the pages next to [LiquidPagerState.currentPage] (and the page being revealed) are
 * composed. Page state is preserved with `rememberSaveable` while a page is not composed. Only the
 * current page is exposed to accessibility services; the pager itself exposes page left/right and
 * scroll actions.
 *
 * @param state State of the pager, see [rememberLiquidPagerState].
 * @param modifier Modifier for the pager. It fills the available space; give it a size when the
 * constraints are unbounded.
 * @param waveSpec Geometry and choreography of the wave.
 * @param animationSpec Animation used when a transition settles or is triggered programmatically.
 * Tween durations are the duration of a full transition, partial ones take proportionally less.
 * @param userScrollEnabled Whether the user can swipe between pages.
 * @param snapPositionalThreshold Fraction of the width the finger must travel to commit a
 * transition on release when moving slowly.
 * @param snapVelocityThreshold Fling velocity (per second) above which the fling direction decides
 * whether the transition commits.
 * @param key Optional stable key for each page, used to preserve saved state.
 * @param nextButton Button shown on the resting bulge of the current page. `null` hides it. The
 * default one animates to the next page when tapped.
 * @param pagesAreOpaque When `true` (the default) the parts of a page hidden behind another page are
 * not drawn at all, which saves a full-screen overdraw per frame. Set it to `false` if your pages
 * have transparent areas through which the page underneath must show.
 * @param content Content of each page.
 */
@Composable
fun LiquidPager(
    state: LiquidPagerState,
    modifier: Modifier = Modifier,
    waveSpec: LiquidWaveSpec = LiquidWaveSpec.Default,
    animationSpec: AnimationSpec<Float> = LiquidPagerDefaults.AnimationSpec,
    userScrollEnabled: Boolean = true,
    snapPositionalThreshold: Float = LiquidPagerDefaults.SnapPositionalThreshold,
    snapVelocityThreshold: Dp = LiquidPagerDefaults.SnapVelocityThreshold,
    key: ((page: Int) -> Any)? = null,
    nextButton: (@Composable () -> Unit)? = { LiquidPagerDefaults.NextButton(state) },
    pagesAreOpaque: Boolean = true,
    content: @Composable (page: Int) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    state.density = density
    SideEffect { state.spec = waveSpec }

    val pageCount = state.pageCount
    LaunchedEffect(state, pageCount) { state.onPageCountChanged() }
    // If the pager leaves composition mid-gesture no drag end arrives: don't leave it stuck.
    DisposableEffect(state) { onDispose { state.abandonDrag() } }
    // While dragging, the wave center chases the finger once per frame (independent of how often
    // pointer events arrive, which varies between platforms and input devices).
    LaunchedEffect(state) {
        snapshotFlow { state.isDragging }.collectLatest { dragging ->
            if (!dragging) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                state.chaseWaveCenter((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }

    val scope = rememberCoroutineScope()
    // Read through State so the gesture detector never restarts when these change mid-drag.
    val currentAnimationSpec = rememberUpdatedState(animationSpec)
    val currentPositionalThreshold = rememberUpdatedState(snapPositionalThreshold)
    val currentVelocityThresholdPx = rememberUpdatedState(with(density) { snapVelocityThreshold.toPx() })
    val saveableStateHolder = rememberSaveableStateHolder()
    val current = state.currentPage
    // Derived so that the target flipping between neighbours during a drag doesn't recompose.
    val pages by remember(state) {
        derivedStateOf {
            val finishingPages = state.finishing?.let { intArrayOf(it.from, it.to) } ?: IntArray(0)
            visiblePages(state.currentPage, state.targetPage, state.pageCount, finishingPages)
        }
    }
    val buttonGap = with(density) { LiquidPagerDefaults.NextButtonGap.toPx() }
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val currentIsRtl = rememberUpdatedState(isRtl)
    // Without a resting bulge there is nothing to put the button on.
    val showNextButton = nextButton != null && waveSpec.restHorizontalRadius > 0.dp

    Layout(
        content = {
            pages.forEach { page ->
                val pageKey = key?.invoke(page) ?: page
                androidx.compose.runtime.key(pageKey) {
                    val maskPath = remember { Path() }
                    saveableStateHolder.SaveableStateProvider(pageKey) {
                        Box(
                            modifier = Modifier
                                .layoutId(page)
                                .liquidPageMask(state, page, maskPath, cullHidden = pagesAreOpaque)
                                // Pages underneath or still arriving are not read by accessibility.
                                .then(if (page == current) Modifier else Modifier.clearAndSetSemantics {}),
                            propagateMinConstraints = true,
                        ) {
                            // Own layer for the content: the clip above is re-recorded every frame,
                            // the content's display list is not.
                            Box(Modifier.graphicsLayer(), propagateMinConstraints = true) {
                                content(page)
                            }
                        }
                    }
                }
            }
            if (showNextButton) {
                Box(
                    modifier = Modifier
                        .layoutId(NextButtonId)
                        .nextButtonLayer(state, isRtl, buttonGap),
                ) {
                    nextButton.invoke()
                }
            }
        },
        modifier = modifier
            .clipToBounds()
            .liquidPagerSemantics(state, isRtl, scope)
            .then(
                if (userScrollEnabled && pageCount > 1) {
                    Modifier.liquidPagerGestures(
                        state = state,
                        animationSpec = currentAnimationSpec,
                        positionalThreshold = currentPositionalThreshold,
                        velocityThresholdPx = currentVelocityThresholdPx,
                        isRtl = currentIsRtl,
                        scope = scope,
                    )
                } else {
                    Modifier
                }
            ),
    ) { measurables, constraints ->
        val pageConstraints = Constraints(
            minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
            maxWidth = constraints.maxWidth,
            minHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
            maxHeight = constraints.maxHeight,
        )
        val pagePlaceables = HashMap<Int, androidx.compose.ui.layout.Placeable>()
        var buttonMeasurable: androidx.compose.ui.layout.Measurable? = null
        measurables.forEach { measurable ->
            when (val id = measurable.layoutId) {
                NextButtonId -> buttonMeasurable = measurable
                is Int -> pagePlaceables[id] = measurable.measure(pageConstraints)
            }
        }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (pagePlaceables.values.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            (pagePlaceables.values.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        val buttonPlaceable = buttonMeasurable?.measure(Constraints(maxWidth = width, maxHeight = height))
        state.viewportSize = IntSize(width, height)

        layout(width, height) {
            // Placement order and z-index decide who is on top: the clipped page must be above
            // the page being revealed through it.
            val currentPage = state.currentPage
            val targetPage = state.targetPage
            val finishing = state.finishing
            // The next page always sits underneath: it peeks through the resting bulge.
            val nextPage = currentPage + 1
            if (nextPage != targetPage) pagePlaceables[nextPage]?.place(0, 0, zIndex = -1f)
            // A page handed off while leaving keeps finishing its wave on top of everything.
            if (finishing != null && finishing.forward) {
                pagePlaceables[finishing.from]?.place(0, 0, zIndex = 3f)
            }
            when {
                targetPage > currentPage -> {
                    pagePlaceables[targetPage]?.place(0, 0, zIndex = 0f)
                    pagePlaceables[currentPage]?.place(0, 0, zIndex = 1f)
                }

                targetPage < currentPage -> {
                    pagePlaceables[currentPage]?.place(0, 0, zIndex = 0f)
                    pagePlaceables[targetPage]?.place(0, 0, zIndex = 1f)
                }

                else -> pagePlaceables[currentPage]?.place(0, 0, zIndex = 0f)
            }
            if (buttonPlaceable != null && currentPage < state.pageCount - 1) {
                buttonPlaceable.place(0, 0, zIndex = 2f)
            }
        }
    }
}

/** Pages that must stay composed: the neighbours of the current page plus the target page. */
private fun visiblePages(current: Int, target: Int, pageCount: Int, extra: IntArray): List<Int> {
    if (pageCount <= 0) return emptyList()
    val pages = ArrayList<Int>(6)
    for (page in intArrayOf(current - 1, current, current + 1, target, *extra)) {
        if (page in 0 until pageCount && page !in pages) pages.add(page)
    }
    pages.sort()
    return pages
}

private object NextButtonId

/**
 * Clips a page with its liquid mask, reading the state in the draw phase so the animation only
 * re-records this layer: no recomposition and no re-layout per frame.
 *
 * Anti-aliased path clipping is expensive: the renderer builds a coverage mask as large as the
 * clip. So the path clip is applied only to the rectangle actually touched by the wave; everything
 * else is drawn through pixel-aligned rect clips, which are free. At rest the path region is the
 * ~60 dp bulge instead of the whole screen. With [cullHidden], the parts of the page hidden behind
 * the page above are skipped as well.
 */
private fun Modifier.liquidPageMask(
    state: LiquidPagerState,
    page: Int,
    path: Path,
    cullHidden: Boolean,
): Modifier = graphicsLayer().drawWithContent {
    val geometry = state.maskGeometry(page, size, this)
    val coveredLeft = if (cullHidden) state.coveredWidth(page, size, this) else 0f
    if (geometry == null || geometry.isFullyRevealed) {
        if (coveredLeft <= 0f) {
            drawContent()
        } else if (coveredLeft < size.width) {
            drawVisible(coveredLeft, 0f, size.width, size.height)
        }
    } else {
        drawMasked(geometry, path, coveredLeft)
    }
}

/** Draws the content clipped to a rect given in leading-edge coordinates (mirrored in RTL). */
private fun ContentDrawScope.drawVisible(left: Float, top: Float, right: Float, bottom: Float) {
    if (right <= left || bottom <= top) return
    if (layoutDirection == LayoutDirection.Rtl) {
        clipRect(size.width - right, top, size.width - left, bottom) { this@drawVisible.drawContent() }
    } else {
        clipRect(left, top, right, bottom) { this@drawVisible.drawContent() }
    }
}

private fun ContentDrawScope.drawMasked(geometry: LiquidWaveGeometry, path: Path, coveredLeft: Float) {
    val width = size.width
    val height = size.height
    val maskWidth = width - geometry.sideWidth
    val radius = geometry.horizontalRadius
    // Rect touched by the wave, expanded to whole pixels so the plain regions around it never
    // share a partially covered pixel column or row with it (that would show as a seam).
    val waveLeft = floor(min(maskWidth - radius, maskWidth)).coerceIn(0f, width)
    val waveRight = ceil(max(maskWidth - radius, maskWidth)).coerceIn(0f, width)
    val waveTop = floor(geometry.centerY - geometry.verticalRadius).coerceIn(0f, height)
    val waveBottom = ceil(geometry.centerY + geometry.verticalRadius).coerceIn(0f, height)
    val visibleRight = maskWidth.coerceIn(0f, width)

    // 1. Left of the wave: fully visible.
    drawVisible(coveredLeft, 0f, waveLeft, height)
    // 2. Same columns as the wave but above and below it: fully visible up to the mask edge.
    drawVisible(max(waveLeft, coveredLeft), 0f, visibleRight, waveTop)
    drawVisible(max(waveLeft, coveredLeft), waveBottom, visibleRight, height)
    // 3. The wave itself: the only region that needs the anti-aliased path clip.
    val pathLeft = max(waveLeft, coveredLeft)
    if (waveRight > pathLeft && waveBottom > waveTop) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        path.rewind()
        path.addLiquidWave(size, geometry, mirrored = rtl)
        val left = if (rtl) width - waveRight else pathLeft
        val right = if (rtl) width - pathLeft else waveRight
        clipRect(left, waveTop, right, waveBottom) {
            clipPath(path) { this@drawMasked.drawContent() }
        }
    }
}

/** Keeps the "next" button on the tip of the resting bulge, fading it out while swiping. */
private fun Modifier.nextButtonLayer(
    state: LiquidPagerState,
    isRtl: Boolean,
    gapPx: Float,
): Modifier = graphicsLayer {
    val viewport = state.viewportSize
    if (viewport == IntSize.Zero) {
        alpha = 0f
        return@graphicsLayer
    }
    val viewportSize = Size(viewport.width.toFloat(), viewport.height.toFloat())
    val geometry = state.currentPageGeometry(viewportSize, this)
    val leading = geometry.tipX(viewportSize.width) + gapPx
    translationX = if (isRtl) viewportSize.width - leading - size.width else leading
    translationY = geometry.centerY - size.height / 2f

    val presence = state.effectiveButtonPresence
    val swipeAlpha = if (state.targetPage > state.currentPage) {
        LiquidWaveMath.buttonAlpha(state.progress)
    } else {
        1f
    }
    alpha = presence * swipeAlpha
    scaleX = presence
    scaleY = presence
}

/**
 * Exposes the pager to accessibility services: position in pages, page left/right actions and
 * scroll actions (TalkBack's "scroll forward/backward" swipes).
 */
private fun Modifier.liquidPagerSemantics(
    state: LiquidPagerState,
    isRtl: Boolean,
    scope: CoroutineScope,
): Modifier = semantics {
    fun goForward(): Boolean {
        if (!state.canScrollForward) return false
        scope.launch { state.animateToPage(state.currentPage + 1) }
        return true
    }
    fun goBackward(): Boolean {
        if (!state.canScrollBackward) return false
        scope.launch { state.animateToPage(state.currentPage - 1) }
        return true
    }
    horizontalScrollAxisRange = ScrollAxisRange(
        value = { state.currentPage + state.currentPageOffsetFraction },
        maxValue = { (state.pageCount - 1).coerceAtLeast(0).toFloat() },
        reverseScrolling = isRtl,
    )
    collectionInfo = CollectionInfo(rowCount = 1, columnCount = state.pageCount)
    pageRight { if (isRtl) goBackward() else goForward() }
    pageLeft { if (isRtl) goForward() else goBackward() }
    scrollBy { x, _ ->
        when {
            x > 0f -> if (isRtl) goBackward() else goForward()
            x < 0f -> if (isRtl) goForward() else goBackward()
            else -> false
        }
    }
}

/**
 * Horizontal drag detection. The drag is applied to the state from inside the pointer input
 * coroutine, right when each event is dispatched, instead of launching a coroutine per event: on
 * some platforms a launched coroutine is not guaranteed to run before the next frame, which shows
 * as stutter under the finger. Only the settle animation, which outlives the gesture, is launched.
 */
private fun Modifier.liquidPagerGestures(
    state: LiquidPagerState,
    animationSpec: State<AnimationSpec<Float>>,
    positionalThreshold: State<Float>,
    velocityThresholdPx: State<Float>,
    isRtl: State<Boolean>,
    scope: CoroutineScope,
): Modifier = pointerInput(state) {
    val velocityTracker = VelocityTracker()
    fun forward(deltaX: Float): Float = if (isRtl.value) deltaX else -deltaX
    fun settle(velocityX: Float) {
        scope.launch {
            state.dragEnd(
                forwardVelocityPxPerSec = forward(velocityX),
                positionalThreshold = positionalThreshold.value,
                velocityThresholdPxPerSec = velocityThresholdPx.value,
                animationSpec = animationSpec.value,
            )
        }
    }
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var overSlop = 0f
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            } ?: return@awaitEachGesture

            velocityTracker.resetTracking()
            velocityTracker.addPosition(start.uptimeMillis, start.position)
            state.dragStart()
            state.drag(forward(overSlop), start.position.y)
            // Track the absolute position ourselves: a change dispatched while the state was being
            // updated is skipped by the pointer input coroutine, and its delta must not be lost.
            var lastX = start.position.x
            var pointerId = start.id
            var cancelled = false
            while (true) {
                val change = awaitHorizontalDragOrCancellation(pointerId)
                if (change == null) {
                    cancelled = true
                    break
                }
                if (change.changedToUpIgnoreConsumed()) break
                pointerId = change.id
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                val deltaX = change.position.x - lastX
                lastX = change.position.x
                state.drag(forward(deltaX), change.position.y)
            }
            settle(if (cancelled) 0f else velocityTracker.calculateVelocity().x)
        }
    } finally {
        // The gesture modifier went away (userScrollEnabled turned off, pager disposed...) while a
        // drag may be in progress: settle it as a cancelled drag so the state never gets stuck.
        if (state.isDragging) settle(0f)
    }
}

/** Default values used by [LiquidPager]. */
object LiquidPagerDefaults {
    /**
     * Constant-speed transition lasting 800 ms from one page to the next, like the original. The
     * liquid feel comes from the wave choreography itself, so no easing is applied.
     */
    val AnimationSpec: AnimationSpec<Float> = tween(durationMillis = 800, easing = LinearEasing)

    /** Fraction of the width the finger must travel to commit a transition. */
    const val SnapPositionalThreshold: Float = 1f / 3f

    /** Fling velocity per second above which the fling direction decides the outcome. */
    val SnapVelocityThreshold: Dp = 400.dp

    /** Distance between the wave tip and the leading edge of the "next" button. */
    val NextButtonGap: Dp = 8.dp

    /**
     * A round 48dp button with a chevron, placed on the resting bulge. Tapping it animates to the
     * next page unless [onClick] is provided.
     */
    @Composable
    fun NextButton(
        state: LiquidPagerState,
        modifier: Modifier = Modifier,
        tint: Color = Color.White,
        contentDescription: String? = "Next page",
        onClick: (() -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val enabled = !state.isTransitioning && !state.isDragging
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    if (onClick != null) {
                        onClick()
                    } else {
                        scope.launch { state.animateToPage(state.currentPage + 1) }
                    }
                }
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val chevron = Path().apply {
                    moveTo(size.width * 0.38f, size.height * 0.25f)
                    lineTo(size.width * 0.63f, size.height * 0.5f)
                    lineTo(size.width * 0.38f, size.height * 0.75f)
                }
                drawPath(chevron, color = tint, style = stroke)
            }
        }
    }
}

