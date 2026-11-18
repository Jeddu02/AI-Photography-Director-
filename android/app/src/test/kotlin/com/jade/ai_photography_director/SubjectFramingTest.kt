package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class SubjectFramingTest {
    private val person = Subject(Box(.42f, .30f, .58f, .70f), .95f, "person", 1)
    private val face = Subject(Box(.47f, .31f, .53f, .40f), .9f, "face", 2, face = true)
    private val focus = Focus(person.box, person.label, listOf(person))
    private val leftThird = Box(.30f, .20f, .90f, .80f)
    private val rightThird = Box(.10f, .20f, .70f, .80f)

    @Test
    fun explicitThirdsRankOppositeCropsHigher() {
        val left = SubjectFraming(focus, emptyList(), "Portrait", "Left third")
        val right = SubjectFraming(focus, emptyList(), "Portrait", "Right third")
        assertTrue(left.score(leftThird) > left.score(rightThird))
        assertTrue(right.score(rightThird) > right.score(leftThird))
    }

    @Test
    fun lookingRoomFollowsDetectedFaceDirection() {
        val right = SubjectFraming(focus, listOf(face.copy(yaw = 28f)), "Portrait", "Nearest third")
        val left = SubjectFraming(focus, listOf(face.copy(yaw = -28f)), "Portrait", "Nearest third")
        assertTrue(right.score(leftThird) > right.score(rightThird))
        assertTrue(left.score(rightThird) > left.score(leftThird))
        assertEquals("Leave room in the direction they face", right.reason(leftThird))
    }

    @Test
    fun headroomRewardsModerateClearanceOverExcessEmptySpace() {
        val framing = SubjectFraming(focus, listOf(face), "Portrait", "Center")
        val moderate = Box(.16f, .23f, .84f, .91f)
        val excess = Box(.16f, .05f, .84f, .73f)
        assertTrue(framing.score(moderate) > framing.score(excess))
    }

    @Test
    fun automaticGroupFramingPrefersBalanceAroundTheCenter() {
        val group = focus.copy(label = "group")
        val framing =
            SubjectFraming(group, listOf(face.copy(yaw = 30f)), "Auto Director", "Nearest third")
        assertEquals(.5f, framing.targetX, .001f)
        assertTrue(framing.score(Box(.20f, .20f, .80f, .80f)) > framing.score(leftThird))
    }

    @Test
    fun faceOutsideSelectedSubjectDoesNotChooseFacingSide() {
        val unrelated = face.copy(box = Box(.72f, .30f, .80f, .40f), yaw = -30f)
        val framing = SubjectFraming(focus, listOf(unrelated), "Portrait", "Nearest third")
        assertEquals(1f / 3f, framing.targetX, .001f)
    }

    @Test
    fun cropPlannerUsesTheChosenThirdAndPreservesTheFullPerson() {
        val channel = FloatArray(4096) { .5f }
        val features =
            Features(
                listOf(person),
                .5f,
                0f,
                0f,
                null,
                0f,
                0f,
                0f,
                SceneMap(64, channel, channel, channel),
            )
        for ((preference, expected) in listOf("Left third" to 1f / 3, "Right third" to 2f / 3)) {
            val shot = ShotPlanner().rank(features, "Portrait", preference, .55f).first()
            assertTrue(shot.frame.w < 1f)
            assertEquals(expected, (person.box.x - shot.frame.l) / shot.frame.w, .025f)
            assertTrue(CropProtection.accepts(shot.frame, focus, listOf(person)))
        }
    }
}
