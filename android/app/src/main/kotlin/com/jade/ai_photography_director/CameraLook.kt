package com.jade.ai_photography_director

import kotlin.math.pow
enum class Film(
    val label: String,
    val contrast: Float = 1f,
    val sat: Float = 1f,
    val warmth: Float = 0f,
    val lift: Float = 0f,
    val tint: Float = 0f,
) {
    STANDARD("Standard"), RICH("Rich Contrast", 1.18f), VIBRANT("Vibrant", 1.05f, 1.28f),
    WARM("Warm", warmth = 12f), COOL("Cool", warmth = -13f), NOIR("Film Noir", 1.3f, 0f),
    STARK("Stark B&W", 1.18f, 0f), LUMINOUS("Luminous", .93f, 1.03f, 2f, 10f),
    DRAMATIC("Dramatic", 1.24f, .83f, -4f), QUIET("Quiet", .92f, .75f),
    COZY("Cozy", .95f, .91f, 13f, 4f), ETHEREAL("Ethereal", .83f, .82f, 4f, 12f),
    MUTED("Muted B&W", .86f, 0f, 0f, 6f), AMBER("Amber", 1.04f, .92f, 18f),
    GOLD("Gold", 1.07f, 1.10f, 14f, 5f), ROSE("Rose Gold", .96f, .88f, 11f, 7f, -5f),
    MATINEE("Matinee", .93f, .84f, 8f, 3f), BLUE("Blue Hour", 1.09f, .86f, -16f),
    NEUTRAL("Neutral", .98f, .90f), COOL_ROSE("Cool Rose", .94f, .91f, -9f, 4f, -5f),
}

enum class Lighting(val label: String, val contrast: Float, val lift: Float, val mono: Boolean = false) {
    NATURAL("Natural Light", 1f, 0f), STUDIO("Studio Light", .88f, 14f),
    CONTOUR("Contour Light", 1.16f, 0f), STAGE("Stage Light", 1.23f, -10f),
    STAGE_MONO("Stage Light Mono", 1.23f, -10f, true), HIGH_KEY("High-Key Light Mono", .82f, 28f, true),
}

internal enum class Adjustment(val label: String, val minimum: Int = -100, val advanced: Boolean = true) {
    EXPOSURE("Rendered exposure", advanced = false),
    BRILLIANCE("Brilliance"), HIGHLIGHTS("Highlights"), SHADOWS("Shadows"),
    CONTRAST("Contrast", advanced = false), BRIGHTNESS("Brightness", advanced = false),
    BLACK_POINT("Black Point"), SATURATION("Saturation", advanced = false), VIBRANCE("Vibrance"),
    WARMTH("Warmth", advanced = false), TINT("Tint", advanced = false),
    SHARPNESS("Sharpness", 0), DEFINITION("Definition", 0),
    NOISE_REDUCTION("Noise Reduction", 0), VIGNETTE("Vignette"),
}

internal data class CameraLook(
    val film: Film = Film.STANDARD,
    val light: Lighting = Lighting.NATURAL,
    val saturation: Int = 100,
    val tone: Int = 100,
    val warmth: Int = 0,
    val intensity: Int = 100,
    val adjustments: Map<Adjustment, Int> = emptyMap(),
    val blur: Int = 0,
) {
    fun value(control: Adjustment) = (adjustments[control] ?: 0).coerceIn(control.minimum, 100)

    fun adjusted(control: Adjustment, value: Int): CameraLook {
        val bounded = value.coerceIn(control.minimum, 100)
        return copy(adjustments = if (bounded == 0) adjustments - control else adjustments + (control to bounded))
    }

    fun matrix(): FloatArray {
        val weights = floatArrayOf(.2126f, .7152f, .0722f)
        val strength = intensity.coerceIn(0, 100) / 100f
        val s = if (light.mono) 0f else
            (1f + (film.sat - 1f) * strength) * saturation.coerceIn(0, 200) / 100f *
                (1f + .009f * value(Adjustment.SATURATION))
        val contrast = (1f + (film.contrast - 1f) * strength) * light.contrast *
            tone.coerceIn(50, 150) / 100f * (1f + .005f * value(Adjustment.CONTRAST))
        val exposure = 2f.pow(.015f * value(Adjustment.EXPOSURE))
        val w = if (s == 0f) 0f else film.warmth * strength + warmth.coerceIn(-30, 30) + .24f * value(Adjustment.WARMTH)
        val tint = if (s == 0f) 0f else film.tint * strength - .17f * value(Adjustment.TINT)
        val offsets = floatArrayOf(w - tint * .35f, w * .1f + tint, -w - tint * .35f)
        return FloatArray(20).apply {
            for (row in 0..2) {
                for (col in 0..2) {
                    this[row * 5 + col] = exposure * contrast * ((1f - s) * weights[col] + if (row == col) s else 0f)
                }
                this[row * 5 + 4] = 128f * (1f - contrast) + film.lift * strength +
                    light.lift + offsets[row] + .44f * value(Adjustment.BRIGHTNESS)
            }
            this[18] = 1f
        }
    }
}
