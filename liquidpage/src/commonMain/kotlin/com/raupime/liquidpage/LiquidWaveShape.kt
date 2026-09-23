package com.raupime.liquidpage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A [Shape] shaped like the liquid wave mask, usable with `Modifier.clip` or `graphicsLayer` on
 * any composable, for example to build custom reveal transitions.
 *
 * Two flavours are available:
 * - Progress based: [LiquidWaveShape] `(progress, spec, ...)` resolves the geometry for the size it
 *   is applied to, following the same choreography as [LiquidPager].
 * - Explicit: [LiquidWaveShape] `(geometry)` uses already resolved pixel values.
 *
 * In right-to-left layouts the wave is mirrored to the left edge.
 */
@Immutable
class LiquidWaveShape private constructor(
    private val geometry: LiquidWaveGeometry?,
    private val progress: Float,
    private val spec: LiquidWaveSpec,
    private val waveCenterFraction: Float,
    private val backward: Boolean,
) : Shape {

    /**
     * @param progress Transition progress in `0..1`.
     * @param spec Wave geometry and choreography.
     * @param waveCenterFraction Vertical position of the wave center as a fraction of the height.
     * @param backward `false` for the forward wave (the page is swiped away), `true` for the wave
     * of a page being revealed from the previous position.
     */
    constructor(
        progress: Float,
        spec: LiquidWaveSpec = LiquidWaveSpec.Default,
        waveCenterFraction: Float = spec.restWaveCenterFraction,
        backward: Boolean = false,
    ) : this(
        geometry = null,
        progress = progress,
        spec = spec,
        waveCenterFraction = waveCenterFraction,
        backward = backward,
    )

    constructor(geometry: LiquidWaveGeometry) : this(
        geometry = geometry,
        progress = 0f,
        spec = LiquidWaveSpec.Default,
        waveCenterFraction = 0f,
        backward = false,
    )

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val resolved = geometry ?: run {
            val centerY = waveCenterFraction * size.height
            if (backward) {
                LiquidWaveGeometry.backward(progress, size, spec, density, centerY)
            } else {
                LiquidWaveGeometry.forward(progress, size, spec, density, centerY)
            }
        }
        val path = Path().apply {
            addLiquidWave(size, resolved, mirrored = layoutDirection == LayoutDirection.Rtl)
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidWaveShape) return false
        return geometry == other.geometry &&
            progress == other.progress &&
            spec == other.spec &&
            waveCenterFraction == other.waveCenterFraction &&
            backward == other.backward
    }

    override fun hashCode(): Int {
        var result = geometry.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + spec.hashCode()
        result = 31 * result + waveCenterFraction.hashCode()
        result = 31 * result + backward.hashCode()
        return result
    }

    override fun toString(): String = if (geometry != null) {
        "LiquidWaveShape(geometry=$geometry)"
    } else {
        "LiquidWaveShape(progress=$progress, backward=$backward, waveCenterFraction=$waveCenterFraction)"
    }
}
