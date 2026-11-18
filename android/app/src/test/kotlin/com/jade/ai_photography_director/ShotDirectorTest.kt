package com.jade.ai_photography_director

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class ShotDirectorTest {
    private fun scene(): SceneMap {
        val n = 64
        val pixels =
            IntArray(n * n) { i ->
                val x = i % n
                val y = i / n
                if (y < 24) 0xff9cbee1.toInt()
                else {
                    val checker = if ((x / 4 + y / 3) % 2 == 0) 35 else 0
                    val r = 90 + checker
                    val g = 110 + checker
                    val b = 50 + checker
                    (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        return SceneMap.fromPixels(pixels, n)
    }

    private fun textured(): SceneMap {
        val n = 64
        return SceneMap(
            n,
            FloatArray(n * n) { i ->
                (.5 + .24 * sin((i % n) * .28) + .18 * cos((i / n) * .31))
                    .toFloat()
                    .coerceIn(0f, 1f)
            },
            FloatArray(n * n) { i ->
                (.4 + .25 * sin((i % n) * .19 + (i / n) * .17)).toFloat().coerceIn(0f, 1f)
            },
            FloatArray(n * n) { i ->
                (.45 + .2 * cos((i % n) * .13 - (i / n) * .23)).toFloat().coerceIn(0f, 1f)
            },
        )
    }

    private fun features(map: SceneMap = scene(), subjects: List<Subject> = emptyList()) =
        Features(subjects, map.mean, 0f, 0f, null, 0f, .1f, .5f, map)

    private fun person(height: Float = .30f) =
        Subject(
            Box(.5f - height * .3f, .5f - height / 2, .5f + height * .3f, .5f + height / 2),
            .95f,
            "person",
            1,
        )

    @Test
    fun unlabelledSceneWithMarginalCropGainKeepsWiderView() {
        val plans = ShotPlanner().rank(features(), "Landscape", "Nearest third", .55f)
        assertTrue(plans.isNotEmpty())
        assertEquals(Box(0f, 0f, 1f, 1f), plans.first().frame)
        assertEquals("Keep this wider view", plans.first().reason)
    }

    @Test
    fun emptyUniformSceneDoesNotInventShot() {
        val map =
            SceneMap(
                64,
                FloatArray(4096) { .5f },
                FloatArray(4096) { .5f },
                FloatArray(4096) { .5f },
            )
        assertTrue(ShotPlanner().rank(features(map), "Landscape", "Center", .55f).isEmpty())
    }

    @Test
    fun proposalsAreBoundedAndMatchPreviewAspect() {
        for (p in ShotPlanner().rank(features(), "Landscape", "Nearest third", .55f)) {
            assertTrue(
                p.frame.l >= 0 && p.frame.t >= 0 && p.frame.r <= 1.0001f && p.frame.b <= 1.0001f
            )
            assertEquals(p.frame.w, p.frame.h, .0001f)
            assertTrue(p.frame.w >= .58f)
        }
    }

    @Test
    fun smallSubjectEarnsRelativeZoom() {
        val s = person()
        val plans = ShotPlanner().rank(features(subjects = listOf(s)), "Portrait", "Center", .55f)
        assertTrue(plans.toString(), plans.first().frame.w < 1f)
        assertTrue(1 / plans.first().frame.w > 1.1f)
    }

    @Test
    fun groupFramesNeverCutADetectedMember() {
        val a = Subject(Box(.08f, .2f, .35f, .8f), .9f, "person")
        val b = Subject(Box(.65f, .2f, .92f, .8f), .9f, "person")
        val plans =
            ShotPlanner().rank(features(subjects = listOf(a, b)), "Group Photo", "Center", .55f)
        assertTrue(plans.isNotEmpty())
        for (p in plans) {
            assertTrue(
                p.frame.l <= .08f && p.frame.r >= .92f && p.frame.t <= .2f && p.frame.b >= .8f
            )
        }
    }

    @Test
    fun identicalFramesDoNotMoveOrInventZoom() {
        val map = textured()
        val transform = SceneMatcher().estimate(map, map)!!
        assertEquals(1f, transform.scale, .001f)
        assertEquals(.5f, transform.centerX, .001f)
    }

    @Test
    fun imageRegistrationRecognizesPanAndZoom() {
        val previous = textured()
        val scale = .8f
        val cx = .54f
        val cy = .46f
        val n = 64
        fun sample(data: FloatArray) =
            FloatArray(n * n) { i ->
                previous.sample(
                    data,
                    cx + (i % n / (n - 1f) - .5f) * scale,
                    cy + (i / n / (n - 1f) - .5f) * scale,
                )
            }
        val current =
            SceneMap(n, sample(previous.red), sample(previous.green), sample(previous.blue))
        val result = SceneMatcher().estimate(previous, current)!!
        assertEquals(scale, result.scale, .07f)
        assertEquals(cx, result.centerX, .04f)
        assertEquals(cy, result.centerY, .04f)
    }

    @Test
    fun transformTracksGoalUntilItFillsCurrentView() {
        val goal = Box(.15f, .15f, .85f, .85f)
        val now = SceneTransform(.7f, .5f, .5f, 0f).toCurrent(goal)
        assertEquals(0f, now.l, .001f)
        assertEquals(1f, now.r, .001f)
    }

    @Test
    fun lockedSceneDoesNotKeepRequestingMoreZoom() {
        val f = features()
        val d = ShotDirector()
        val first = d.evaluate(f, "Landscape", "Nearest third", .55f)
        for (i in 0..12) {
            val next = d.evaluate(f, "Landscape", "Nearest third", .55f)
            assertEquals(first.frame!!.w, next.frame!!.w, .0001f)
            assertEquals(first.zoom, next.zoom, .0001f)
        }
    }

    @Test
    fun subjectZoomProgressReducesRemainingZoom() {
        val d = ShotDirector()
        val map = textured()
        val first = d.evaluate(features(map, listOf(person(.3f))), "Portrait", "Center", .55f)
        val second = d.evaluate(features(map, listOf(person(.5f))), "Portrait", "Center", .55f)
        assertTrue(second.zoom < first.zoom)
    }

    @Test
    fun croppedSubjectNeverGetsCaptureRecommendation() {
        val s = Subject(Box(0f, .2f, .4f, .9f), .9f, "person")
        val advice =
            ShotDirector().evaluate(features(subjects = listOf(s)), "Portrait", "Center", .55f)
        assertNotEquals("ready", advice.key)
        assertEquals("uncrop", advice.key)
    }

    @Test
    fun nextShotCanSelectAnotherCandidate() {
        val d = ShotDirector()
        val f = features(subjects = listOf(person()))
        val first = d.evaluate(f, "Portrait", "Center", .55f)
        d.next()
        val next = d.evaluate(f, "Portrait", "Center", .55f)
        assertNotEquals(first.frame, next.frame)
        assertEquals("person", next.focusLabel)
    }

    @Test
    fun alignedRecognizedSubjectCanBecomeReadyAfterFraming() {
        val map = textured()
        val s = Subject(Box(.3f, .15f, .7f, .85f), .95f, "person")
        val f = features(map, listOf(s))
        val engine = CompositionEngine()
        assertNotEquals("ready", engine.evaluate(f, "Portrait", "Center", .55f, 1000).key)
        for (t in listOf(1700L, 2400L, 3100L, 3800L)) engine.evaluate(
            f,
            "Portrait",
            "Center",
            .55f,
            t,
        )
        assertEquals("ready", engine.evaluate(f, "Portrait", "Center", .55f, 4500).key)
    }

    @Test
    fun displayInterpolationIsContinuousAndDoesNotOvershoot() {
        val smoother = SmoothFrame()
        smoother.target(Box(.1f, .1f, .5f, .5f))
        smoother.sample(1000)
        smoother.target(Box(.3f, .3f, .9f, .9f))
        var previous = .1f
        for (t in 1016L..1500L step 16) {
            val b = smoother.sample(t)!!
            assertTrue(b.l >= previous && b.l < .3f)
            assertTrue(b.l - previous < .025f)
            previous = b.l
        }
        assertTrue(previous > .28f)
    }

    @Test
    fun exposureFailureOverridesShotSearch() {
        val advice =
            ShotDirector().evaluate(features().copy(brightness = .01f), "Landscape", "Center", .55f)
        assertEquals("dark", advice.key)
        assertEquals(2, advice.level)
    }

    @Test
    fun unlabelledSceneryCanReachFramingReadiness() {
        val f = features(textured())
        val engine = CompositionEngine()
        for (t in listOf(1000L, 1700L, 2400L, 3100L)) engine.evaluate(
            f,
            "Landscape",
            "Center",
            .55f,
            t,
        )
        assertEquals("ready", engine.evaluate(f, "Landscape", "Center", .55f, 3800).key)
    }

    @Test
    fun aimArrowAgreesWithHighlightedScreenPosition() {
        for (cx in listOf(.2f, .8f)) {
            val s = Subject(Box(cx - .07f, .3f, cx + .07f, .6f), .95f, "person")
            val a =
                ShotDirector().evaluate(features(subjects = listOf(s)), "Portrait", "Center", .55f)
            assertEquals("aim", a.key)
            assertEquals(a.frame!!.x - .5f, a.dx, .0001f)
            assertTrue(
                if (cx > .5f) a.dx > 0 && a.text.contains("→") else a.dx < 0 && a.text.contains("←")
            )
        }
    }

    @Test
    fun portraitWithoutDetectedPeopleCannotRecommendCapture() {
        assertEquals("scan", ShotDirector().evaluate(features(), "Portrait", "Center", .55f).key)
    }

    @Test
    fun darkSceneNeverRecommendsZoomOrCapture() {
        val a =
            ShotDirector().evaluate(features().copy(brightness = .08f), "Landscape", "Center", .55f)
        assertEquals("dark", a.key)
        assertEquals(2, a.level)
    }

    @Test
    fun concentratedUnlabelledDetailCanStillEarnTighterFraming() {
        val pixels =
            IntArray(4096) { i ->
                val x = i % 64
                val y = i / 64
                if (x in 20..50 && y in 27..58) {
                    if ((x / 3 + y / 3) % 2 == 0) 0xffd52e20.toInt() else 0xff47d540.toInt()
                } else 0xff454545.toInt()
            }
        val map = SceneMap.fromPixels(pixels, 64)
        val plans = ShotPlanner().rank(features(map), "Landscape", "Nearest third", .55f)
        assertTrue(plans.toString(), plans.first().frame.w < 1f)
        assertEquals("Concentrate visible detail", plans.first().reason)
    }

    @Test
    fun nextDoesNotInventAnotherCropWhenOnlyWideViewQualifies() {
        val director = ShotDirector()
        val f = features()
        val first = director.evaluate(f, "Landscape", "Center", .55f)
        director.next()
        assertEquals(first.frame, director.evaluate(f, "Landscape", "Center", .55f).frame)
    }
}
