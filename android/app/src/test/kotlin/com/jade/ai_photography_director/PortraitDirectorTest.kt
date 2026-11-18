package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class PortraitDirectorTest {
    private val n=32
    private fun fixture(person:Boolean=true,bright:Float=.85f,subject:Float=.25f):Features{
        val values=FloatArray(n*n){if(it%n in 10..21 && it/n in 4..28)subject else bright}
        val map=SceneMap(n,values,values.copyOf(),values.copyOf())
        val subjects=if(person)listOf(Subject(Box(.25f,.08f,.75f,.95f),.9f,"person"),Subject(Box(.38f,.14f,.64f,.35f),.9f,"face",face=true))else emptyList()
        return Features(subjects,map.mean,0f,0f,null,0f,0f,0f,map)
    }
    @Test fun noSubjectLeavesLightingOff(){assertNull(PortraitDirector.matte(fixture(false)))}
    @Test fun maskProtectsTheInteriorAndRejectsBackground(){
        val m=PortraitDirector.matte(fixture())!!
        assertTrue(m.alpha[16*n+16]>.85f);assertTrue(m.alpha[16*n]<.05f)
        val stage=PortraitDirector.field(m,Lighting.STAGE,100)
        assertEquals(0,stage[0] and 0xffffff)
        assertTrue(stage[0] ushr 24>220)
        assertTrue(stage[16*n+16] ushr 24<45)
    }
    @Test fun zeroIntensityIsTransparent(){
        val m=PortraitDirector.matte(fixture())!!
        for(s in Lighting.entries)assertTrue(PortraitDirector.field(m,s,0).all {it ushr 24==0})
    }
    @Test fun shadowedPersonGetsConservativeStudioRecommendation(){
        val f=fixture();val a=PortraitDirector.auto(f,PortraitDirector.matte(f))!!
        assertEquals(Lighting.STUDIO,a.look.light);assertTrue(a.strength in 15..55);assertEquals(100,a.look.saturation)
    }
    @Test fun clippedSceneGetsLowerExposure(){assertTrue(PortraitDirector.auto(fixture(false,1f),null)!!.exposureDelta<0f)}
}
