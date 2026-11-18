package com.jade.ai_photography_director

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal object CameraLevel {
    /** Gravity in device coordinates, mapped to the current display. */
    fun roll(x: Float, y: Float, displayRotation: Int): Float? {
        val (horizontal, vertical) =
            when (displayRotation) {
                1 -> y to -x
                2 -> -x to -y
                3 -> -y to x
                else -> x to y
            }
        if (hypot(horizontal, vertical) < 3f) return null
        return Math.toDegrees(atan2(horizontal, abs(vertical)).toDouble()).toFloat()
    }
}
