package com.jade.ai_photography_director

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeGradeTest {
    @Test fun styleIntensityZeroPreservesNeutralAndFullStrengthLooksDiffer() {
        val neutral = CameraLook().matrix()
        Film.entries.forEach { film -> assertArrayEquals(neutral, CameraLook(film, intensity = 0).matrix(), 0f) }
        assertEquals(Film.entries.size, Film.entries.map { CameraLook(it).matrix().toList() }.toSet().size)
    }

    @Test fun matrixControlsHaveDistinctResponsesAndCompose() {
        val neutral = CameraLook()
        val controls = Adjustment.entries.filter { !it.advanced }
        val responses = controls.map { neutral.adjusted(it, 40).matrix().toList() }
        assertEquals(controls.size, responses.toSet().size)
        assertTrue(responses.none { it == neutral.matrix().toList() })
        val combined = controls.fold(neutral) { look, adjustment -> look.adjusted(adjustment, 40) }
        assertTrue(combined.matrix().toList() !in responses)
        assertEquals(neutral, controls.fold(combined) { look, control -> look.adjusted(control, 0) })
    }

    @Test fun highlightsAndShadowsPreferTheirOwnTonalRanges() {
        val shadows = CreativeGrade(CameraLook().adjusted(Adjustment.SHADOWS, 100))
        val highlights = CreativeGrade(CameraLook().adjusted(Adjustment.HIGHLIGHTS, -100))
        assertTrue(shadows.tone(.25f) > shadows.tone(.75f))
        assertTrue(highlights.tone(.75f) < highlights.tone(.25f))
        for (grade in listOf(shadows, highlights)) {
            assertEquals(0f, grade.tone(0f), 0f)
            assertEquals(0f, grade.tone(1f), 0f)
        }
    }

    @Test fun brillianceAndBlackPointRespondDifferently() {
        assertTrue(CreativeGrade(CameraLook().adjusted(Adjustment.BRILLIANCE, 40)).tone(.45f) > 0f)
        assertTrue(CreativeGrade(CameraLook().adjusted(Adjustment.BLACK_POINT, 40)).tone(.15f) < 0f)
    }

    @Test fun vibranceLimitsBoostForWarmHuesAndVignetteLeavesCenterAlone() {
        val grade = CreativeGrade(CameraLook().adjusted(Adjustment.VIBRANCE, 100).adjusted(Adjustment.VIGNETTE, 100))
        assertTrue(grade.colorWeight(.60f, .51f, .43f) < grade.colorWeight(.43f, .51f, .60f))
        assertEquals(1f, grade.edge(0f, 0f), 0f)
        assertTrue(grade.edge(.95f, .95f) < .6f)
    }

    @Test fun textureControlsDistinguishFineTextureFromLargerEdges() {
        fun grade(control: Adjustment) = CreativeGrade(CameraLook().adjusted(control, 70))
        assertTrue(grade(Adjustment.SHARPNESS).detail(.52f, .5f, .52f) > 0f)
        assertTrue(grade(Adjustment.DEFINITION).detail(.52f, .52f, .49f) > 0f)
        val smoothing = grade(Adjustment.NOISE_REDUCTION)
        assertTrue(smoothing.detail(.52f, .50f, .52f) < 0f)
        assertEquals(0f, smoothing.detail(.7f, .50f, .7f), .00001f)
    }
}
