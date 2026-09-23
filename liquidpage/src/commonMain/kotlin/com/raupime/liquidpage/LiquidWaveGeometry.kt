package com.raupime.liquidpage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density

/**
 * Resolved (pixel) parameters of a liquid wave mask for a given viewport.
 *
 * The mask reveals the page from its leading edge up to `width - sideWidth`, with a wave centered
 * at [centerY] carved into it. Positive [horizontalRadius] values carve the wave *into* the page
 * (the page behind bulges through); negative values make the page bulge outwards instead.
 *
 * @param sideWidth Width of the strip hidden along the trailing edge.
 * @param horizontalRadius Horizontal radius of the wave.
 * @param verticalRadius Vertical radius of the wave.
 * @param centerY Vertical center of the wave.
 */
@Immutable
class LiquidWaveGeometry(
    val sideWidth: Float,
    val horizontalRadius: Float,
    val verticalRadius: Float,
    val centerY: Float,
) {
    /** X coordinate of the wave tip, measured from the leading edge of a viewport of [width]. */
    fun tipX(width: Float): Float = width - sideWidth - horizontalRadius

    /** `true` when the mask hides nothing, so clipping can be skipped altogether. */
    val isFullyRevealed: Boolean get() = sideWidth <= 0f && horizontalRadius <= 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidWaveGeometry) return false
        return sideWidth == other.sideWidth &&
            horizontalRadius == other.horizontalRadius &&
            verticalRadius == other.verticalRadius &&
            centerY == other.centerY
    }

    override fun hashCode(): Int {
        var result = sideWidth.hashCode()
        result = 31 * result + horizontalRadius.hashCode()
        result = 31 * result + verticalRadius.hashCode()
        result = 31 * result + centerY.hashCode()
        return result
    }

    override fun toString(): String =
        "LiquidWaveGeometry(sideWidth=$sideWidth, horizontalRadius=$horizontalRadius, " +
            "verticalRadius=$verticalRadius, centerY=$centerY)"

    companion object {
        /**
         * Geometry of the current page while it is swiped forward (the next page bulges through).
         *
         * @param progress Transition progress in `0..1`.
         * @param restAmount How much of the resting bulge is currently grown (`0..1`), so a swipe
         * that starts while the bulge is still appearing stays continuous.
         */
        fun forward(
            progress: Float,
            size: Size,
            spec: LiquidWaveSpec,
            density: Density,
            centerY: Float = spec.restWaveCenterFraction * size.height,
            restAmount: Float = 1f,
        ): LiquidWaveGeometry = with(density) {
            val initialHorizontal = spec.restHorizontalRadius.toPx() * restAmount
            val initialSide = spec.restSideWidth.toPx() * restAmount
            LiquidWaveGeometry(
                sideWidth = LiquidWaveMath.sideWidth(progress, initialSide, size.width),
                horizontalRadius = LiquidWaveMath.forwardHorizontalRadius(
                    progress = progress,
                    initial = initialHorizontal,
                    max = size.width * spec.maxHorizontalRadiusFraction,
                ),
                verticalRadius = LiquidWaveMath.verticalRadius(
                    progress = progress,
                    initial = spec.restVerticalRadius.toPx(),
                    max = size.height * spec.maxVerticalRadiusFraction,
                ),
                centerY = centerY,
            )
        }

        /**
         * Geometry of the previous page while it is being revealed on top of the current one.
         *
         * @param progress Reveal progress in `0..1`: `0` fully hidden, `1` fully shown (with the
         * resting bulge, so the page can become the current page without any jump).
         * @param restAmount How much of the resting bulge the page ends with (`0..1`).
         */
        fun backward(
            progress: Float,
            size: Size,
            spec: LiquidWaveSpec,
            density: Density,
            centerY: Float = spec.restWaveCenterFraction * size.height,
            restAmount: Float = 1f,
        ): LiquidWaveGeometry = with(density) {
            val hidden = 1f - progress
            LiquidWaveGeometry(
                sideWidth = LiquidWaveMath.sideWidth(hidden, spec.restSideWidth.toPx() * restAmount, size.width),
                horizontalRadius = LiquidWaveMath.backwardHorizontalRadius(
                    hidden = hidden,
                    rest = spec.restHorizontalRadius.toPx() * restAmount,
                    amplitude = spec.backwardWaveAmplitude.toPx(),
                ),
                verticalRadius = LiquidWaveMath.verticalRadius(
                    progress = hidden,
                    initial = spec.restVerticalRadius.toPx(),
                    max = size.height * spec.maxVerticalRadiusFraction,
                ),
                centerY = centerY,
            )
        }

        /** Geometry of the resting bulge, scaled by [restAmount] (`0..1`). */
        fun rest(
            size: Size,
            spec: LiquidWaveSpec,
            density: Density,
            centerY: Float = spec.restWaveCenterFraction * size.height,
            restAmount: Float = 1f,
        ): LiquidWaveGeometry = with(density) {
            LiquidWaveGeometry(
                sideWidth = spec.restSideWidth.toPx() * restAmount,
                horizontalRadius = spec.restHorizontalRadius.toPx() * restAmount,
                verticalRadius = spec.restVerticalRadius.toPx(),
                centerY = centerY,
            )
        }
    }
}

/**
 * Appends the liquid wave mask described by [geometry] for a viewport of [size].
 * When [mirrored] is `true` the wave is drawn on the left edge instead (right-to-left layouts).
 */
fun Path.addLiquidWave(size: Size, geometry: LiquidWaveGeometry, mirrored: Boolean = false) {
    val width = size.width
    val height = size.height
    val maskWidth = width - geometry.sideWidth
    val hr = geometry.horizontalRadius
    val vr = geometry.verticalRadius
    val curveStartY = geometry.centerY + vr

    fun x(value: Float): Float = if (mirrored) width - value else value
    fun curve(
        c1x: Double, c1y: Double,
        c2x: Double, c2y: Double,
        x2: Double, y2: Double,
    ) {
        cubicTo(
            x((maskWidth - hr * c1x).toFloat()), (curveStartY - vr * c1y).toFloat(),
            x((maskWidth - hr * c2x).toFloat()), (curveStartY - vr * c2y).toFloat(),
            x((maskWidth - hr * x2).toFloat()), (curveStartY - vr * y2).toFloat(),
        )
    }

    moveTo(x(0f), 0f)
    lineTo(x(0f), height)
    lineTo(x(maskWidth), height)
    lineTo(x(maskWidth), curveStartY)

    // Lower half of the wave, from the trailing edge to the tip.
    curve(0.0, 0.1346194756, 0.05341339583, 0.2412779634, 0.1561501458, 0.3322374268)
    curve(0.2361659167, 0.4030805244, 0.3305285625, 0.4561193293, 0.5012484792, 0.5350576951)
    curve(0.515878125, 0.5418222317, 0.5664134792, 0.5650349878, 0.574934875, 0.5689655122)
    curve(0.7283715208, 0.6397387195, 0.8086618958, 0.6833456585, 0.8774032292, 0.7399037439)
    curve(0.9653464583, 0.8122605122, 1.0, 0.8936183659, 1.0, 1.0)
    // Upper half of the wave, from the tip back to the trailing edge.
    curve(1.0, 1.100142878, 0.9595746667, 1.1887991951, 0.8608411667, 1.270484439)
    curve(0.7852123333, 1.3330544756, 0.703382125, 1.3795848049, 0.5291125625, 1.4665102805)
    curve(0.5241858333, 1.4689677195, 0.505739125, 1.4781625854, 0.5015305417, 1.4802616098)
    curve(0.3187486042, 1.5714239024, 0.2332057083, 1.6204116463, 0.1541165417, 1.687403)
    curve(0.0509933125, 1.774752061, 0.0, 1.8709256829, 0.0, 2.0)

    lineTo(x(maskWidth), 0f)
    close()
}
