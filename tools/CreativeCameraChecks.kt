package com.jade.ai_photography_director

fun main(){
    val neutral=CameraLook().matrix()
    for(i in neutral.indices)check(neutral[i]==if(i in listOf(0,6,12,18))1f else 0f)
    for(style in listOf(Film.NOIR,Film.STARK,Film.MUTED)){
        val m=CameraLook(style).matrix()
        for(i in 0..4)check(m[i]==m[i+5] && m[i]==m[i+10])
    }
    check(Film.entries.map { CameraLook(it).matrix().toList() }.distinct().size==Film.entries.size)
    check(Lighting.entries.map { CameraLook(light=it).matrix().toList() }.distinct().size==Lighting.entries.size)
    check(CameraLook(warmth=15).matrix().let {it[4]==15f && it[14]== -15f})
    val m=CameraLook(light=Lighting.STUDIO).matrix();check((m[0]+m[1]+m[2])*128+m[4]>128)
    check(PanoMatch.align(IntArray(3072),IntArray(3072))==null)
    val rand=java.util.Random(42)
    val image=IntArray(128*80){rand.nextInt(256)}
    fun sample(dx:Int,dy:Int)=IntArray(3072){image[(it/64+8+dy)*128+(it%64)+dx]}
    for(dx in listOf(12,21,36))for(dy in listOf(-4,0,4))check(PanoMatch.align(sample(0,0),sample(dx,dy))==Offset(dx,dy))
    println("Passed neutral transform, monochrome channels, distinct film and lighting presets, warmth, studio mid-grey, uniform rejection, nine panorama alignments")
}
