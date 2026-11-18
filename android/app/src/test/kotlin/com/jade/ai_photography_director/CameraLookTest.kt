package com.jade.ai_photography_director

import org.junit.Assert.*
import org.junit.Test

class CameraLookTest {
    @Test fun neutralKeepsRgbAndAlpha() {
        val m=CameraLook().matrix()
        for(i in m.indices) assertEquals(if(i in listOf(0,6,12,18)) 1f else 0f,m[i],.00001f)
    }
    @Test fun noirChannelsMatch() {
        val m=CameraLook(Film.NOIR).matrix()
        for(i in 0..4){assertEquals(m[i],m[i+5],0f);assertEquals(m[i],m[i+10],0f)}
    }
    @Test fun warmthShiftsRedAndBlueInOppositeDirections() {
        val m=CameraLook(warmth=15).matrix()
        assertEquals(15f,m[4],0f);assertEquals(-15f,m[14],0f)
    }
    @Test fun everyFilmHasADistinctTransform() {
        assertEquals(Film.entries.size,Film.entries.map {CameraLook(it).matrix().toList()}.distinct().size)
    }
    @Test fun studioRaisesMidGrey() {
        val m=CameraLook(light=Lighting.STUDIO).matrix()
        assertTrue((m[0]+m[1]+m[2])*128+m[4]>128)
    }
}
