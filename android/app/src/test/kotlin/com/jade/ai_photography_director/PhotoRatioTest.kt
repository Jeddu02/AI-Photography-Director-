package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class PhotoRatioTest {
    private fun scene(vararg subjects: Subject) =
        Features(subjects.toList(), .5f, 0f, 0f, null, 0f, 0f, 0f)

    @Test
    fun viewfinderFitsExactRatiosInPortraitAndLandscape() {
        for (ratio in PhotoRatio.entries) {
            for (portrait in listOf(true, false)) {
                val (w, h) = ratio.fit(761, 547, portrait)
                assertTrue(w <= 761 && h <= 547 && w > 0 && h > 0)
                assertEquals(w * ratio.height(portrait), h * ratio.width(portrait))
            }
        }
        assertEquals("9:16", PhotoRatio.WIDE.label(true))
    }

    @Test
    fun subjectAwareSuggestionsSupportFoodAndLandscape() {
        val food = Subject(Box(.25f, .3f, .75f, .7f), .9f, "pizza")
        assertEquals(
            PhotoRatio.SQUARE,
            suggestPhotoRatio(scene(food), PhotoRatio.CLASSIC, true, "Auto Director", .55f)?.ratio,
        )
        assertEquals(
            PhotoRatio.WIDE,
            suggestPhotoRatio(scene(), PhotoRatio.CLASSIC, false, "Landscape", .55f)?.ratio,
        )
    }

    @Test
    fun guidanceAvoidsRatiosThatMayCutOffTheSubject() {
        val food = Subject(Box(.25f, .03f, .75f, .7f), .9f, "pizza")
        assertNull(suggestPhotoRatio(scene(food), PhotoRatio.CLASSIC, true, "Auto Director", .55f))
        val face = Subject(Box(.03f, .3f, .35f, .7f), .9f, "face", face = true)
        assertNull(suggestPhotoRatio(scene(face), PhotoRatio.SQUARE, true, "Auto Director", .55f))
    }

    @Test
    fun currentRatioDoesNotGetRecommendedAgain() {
        val subject = Subject(Box(.25f, .2f, .75f, .8f), .9f, "person")
        assertNull(
            suggestPhotoRatio(scene(subject), PhotoRatio.CLASSIC, true, "Auto Director", .55f)
        )
    }
}
