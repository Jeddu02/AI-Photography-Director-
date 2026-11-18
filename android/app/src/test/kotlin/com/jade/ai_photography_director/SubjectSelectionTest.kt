package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class SubjectSelectionTest {
    private val bottle = Subject(Box(.35f, .3f, .55f, .7f), .95f, "bottle", 4)
    private val cat = Subject(Box(.65f, .3f, .9f, .7f), .9f, "cat", 7)
    private val person = Subject(Box(.08f, .25f, .28f, .75f), .9f, "person", 9)

    private fun map() =
        SceneMap(64, FloatArray(4096) { .45f }, FloatArray(4096) { .5f }, FloatArray(4096) { .55f })

    private fun frame(subjects: List<Subject>) =
        Features(subjects, .5f, 0f, 0f, null, 0f, .1f, .5f, map())

    @Test
    fun choiceOverridesAutomaticPersonPriority() {
        val f = frame(listOf(person, bottle, cat))
        val planner = ShotPlanner()
        assertEquals("person", planner.focus(f, "Auto Director", .55f)!!.label)
        assertEquals(
            "bottle",
            planner.focus(f, "Auto Director", .55f, SubjectChoice.Detected(4, "bottle"))!!.label,
        )
    }

    @Test
    fun explicitSubjectOverridesModeWithoutInventingAClass() {
        assertEquals(
            "cat",
            ShotPlanner()
                .focus(frame(listOf(cat)), "Landscape", .55f, SubjectChoice.Detected(7, "cat"))!!
                .label,
        )
    }

    @Test
    fun wholeSceneOffersOnlyWideFraming() {
        val plans =
            ShotPlanner()
                .rank(
                    frame(listOf(person, bottle)),
                    "Portrait",
                    "Center",
                    .55f,
                    SubjectChoice.WholeScene,
                )
        assertEquals(1, plans.size)
        assertEquals(Box(0f, 0f, 1f, 1f), plans.single().frame)
        assertEquals("Keep this wider view", plans.single().reason)
    }

    @Test
    fun explicitLockNeverFallsBackToDifferentInstance() {
        val director = ShotDirector()
        director.choose(SubjectChoice.Detected(4, "bottle"))
        assertNotEquals(
            "subject_lost",
            director.evaluate(frame(listOf(bottle)), "Auto Director", "Center", .55f).key,
        )
        val other = bottle.copy(id = 5)
        val lost = director.evaluate(frame(listOf(other)), "Auto Director", "Center", .55f)
        assertEquals("subject_lost", lost.key)
        assertNull(lost.frame)
        assertEquals(2, lost.level)
    }

    @Test
    fun lostSelectionCannotBecomeReadyThroughHysteresis() {
        val engine = CompositionEngine()
        engine.selectSubject(SubjectChoice.Detected(4, "bottle"))
        for (t in listOf(1000L, 1700L, 2400L, 3100L)) engine.evaluate(
            frame(listOf(bottle)),
            "Auto Director",
            "Center",
            .55f,
            t,
        )
        val lost = engine.evaluate(frame(listOf(cat)), "Auto Director", "Center", .55f, 3800)
        assertEquals("subject_lost", lost.key)
        assertNotEquals(0, lost.level)
    }

    @Test
    fun sceneChoiceSurvivesObservationGapButClearResetsIt() {
        val engine = CompositionEngine()
        engine.selectSubject(SubjectChoice.WholeScene)
        engine.evaluate(frame(listOf(person)), "Portrait", "Center", .55f, 1000)
        assertEquals(
            1f,
            engine.evaluate(frame(listOf(person)), "Portrait", "Center", .55f, 5000).frame!!.w,
            .001f,
        )
        engine.reset(clearSelection = true)
        assertNotEquals(
            1f,
            engine.evaluate(frame(listOf(person)), "Portrait", "Center", .55f, 5700).frame!!.w,
            .001f,
        )
    }

    @Test
    fun candidateMenuSuppressesDuplicateFaceAndUncertainDetections() {
        val face = Subject(Box(.1f, .27f, .2f, .35f), .9f, "face", 11, true)
        val uncertain = cat.copy(confidence = .4f)
        val options = SubjectMenu.candidates(listOf(person, face, bottle, uncertain), .55f)
        assertEquals(listOf("person", "bottle"), options.map { it.subject.label })
        assertTrue(options.first().title.contains("left"))
    }

    @Test
    fun candidateMenuIsBoundedAndDistinguishesSameClassByPosition() {
        val options =
            SubjectMenu.candidates(
                (1..8).map {
                    bottle.copy(id = it, box = Box(it * .1f - .08f, .2f, it * .1f + .02f, .5f))
                },
                .55f,
            )
        assertEquals(4, options.size)
        assertEquals(4, options.map { it.subject.id }.distinct().size)
    }

    @Test
    fun availabilityChecksBothIdAndClassAndConfidence() {
        assertTrue(
            SubjectMenu.isAvailable(SubjectChoice.Detected(4, "bottle"), listOf(bottle), .55f)
        )
        assertFalse(SubjectMenu.isAvailable(SubjectChoice.Detected(4, "cat"), listOf(bottle), .55f))
        assertFalse(
            SubjectMenu.isAvailable(
                SubjectChoice.Detected(4, "bottle"),
                listOf(bottle.copy(confidence = .3f)),
                .55f,
            )
        )
    }

    @Test
    fun cropMustIncludeSelectedSubjectWithBreathingRoom() {
        val focus = Focus(person.box, "person", listOf(person))
        assertFalse(CropProtection.accepts(Box(.08f, .2f, .8f, .9f), focus, listOf(person)))
        assertTrue(CropProtection.accepts(Box(0f, .15f, .8f, .95f), focus, listOf(person)))
        assertFalse(CropProtection.accepts(Box(.12f, .2f, .8f, .88f), focus, listOf(person)))
    }

    @Test
    fun cropCannotCutAnUnselectedPerson() {
        val focus = Focus(bottle.box, "bottle", listOf(bottle))
        assertFalse(
            CropProtection.accepts(Box(.18f, .18f, .98f, .98f), focus, listOf(person, bottle))
        )
        assertTrue(
            CropProtection.accepts(Box(.30f, .20f, .95f, .85f), focus, listOf(person, bottle))
        )
    }

    @Test
    fun boxOverlapWarningDoesNotMistakeAssociatedFaceForDistraction() {
        val focus = Focus(person.box, "person", listOf(person))
        val face = Subject(Box(.1f, .27f, .2f, .35f), .9f, "face", 11, true)
        assertFalse(CropProtection.competingOverlap(focus, listOf(person, face)))
        assertTrue(
            CropProtection.competingOverlap(
                Focus(bottle.box, "bottle", listOf(bottle)),
                listOf(bottle, cat.copy(box = Box(.42f, .3f, .7f, .7f))),
            )
        )
    }

    @Test
    fun noOverlapDoesNotInventASeparationInstruction() {
        val director = ShotDirector()
        director.choose(SubjectChoice.Detected(4, "bottle"))
        assertNotEquals(
            "separate",
            director.evaluate(frame(listOf(bottle, cat)), "Auto Director", "Center", .55f).key,
        )
    }

    @Test
    fun nearBottomPersonGetsWidenWarningBeforeCapture() {
        val low = person.copy(box = Box(.35f, .5f, .65f, .98f))
        val a = ShotDirector().evaluate(frame(listOf(low)), "Portrait", "Center", .55f)
        assertEquals("uncrop", a.key)
        assertEquals(2, a.level)
    }

    @Test
    fun uniformSceneDoesNotInventAnAutomaticZoom() {
        assertTrue(ShotPlanner().rank(frame(emptyList()), "Landscape", "Center", .55f).isEmpty())
    }

    @Test
    fun explicitWholeSceneCanBeFramedEvenWithoutTexture() {
        val director = ShotDirector()
        director.choose(SubjectChoice.WholeScene)
        assertEquals(
            1f,
            director.evaluate(frame(emptyList()), "Landscape", "Center", .55f).zoom,
            .001f,
        )
    }

    @Test
    fun interpolationIgnoresTinyJitterButFollowsMeaningfulChanges() {
        val smooth = SmoothFrame()
        val initial = Box(.1f, .1f, .9f, .9f)
        smooth.target(initial)
        smooth.sample(1000)
        smooth.target(Box(.103f, .1f, .903f, .9f))
        assertEquals(initial, smooth.sample(1016))
        smooth.target(Box(.14f, .1f, .94f, .9f))
        assertTrue(smooth.sample(1032)!!.l > .1f)
    }

    @Test
    fun automaticSubjectSwitchRestartsReadiness() {
        val a = Subject(Box(.3f, .15f, .7f, .85f), .95f, "person", 12)
        val engine = CompositionEngine()
        for (t in listOf(1000L, 1700L, 2400L, 3100L)) engine.evaluate(
            frame(listOf(a)),
            "Portrait",
            "Center",
            .55f,
            t,
        )
        assertEquals(
            "ready",
            engine.evaluate(frame(listOf(a)), "Portrait", "Center", .55f, 3800).key,
        )
        assertNotEquals(
            "ready",
            engine.evaluate(frame(listOf(a.copy(id = 13))), "Portrait", "Center", .55f, 4500).key,
        )
    }

    @Test
    fun unrelatedFaceCannotBlockSelectedObjectForClosedEyes() {
        val face = Subject(Box(.02f, .2f, .12f, .3f), .9f, "face", 15, true, eyes = .05f)
        val director = ShotDirector()
        director.choose(SubjectChoice.Detected(4, "bottle"))
        assertNotEquals(
            "eyes",
            director.evaluate(frame(listOf(bottle, face)), "Auto Director", "Center", .55f).key,
        )
    }
}
