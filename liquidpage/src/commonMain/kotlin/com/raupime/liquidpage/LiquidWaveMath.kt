package com.raupime.liquidpage

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Pure functions describing how the wave parameters evolve with the transition progress.
 * Ported from Cuberto's liquid-swipe (MIT). Progress is always in `0..1`.
 */
internal object LiquidWaveMath {
    /** Progress at which the wave stops growing and the damped oscillation starts. */
    private const val GROW_END = 0.4f

    /** Progress range during which the side strip sweeps across the whole width. */
    private const val SIDE_START = 0.2f
    private const val SIDE_END = 0.8f

    // Damped harmonic oscillator constants (friction, mass, stiffness).
    private const val FRICTION = 40f
    private const val MASS = 9.8f
    private const val STIFFNESS = 50f
    private val BETA = FRICTION / (2f * MASS)
    private val OMEGA0 = STIFFNESS / MASS
    private val OMEGA = sqrt(OMEGA0 * OMEGA0 - BETA * BETA)

    /** Damped oscillation with amplitude [amplitude] over the normalized time `t` in `0..1`. */
    private fun dampedOscillation(amplitude: Float, t: Float): Float =
        amplitude * exp(-BETA * t) * cos(OMEGA * t)

    /**
     * Horizontal radius of the forward wave: grows linearly from [initial] to [max] until
     * [GROW_END], then oscillates and dies out, overshooting into negative values (the old page
     * bulges back before disappearing), which is what gives the effect its liquid feel.
     */
    fun forwardHorizontalRadius(progress: Float, initial: Float, max: Float): Float {
        if (progress <= 0f) return initial
        if (progress >= 1f) return 0f
        if (progress <= GROW_END) return initial + progress / GROW_END * (max - initial)
        val t = (progress - GROW_END) / (1f - GROW_END)
        return dampedOscillation(max, t)
    }

    /**
     * Horizontal radius of the wave of a page being revealed from the previous position. Here the
     * curve is parameterized by the *hidden* fraction: `1` means fully hidden, `0` means fully
     * shown (which coincides with the resting bulge of radius [rest]).
     */
    fun backwardHorizontalRadius(hidden: Float, rest: Float, amplitude: Float): Float {
        if (hidden <= 0f) return rest
        if (hidden >= 1f) return 0f
        val peak = 2f * amplitude
        if (hidden <= GROW_END) return rest + hidden / GROW_END * (peak - rest)
        val t = (hidden - GROW_END) / (1f - GROW_END)
        return dampedOscillation(peak, t)
    }

    /** Vertical radius: grows linearly from [initial] to [max] until [GROW_END]. */
    fun verticalRadius(progress: Float, initial: Float, max: Float): Float {
        if (progress <= 0f) return initial
        if (progress >= GROW_END) return max
        return initial + (max - initial) * progress / GROW_END
    }

    /** Width of the revealed strip along the trailing edge, sweeping across the page. */
    fun sideWidth(progress: Float, initial: Float, width: Float): Float {
        if (progress <= SIDE_START) return initial
        if (progress >= SIDE_END) return width
        return initial + (width - initial) * (progress - SIDE_START) / (SIDE_END - SIDE_START)
    }

    /** Opacity of the "next" button while swiping forward: visible until 10%, gone at 30%. */
    fun buttonAlpha(progress: Float): Float {
        val start = 0.1f
        val end = 0.3f
        if (progress <= start) return 1f
        if (progress >= end) return 0f
        return 1f - (progress - start) / (end - start)
    }
}
