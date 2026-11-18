package com.jade.ai_photography_director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFrameTest {
    private val centeredCup = Advice(
        key = "zoom_in",
        text = "Tighten the shot",
        frame = Box(.25f, .25f, .75f, .75f),
        zoom = 2f,
        focusLabel = "cup",
        focusBounds = Box(.40f, .34f, .61f, .66f),
        confidence = .78f,
    )

    @Test fun tightensAroundDetectedSubject() {
        assertEquals(2f, suggestedZoomRatio(centeredCup, 1f, .5f, 4f)!!, .001f)
        assertEquals(1.5f, suggestedZoomRatio(centeredCup, 1f, .5f, 1.5f)!!, .001f)
    }

    @Test fun leavesAimAndUncertainSubjectsInManualControl() {
        assertNull(suggestedZoomRatio(centeredCup.copy(key = "aim"), 1f, .5f, 4f))
        assertNull(suggestedZoomRatio(centeredCup.copy(confidence = .45f), 1f, .5f, 4f))
        assertNull(suggestedZoomRatio(centeredCup.copy(focusBounds = null), 1f, .5f, 4f))
        assertNull(suggestedZoomRatio(centeredCup.copy(frame = Box(.40f, .25f, .90f, .75f)), 1f, .5f, 4f))
    }

    @Test fun rejectsZoomIfEdgesWouldCutTheSubject() {
        val crowded = centeredCup.copy(focusBounds = Box(.20f, .30f, .66f, .78f))
        assertNull(suggestedZoomRatio(crowded, 1f, .5f, 4f))
        assertNull(suggestedZoomRatio(centeredCup, 4f, .5f, 4f))
    }

    @Test fun widensAroundCenteredSubjectWhenRequested() {
        val wide = centeredCup.copy(
            key = "zoom_out", frame = Box(-.1f, -.1f, 1.1f, 1.1f), zoom = .83f,
        )
        assertEquals(1.66f, suggestedZoomRatio(wide, 2f, .5f, 4f)!!, .001f)
    }
}
