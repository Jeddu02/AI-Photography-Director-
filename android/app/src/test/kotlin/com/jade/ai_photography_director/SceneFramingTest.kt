package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class SceneFramingTest {
    @Test
    fun darkOverhangFavorsLowerCropWithAFullFrameAlternative() {
        val pixels =
            IntArray(64 * 64) { i ->
                val x = i % 64
                val y = i / 64
                val value = if (y < 19 || (x < 9 && y < 38)) 11 else 102 + (13 * x + 7 * y) % 83
                value * 0x010101
            }
        val f =
            Features(emptyList(), .45f, 0f, 0f, null, 0f, 0f, 0f, SceneMap.fromPixels(pixels, 64))
        val frames = ShotPlanner().rank(f, "Landscape", "Nearest third", .55f)
        assertTrue(frames.first().frame.t > .12f)
        assertTrue(frames.any { it.frame == Box(0f, 0f, 1f, 1f) })
    }
}
