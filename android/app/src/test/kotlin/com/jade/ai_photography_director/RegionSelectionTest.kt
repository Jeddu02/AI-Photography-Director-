package com.jade.ai_photography_director

import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class RegionSelectionTest {
    @Test
    fun tapAndReverseDragCreateBoundedTargets() {
        val tap = targetBounds(0f to 1f, 0f to 1f)
        assertEquals(.11f, tap.w, .001f)
        assertEquals(.11f, tap.h, .001f)
        assertTrue(tap.l >= 0f && tap.b <= 1f)
        assertEquals(Box(.2f, .3f, .6f, .8f), targetBounds(.6f to .8f, .2f to .3f))
    }

    @Test
    fun regionWorksWithoutDetectionAndLossDoesNotSilentlyRetarget() {
        val shape = FloatArray(4096) { .5f + .3f * sin(it % 64 * .21f) * sin(it / 64 * .17f) }
        val flat = FloatArray(4096) { .5f }
        fun features(channel: FloatArray) =
            Features(
                emptyList(),
                .5f,
                0f,
                0f,
                null,
                0f,
                0f,
                0f,
                SceneMap(64, channel, channel, channel),
            )
        val director = ShotDirector()
        director.choose(SubjectChoice.Region(Box(.38f, .31f, .62f, .69f)))
        val first = director.evaluate(features(shape), "Auto Director", "Center", .55f)
        assertEquals("selected area", first.focusLabel)
        assertNotNull(first.frame)
        assertEquals(
            "subject_lost",
            director.evaluate(features(flat), "Auto Director", "Center", .55f).key,
        )
        assertEquals(
            "subject_lost",
            director.evaluate(features(shape), "Auto Director", "Center", .55f).key,
        )
    }

    @Test
    fun cropCanExcludeBrightBackgroundAndKeepAWideAlternative() {
        val pixels = FloatArray(4096) { if (it % 64 >= 49) 1f else .43f }
        val subject = Subject(Box(.27f, .33f, .53f, .66f), .95f, "cat", 1)
        val f =
            Features(
                listOf(subject),
                .5f,
                0f,
                0f,
                null,
                0f,
                0f,
                0f,
                SceneMap(64, pixels, pixels, pixels),
            )
        val proposals = ShotPlanner().rank(f, "Auto Director", "Center", .55f)
        assertTrue(proposals.first().frame.r < .8f)
        assertTrue(proposals.any { it.frame == Box(0f, 0f, 1f, 1f) })
        assertTrue(
            proposals.all {
                overlap(it.frame, subject.box) >= .995f * subject.box.w * subject.box.h
            }
        )
    }
}
