package com.jade.ai_photography_director

import kotlin.math.max
import kotlin.math.min

internal enum class PhotoRatio(private val longSide: Int, private val shortSide: Int) {
    CLASSIC(4, 3),
    WIDE(16, 9),
    SQUARE(1, 1),
    THREE_TWO(3, 2);

    fun width(portrait: Boolean) = if (portrait) shortSide else longSide

    fun height(portrait: Boolean) = if (portrait) longSide else shortSide

    fun label(portrait: Boolean) = "${width(portrait)}:${height(portrait)}"

    fun fit(availableWidth: Int, availableHeight: Int, portrait: Boolean): Pair<Int, Int> {
        val units = min(availableWidth / width(portrait), availableHeight / height(portrait))
        return width(portrait) * units to height(portrait) * units
    }

    internal fun visibleWithin(current: PhotoRatio, portrait: Boolean): Box {
        val before = current.width(portrait).toFloat() / current.height(portrait)
        val after = width(portrait).toFloat() / height(portrait)
        val horizontal = min(1f, after / before)
        val vertical = min(1f, before / after)
        return Box(
            (1 - horizontal) / 2,
            (1 - vertical) / 2,
            (1 + horizontal) / 2,
            (1 + vertical) / 2,
        )
    }
}

internal data class RatioSuggestion(val ratio: PhotoRatio, val reason: String)

internal fun suggestPhotoRatio(
    features: Features,
    current: PhotoRatio,
    portrait: Boolean,
    mode: String,
    threshold: Float,
): RatioSuggestion? {
    val subjects = features.subjects.filter { !it.face && it.confidence >= threshold }
    val people = subjects.filter { it.label == "person" }
    val suggestion =
        when {
            !portrait && (people.size > 1 || mode == "Landscape") ->
                RatioSuggestion(PhotoRatio.WIDE, "More room across the frame")
            mode == "Food" || subjects.any { it.label in CompositionEngine.foodLabels } ->
                RatioSuggestion(PhotoRatio.SQUARE, "Tighter framing for food")
            people.size == 1 || features.subjects.any { it.face && it.confidence >= threshold } ->
                RatioSuggestion(PhotoRatio.CLASSIC, "More breathing room for the person")
            else -> return null
        }
    if (current == suggestion.ratio) return null
    val crop = suggestion.ratio.visibleWithin(current, portrait)
    val protected =
        when {
            people.isNotEmpty() -> people
            suggestion.ratio == PhotoRatio.CLASSIC ->
                features.subjects.filter { it.face && it.confidence >= threshold }
            else -> subjects
        }
    val margin = .03f
    if (
        protected.any {
            it.box.l < max(0f, crop.l + margin) ||
                it.box.r > min(1f, crop.r - margin) ||
                it.box.t < max(0f, crop.t + margin) ||
                it.box.b > min(1f, crop.b - margin)
        }
    )
        return null
    return suggestion
}
