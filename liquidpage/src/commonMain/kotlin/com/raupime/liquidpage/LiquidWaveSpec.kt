package com.raupime.liquidpage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Describes the geometry and choreography of the liquid wave.
 *
 * The defaults reproduce Cuberto's original liquid-swipe design: a small resting bulge on the
 * trailing edge of the current page (through which the next page peeks), a big wave that follows
 * the finger when swiping forward, and a flatter "curtain" with a small wave when swiping back.
 *
 * All sizes are density independent so the effect looks the same on every screen.
 *
 * @param restHorizontalRadius Horizontal radius of the resting bulge (`0.dp` disables the bulge).
 * @param restVerticalRadius Vertical radius of the wave at rest and at the start of a swipe.
 * @param restSideWidth Width of the strip revealed along the trailing edge at rest.
 * @param restWaveCenterFraction Vertical position of the wave center at rest, as a fraction of the height.
 * @param maxHorizontalRadiusFraction Maximum horizontal radius of the forward wave, as a fraction of the width.
 * @param maxVerticalRadiusFraction Maximum vertical radius of the wave, as a fraction of the height.
 * @param backwardWaveAmplitude Amplitude of the small wave used when swiping back to the previous page.
 * @param forwardDragFraction Progress reached when the finger drags a full width forward. Cuberto uses
 * `0.45` so the wave tip roughly follows the finger; `1f` makes a full drag complete the transition.
 * @param backwardDragFraction Progress reached when the finger drags a full width backward.
 * @param waveCenterFollowsPointer Whether the wave center chases the finger vertically while dragging.
 * @param waveCenterSpeed Speed (per second) at which the wave center chases the finger while dragging.
 * When the finger lifts, the center eases back to its resting position along with the release animation.
 */
@Immutable
class LiquidWaveSpec(
    val restHorizontalRadius: Dp = 48.dp,
    val restVerticalRadius: Dp = 82.dp,
    val restSideWidth: Dp = 15.dp,
    val restWaveCenterFraction: Float = 0.7167487685f,
    val maxHorizontalRadiusFraction: Float = 0.8f,
    val maxVerticalRadiusFraction: Float = 0.9f,
    val backwardWaveAmplitude: Dp = 48.dp,
    val forwardDragFraction: Float = 0.45f,
    val backwardDragFraction: Float = 1f,
    val waveCenterFollowsPointer: Boolean = true,
    val waveCenterSpeed: Dp = 2000.dp,
) {
    init {
        require(forwardDragFraction > 0f) { "forwardDragFraction must be > 0" }
        require(backwardDragFraction > 0f) { "backwardDragFraction must be > 0" }
        require(restWaveCenterFraction in 0f..1f) { "restWaveCenterFraction must be in 0..1" }
    }

    fun copy(
        restHorizontalRadius: Dp = this.restHorizontalRadius,
        restVerticalRadius: Dp = this.restVerticalRadius,
        restSideWidth: Dp = this.restSideWidth,
        restWaveCenterFraction: Float = this.restWaveCenterFraction,
        maxHorizontalRadiusFraction: Float = this.maxHorizontalRadiusFraction,
        maxVerticalRadiusFraction: Float = this.maxVerticalRadiusFraction,
        backwardWaveAmplitude: Dp = this.backwardWaveAmplitude,
        forwardDragFraction: Float = this.forwardDragFraction,
        backwardDragFraction: Float = this.backwardDragFraction,
        waveCenterFollowsPointer: Boolean = this.waveCenterFollowsPointer,
        waveCenterSpeed: Dp = this.waveCenterSpeed,
    ): LiquidWaveSpec = LiquidWaveSpec(
        restHorizontalRadius = restHorizontalRadius,
        restVerticalRadius = restVerticalRadius,
        restSideWidth = restSideWidth,
        restWaveCenterFraction = restWaveCenterFraction,
        maxHorizontalRadiusFraction = maxHorizontalRadiusFraction,
        maxVerticalRadiusFraction = maxVerticalRadiusFraction,
        backwardWaveAmplitude = backwardWaveAmplitude,
        forwardDragFraction = forwardDragFraction,
        backwardDragFraction = backwardDragFraction,
        waveCenterFollowsPointer = waveCenterFollowsPointer,
        waveCenterSpeed = waveCenterSpeed,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidWaveSpec) return false
        return restHorizontalRadius == other.restHorizontalRadius &&
            restVerticalRadius == other.restVerticalRadius &&
            restSideWidth == other.restSideWidth &&
            restWaveCenterFraction == other.restWaveCenterFraction &&
            maxHorizontalRadiusFraction == other.maxHorizontalRadiusFraction &&
            maxVerticalRadiusFraction == other.maxVerticalRadiusFraction &&
            backwardWaveAmplitude == other.backwardWaveAmplitude &&
            forwardDragFraction == other.forwardDragFraction &&
            backwardDragFraction == other.backwardDragFraction &&
            waveCenterFollowsPointer == other.waveCenterFollowsPointer &&
            waveCenterSpeed == other.waveCenterSpeed
    }

    override fun hashCode(): Int {
        var result = restHorizontalRadius.hashCode()
        result = 31 * result + restVerticalRadius.hashCode()
        result = 31 * result + restSideWidth.hashCode()
        result = 31 * result + restWaveCenterFraction.hashCode()
        result = 31 * result + maxHorizontalRadiusFraction.hashCode()
        result = 31 * result + maxVerticalRadiusFraction.hashCode()
        result = 31 * result + backwardWaveAmplitude.hashCode()
        result = 31 * result + forwardDragFraction.hashCode()
        result = 31 * result + backwardDragFraction.hashCode()
        result = 31 * result + waveCenterFollowsPointer.hashCode()
        result = 31 * result + waveCenterSpeed.hashCode()
        return result
    }

    override fun toString(): String = "LiquidWaveSpec(" +
        "restHorizontalRadius=$restHorizontalRadius, restVerticalRadius=$restVerticalRadius, " +
        "restSideWidth=$restSideWidth, restWaveCenterFraction=$restWaveCenterFraction, " +
        "maxHorizontalRadiusFraction=$maxHorizontalRadiusFraction, " +
        "maxVerticalRadiusFraction=$maxVerticalRadiusFraction, " +
        "backwardWaveAmplitude=$backwardWaveAmplitude, forwardDragFraction=$forwardDragFraction, " +
        "backwardDragFraction=$backwardDragFraction, " +
        "waveCenterFollowsPointer=$waveCenterFollowsPointer, waveCenterSpeed=$waveCenterSpeed)"

    companion object {
        /** Cuberto's original look: resting bulge with a "next" button and finger-following wave. */
        val Default: LiquidWaveSpec = LiquidWaveSpec()

        /**
         * No resting bulge, so the effect is only visible while swiping, like the Android
         * LiquidSwipe port. Dragging a full width reveals 75 % of the next page; the rest plays on
         * release.
         */
        val Plain: LiquidWaveSpec = LiquidWaveSpec(
            restHorizontalRadius = 0.dp,
            restSideWidth = 0.dp,
            restWaveCenterFraction = 0.5f,
            forwardDragFraction = 0.75f,
        )
    }
}
