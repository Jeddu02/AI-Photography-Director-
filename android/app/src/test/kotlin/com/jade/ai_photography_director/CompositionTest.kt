package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class CompositionTest {
    private fun f(
        x: Float = .5f,
        y: Float = .4f,
        w: Float = .3f,
        h: Float = .5f,
        confidence: Float = .9f,
        motion: Float = 0f,
    ) =
        Features(
            listOf(Subject(Box(x - w / 2, y - h / 2, x + w / 2, y + h / 2), confidence, "person")),
            .5f,
            0f,
            motion,
            null,
            0f,
            .1f,
            .5f,
        )

    @Test
    fun imageSpaceDirections() {
        val e = CompositionEngine()
        val left = e.decide(f(.7f), "Portrait", "Left third", .55f)
        assertTrue(left.dx < 0)
        assertTrue(left.text.contains("shifts left"))
        val right = e.decide(f(.3f), "Portrait", "Right third", .55f)
        assertTrue(right.dx > 0)
        assertTrue(right.text.contains("shifts right"))
    }

    @Test
    fun lowConfidenceNeverReady() {
        assertEquals(
            "search",
            CompositionEngine().decide(f(confidence = .4f), "Portrait", "Center", .6f).key,
        )
    }

    @Test
    fun darknessOverridesPlacement() {
        assertEquals(
            "dark",
            CompositionEngine().decide(f().copy(brightness = .03f), "Portrait", "Center", .55f).key,
        )
    }

    @Test
    fun movementBlocksReadiness() {
        assertEquals(
            "steady",
            CompositionEngine().decide(f(motion = .1f), "Portrait", "Center", .55f).key,
        )
    }

    @Test
    fun croppingBlocksReadiness() {
        assertEquals("crop", CompositionEngine().decide(f(.1f), "Portrait", "Center", .55f).key)
    }

    @Test
    fun readinessAppearsOnNextQualifyingObservationWithoutTimer() {
        val e = CompositionEngine()
        assertEquals("steady", e.evaluate(f(), "Portrait", "Center", .55f, 1000).key)
        val ready = e.evaluate(f(), "Portrait", "Center", .55f, 1100)
        assertEquals("ready", ready.key)
        assertEquals("Ready to capture", ready.text)
        assertEquals(
            "dark",
            e.evaluate(f().copy(brightness = .02f), "Portrait", "Center", .55f, 3100).key,
        )
    }

    @Test
    fun interruptedReadinessRequiresFreshObservations() {
        val e = CompositionEngine()
        e.evaluate(f(), "Portrait", "Center", .55f, 1000)
        e.evaluate(f(motion = .2f), "Portrait", "Center", .55f, 2400)
        assertNotEquals("ready", e.evaluate(f(), "Portrait", "Center", .55f, 2500).key)
    }

    @Test
    fun previewMapping() {
        val b = Coordinates.toDisplay(Box(.25f, .25f, .75f, .75f), Box(.1f, .2f, .9f, .8f))
        assertEquals(.3f, b.l, .0001f)
        assertEquals(.35f, b.t, .0001f)
        assertEquals(.7f, b.r, .0001f)
        assertEquals(.65f, b.b, .0001f)
    }

    @Test
    fun rotationRoundTrip() {
        val b = Box(.1f, .2f, .4f, .6f)
        val r = Coordinates.rotate(Coordinates.rotate(b, 1), 3)
        assertEquals(b.l, r.l, .0001f)
        assertEquals(b.b, r.b, .0001f)
    }

    @Test
    fun trackingRetainsIdAndSmooths() {
        val t = Tracker()
        val a = t.update(f().subjects).first()
        val b = t.update(f(.55f).subjects).first()
        assertEquals(a.id, b.id)
        assertTrue(b.box.x > .5f)
        assertTrue(b.box.x < .55f)
    }

    @Test
    fun groupIncludesEveryDetectedPerson() {
        val people = f(.3f, w = .2f).subjects + f(.92f, w = .2f).subjects
        assertEquals(
            "crop",
            CompositionEngine()
                .decide(f().copy(subjects = people), "Group Photo", "Center", .5f)
                .key,
        )
    }

    @Test
    fun manualAndLandscapeDoNotClaimReady() {
        val e = CompositionEngine()
        assertEquals("manual", e.decide(f(), "Manual Composition", "Center", .5f).key)
        assertEquals("landscape", e.decide(f(), "Landscape", "Center", .5f).key)
    }

    @Test
    fun closedEyeEstimateBlocksReady() {
        val a =
            f().copy(
                subjects =
                    f().subjects +
                        Subject(Box(.4f, .2f, .6f, .4f), .9f, "face", face = true, eyes = .1f)
            )
        assertEquals("eyes", CompositionEngine().decide(a, "Portrait", "Center", .5f).key)
    }

    @Test
    fun foodDoesNotUseUnrelatedObject() {
        assertEquals("search", CompositionEngine().decide(f(), "Food", "Center", .5f).key)
    }

    @Test
    fun matchingIsOneToOne() {
        val t = Tracker()
        t.update(f().subjects)
        val s = t.update(f(.49f).subjects + f(.51f).subjects)
        assertNotEquals(s[0].id, s[1].id)
    }

    @Test
    fun motionImmediatelyRevokesCaptureAdvice() {
        val e = CompositionEngine()
        for (t in listOf(1000L, 1700L, 2400L, 3000L)) e.evaluate(f(), "Portrait", "Center", .55f, t)
        assertEquals("steady", e.evaluate(f(motion = .2f), "Portrait", "Center", .55f, 3100).key)
    }

    @Test
    fun longGapStartsFreshReadinessStreak() {
        val e = CompositionEngine()
        for (t in listOf(1000L, 1700L, 2400L, 3000L)) e.evaluate(f(), "Portrait", "Center", .55f, t)
        assertNotEquals("ready", e.evaluate(f(), "Portrait", "Center", .55f, 7000).key)
    }
}
