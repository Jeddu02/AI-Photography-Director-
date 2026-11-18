package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class PreviewCalibrationTest {
    private fun frame(width: Int, height: Int, inPreview: (Int, Int) -> Boolean, chrome: Int = 18) =
        IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val shade = if (inPreview(x, y)) 50 + (x * 47 + y * 61 + x * y * 13) % 160 else chrome
            shade * 0x010101
        }

    @Test
    fun portraitFindsPreviewBetweenTopAndBottomControls() {
        val pixels = frame(64, 112, { _, y -> y in 12 until 79 })
        val bounds = PreviewCalibration.detect(pixels, 64, 112, .45f)!!
        assertEquals(0f, bounds.l, .001f)
        assertEquals(1f, bounds.r, .001f)
        assertEquals(12f / 112f, bounds.t, .035f)
        assertEquals(79f / 112f, bounds.b, .035f)
    }

    @Test
    fun rotatedPreviewExcludesSideControls() {
        val pixels = frame(112, 64, { x, _ -> x in 18 until 83 }, chrome = 225)
        val bounds = PreviewCalibration.detect(pixels, 112, 64, 2.2f)!!
        assertEquals(18f / 112f, bounds.l, .035f)
        assertEquals(83f / 112f, bounds.r, .035f)
        assertEquals(0f, bounds.t, .001f)
        assertEquals(1f, bounds.b, .001f)
    }

    @Test
    fun uniformDarkOrBlockedPreviewIsUncertain() {
        assertNull(PreviewCalibration.detect(IntArray(64 * 112), 64, 112, .45f))
    }

    @Test
    fun fullBleedPhotoDoesNotGetAnArtificialCrop() {
        val pixels = frame(64, 112, { _, _ -> true })
        assertEquals(Box(0f, 0f, 1f, 1f), PreviewCalibration.detect(pixels, 64, 112, .45f))
    }

    @Test
    fun layoutChangeAndUncertaintyDoNotReuseStaleBounds() {
        val state = PreviewCalibrationState()
        val portrait = Box(0f, .10f, 1f, .76f)
        val landscape = Box(.14f, 0f, .8f, 1f)
        assertNull(state.update(portrait))
        assertEquals(portrait, state.update(portrait))
        assertNull(state.update(null))
        assertNull(state.update(landscape))
        assertEquals(landscape, state.update(landscape))
    }
}
