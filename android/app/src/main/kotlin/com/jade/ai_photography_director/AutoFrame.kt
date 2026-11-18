package com.jade.ai_photography_director

import kotlin.math.abs

/** Camera zoom keeps the optical center fixed. Check the proposed crop and subject first. */
internal fun suggestedZoomRatio(advice: Advice, current: Float, minimum: Float, maximum: Float): Float? {
    val shot = advice.frame ?: return null
    val subject = advice.focusBounds ?: return null
    if (advice.confidence < .55f || abs(shot.x - .5f) > .055f || abs(shot.y - .5f) > .055f) return null
    val tighten = advice.key == "zoom_in" && advice.zoom > 1.13f
    val widen = advice.key == "zoom_out" && advice.zoom < .89f
    if (!tighten && !widen) return null
    val target = (current * advice.zoom).coerceIn(minimum, maximum)
    val scale = target / current
    if (abs(scale - 1f) < .06f) return null
    val edge = .46f / scale
    if (subject.l < .5f - edge || subject.r > .5f + edge ||
        subject.t < .5f - edge || subject.b > .5f + edge) return null
    return target
}
