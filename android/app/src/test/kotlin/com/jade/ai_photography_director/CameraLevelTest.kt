package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class CameraLevelTest {
    @Test
    fun levelRemapsGravityWithDisplayRotation() {
        assertEquals(0f, CameraLevel.roll(0f, 9.8f, 0)!!, .1f)
        assertEquals(0f, CameraLevel.roll(-9.8f, 0f, 1)!!, .1f)
        assertEquals(0f, CameraLevel.roll(0f, -9.8f, 2)!!, .1f)
        assertEquals(0f, CameraLevel.roll(9.8f, 0f, 3)!!, .1f)
        assertEquals(45f, CameraLevel.roll(6.9f, 6.9f, 0)!!, .1f)
    }

    @Test
    fun levelHidesWhenCameraPointsAlmostStraightUpOrDown() {
        assertNull(CameraLevel.roll(.2f, -.3f, 0))
    }
}
