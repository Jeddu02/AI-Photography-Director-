package com.jade.ai_photography_director

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class SceneCoachTest {
    private fun map(fn: (Float,Float)->Float): SceneMap {
        val a = FloatArray(64*64) { fn((it%64+.5f)/64,(it/64+.5f)/64) }
        return SceneMap(64,a,a,a)
    }

    @Test fun flatWallHasNoStructuralAdvice() {
        val m = map { _,_->.7f }
        assertEquals(0f, SceneCoach.strategy(m,SceneCoach.full,null,"Street").strength,0f)
    }

    @Test fun brightBackgroundAndDarkSubject() {
        val b = Box(.3f,.3f,.6f,.6f)
        val m = map { x,y -> if(x in b.l..b.r && y in b.t..b.b) .13f else .8f }
        assertTrue(SceneCoach.lightingCue(SceneCoach.lighting(m),SceneCoach.lighting(m,b))!!.contains("background"))
    }

    @Test fun horizontalMirrorGetsStructuralVote() {
        val m = map { x,y -> if ((floor(x*9+x*x*5)+floor(abs(y-.5f)*13)).toInt()%2==0) .2f else .7f }
        assertTrue(SceneCoach.strategy(m,SceneCoach.full,null,"Landscape").strength>.3f)
    }

    @Test fun diagonalWedgeGetsConvergenceVote() {
        val m = map { x,y -> if (abs(x-.5f)<(y-.33f)*(.47f/.67f)) .8f else .25f+x*.30f }
        assertTrue(SceneCoach.strategy(m,SceneCoach.full,null,"Street").reason.contains("converging"))
    }

    @Test fun emptySideBesideSubjectCanEarnQuietSpace() {
        val m = map { x,y -> if (x<.62f && (floor(x*17)+floor(y*17)).toInt()%2==0) .1f else .7f }
        assertTrue(SceneCoach.strategy(m,SceneCoach.full,Box(.1f,.2f,.4f,.75f),"Food").reason.contains("quiet"))
    }

    @Test fun reviewDoesNotMutatePixelsOrInferMotion() {
        val m = map { _,_->.02f }; val before = m.light.copyOf()
        val f=Features(emptyList(),.02f,0f,1f,null,0f,0f,0f,m)
        val lines=SceneCoach.review(f,.55f,"Street")
        assertTrue(lines.any { it.contains("Add light") })
        assertFalse(lines.any { it.contains("motion blur") })
        assertArrayEquals(before,m.light,0f)
        assertEquals(listOf("Image measurements unavailable"),SceneCoach.review(f.copy(visual=null),.55f,"Street"))
    }

    @Test fun threeBorderBandsSuggestFramingAroundOpening() {
        val m=map { x,y -> if ((x in .12f.. .28f || x in .72f.. .88f || y in .12f.. .28f) && ((x*43+y*33).toInt()%2==0)) .82f else .25f }
        assertTrue(SceneCoach.strategy(m,SceneCoach.full,null,"Street").reason.contains("surrounding"))
    }

    @Test fun upperAndLowerPreferencesGenerateOrderedCrops() {
        val subject = Subject(Box(.40f,.40f,.53f,.65f),.90f,"bottle")
        val focus = Focus(subject.box,subject.label,listOf(subject))
        val upper = SubjectFraming(focus,listOf(subject),"Street","Upper third")
        val lower = SubjectFraming(focus,listOf(subject),"Street","Lower third")
        assertTrue(upper.preferredTop(.6f) > lower.preferredTop(.6f))
    }

    @Test fun weakTrackingDampsJitter() {
        val frame=Box(.1f,.2f,.8f,.9f)
        fun sample(confidence: Float): Float {
            val smoother=SmoothFrame()
            smoother.target(frame,confidence)
            smoother.sample(100)
            smoother.target(frame.copy(l=.3f),confidence)
            return smoother.sample(116)!!.l
        }
        assertTrue(sample(.4f)<sample(.99f))
    }

    @Test fun disappearedSelectionPrecedesDarkSceneWarning() {
        val m=map { _,_->.02f }
        val d=ShotDirector()
        d.choose(SubjectChoice.Detected(12,"person"))
        val f=Features(emptyList(),m.mean,0f,0f,null,0f,0f,0f,m)
        assertEquals("subject_lost",d.evaluate(f,"Street","Center",.55f).key)
    }
}
