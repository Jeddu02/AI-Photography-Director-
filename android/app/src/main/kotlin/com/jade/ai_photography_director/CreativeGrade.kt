package com.jade.ai_photography_director

import kotlin.math.roundToInt

internal data class CreativeGrade(val look: CameraLook) {
    val brilliance = amount(Adjustment.BRILLIANCE)
    val highlights = amount(Adjustment.HIGHLIGHTS)
    val shadows = amount(Adjustment.SHADOWS)
    val black = amount(Adjustment.BLACK_POINT)
    val vibrance = amount(Adjustment.VIBRANCE)
    val sharpness = amount(Adjustment.SHARPNESS)
    val definition = amount(Adjustment.DEFINITION)
    val denoise = amount(Adjustment.NOISE_REDUCTION)
    val vignette = amount(Adjustment.VIGNETTE)
    val blur = look.blur.coerceIn(0, 100) / 100f
    val hasSpatial = sharpness != 0f || definition != 0f || denoise != 0f || blur > 0f

    private fun amount(control: Adjustment) = look.value(control) / 100f

    fun tone(y: Float): Float {
        val clamped = y.coerceIn(0f, 1f)
        val dark = 1f - clamped
        return clamped * dark * (
            .55f * shadows * dark * dark + .55f * highlights * clamped * clamped +
                .24f * brilliance * dark - .55f * black * dark * dark * dark
        )
    }

    fun colorWeight(r: Float, g: Float, b: Float): Float {
        val spread = (maxOf(r, g, b) - minOf(r, g, b)).coerceIn(0f, 1f)
        // Reduce color boosts in warm hues with ordering typical of skin. No identity inference.
        val warmHue = r >= g && g >= b && r - g <= 1.8f * (g - b)
        return 1f + .55f * vibrance * (1f - spread) * if (warmHue) .25f else 1f
    }

    fun edge(x: Float, y: Float): Float {
        val distance = ((x * x + y * y - .22f) / 1.50f).coerceIn(0f, 1f)
        return 1f - .58f * vignette * distance * distance
    }

    fun detail(center: Float, near: Float, wide: Float): Float {
        val fine = center - near
        val smoothing = (1f - 12f * kotlin.math.abs(fine)).coerceIn(0f, 1f)
        return (.80f * sharpness - .85f * denoise * smoothing) * fine + .48f * definition * (center - wide)
    }

    fun pack(r: Float, g: Float, b: Float): Int {
        fun channel(value: Float) = (255f * value.coerceIn(0f, 1f)).roundToInt()
        return (255 shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
