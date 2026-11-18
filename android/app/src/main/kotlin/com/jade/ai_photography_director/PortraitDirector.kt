package com.jade.ai_photography_director

import kotlin.math.*

internal data class PortraitMatte(val side:Int, val alpha:FloatArray, val subject:Box, val face:Box?, val luminance:Float)
internal data class AutoGrade(val look:CameraLook, val exposureDelta:Float, val strength:Int, val reason:String)

internal object PortraitDirector {
    // Face/person detections seed an appearance model. A soft shape prior and four
    // edge-aware propagation passes constrain the mask in ambiguous backgrounds.
    fun matte(features:Features):PortraitMatte? {
        val scene=features.visual ?: return null
        val people=features.subjects.filter {it.label=="person" && it.confidence>=.55f}
        val face=features.subjects.filter {it.face && it.confidence>=.65f}.maxByOrNull {it.box.w*it.box.h}?.box
        val person=people.filter {face==null || (face.x in it.box.l..it.box.r && face.y in it.box.t..it.box.b)}
            .maxByOrNull {it.confidence*it.box.w*it.box.h}?.box
        val box=person ?: face?.let {Box(max(0f,it.x-it.w*1.25f),it.t,min(1f,it.x+it.w*1.25f),min(1f,it.b+it.h*2.7f))} ?: return null
        if(box.w<.05f || box.h<.08f)return null
        val n=scene.side
        val hist=FloatArray(512){1f};val back=FloatArray(512){1f}
        fun bin(i:Int)=((scene.red[i]*7.99f).toInt()*8+(scene.green[i]*7.99f).toInt())*8+(scene.blue[i]*7.99f).toInt()
        val prior=FloatArray(n*n)
        var fg=512f;var bg=512f
        for(i in prior.indices){
            val x=(i%n+.5f)/n;val y=(i/n+.5f)/n
            val u=(x-box.x)/box.w;val v=(y-box.t)/box.h
            if(x in box.l..box.r && v in 0f..1f){
                val radius=if(v<.26f) .24f else .43f
                prior[i]=((radius-abs(u))/.17f+.45f).coerceIn(.02f,.97f)
                if(face!=null && x in face.l..face.r && y in face.t..face.b)prior[i]=.98f
                if(prior[i]>.9f){hist[bin(i)]++;fg++}
            }else{back[bin(i)]++;bg++}
        }
        var mask=FloatArray(n*n){i->
            val p=prior[i];val k=bin(i);val odds=(hist[k]/fg)/(back[k]/bg)
            if(p==0f)0f else p*odds/(1-p+p*odds)
        }
        repeat(4){
            val old=mask
            mask=FloatArray(n*n){i->
                if(prior[i]==0f)0f else{
                    var sum=old[i]*2;var weight=2f
                    for(j in intArrayOf(i-n,i+n,i-1,i+1)){
                        if(j !in old.indices || abs(j%n-i%n)>1)continue
                        val diff=abs(scene.red[j]-scene.red[i])+abs(scene.green[j]-scene.green[i])+abs(scene.blue[j]-scene.blue[i])
                        val w=1f/(1f+12f*diff);sum+=w*old[j];weight+=w
                    }
                    sum/weight
                }
            }
        }
        val weight=mask.sum()
        if(weight<4)return null
        val lum=mask.indices.sumOf {(mask[it]*scene.light[it]).toDouble()}.toFloat()/weight
        return PortraitMatte(n,mask,box,face,lum)
    }

    // A neutral (white or black) translucent light field leaves spatial detail intact.
    fun field(m:PortraitMatte,style:Lighting,strength:Int):IntArray {
        val face=m.face ?: m.subject
        return IntArray(m.alpha.size){i->
            val a=m.alpha[i]
            val x=(i%m.side+.5f)/m.side
            val lateral=((face.x-x)/face.w.coerceAtLeast(.05f)*2).coerceIn(-1f,1f)
            val gain=when(style){
                Lighting.NATURAL->0f
                Lighting.STUDIO->a*(.10f+.14f*(1-m.luminance))
                Lighting.CONTOUR->a*(.04f+.27f*lateral)
                Lighting.STAGE,Lighting.STAGE_MONO->-.95f*(1-a)+.09f*a
                Lighting.HIGH_KEY->.95f*(1-a)+.15f*a
            }
            val alpha=(255*abs(gain)*strength.coerceIn(0,100)/100f).roundToInt()
            (alpha shl 24) or (if(gain>0)0xffffff else 0)
        }
    }

    fun auto(features:Features, matte:PortraitMatte?):AutoGrade? {
        val scene=features.visual ?: return null
        val dark=scene.light.count {it<.12f}.toFloat()/scene.light.size
        val bright=scene.light.count {it>.94f}.toFloat()/scene.light.size
        val mid=matte?.luminance ?: scene.mean
        val delta=when {bright>.08f->-.5f;mid<.32f && bright<.02f->.5f;mid>.72f->-.33f;else->0f}
        val studio = matte != null && mid < .52f
        val lowLight = dark > .25f && scene.mean < .30f
        val balancedContrast = scene.deviation in .18f.. .40f
        val faceLight = matte?.face != null && mid in .38f.. .70f
        var warmLight = 0f
        var warmSamples = 0
        for (i in scene.red.indices) {
            if (scene.light[i] !in .35f.. .80f || (matte?.alpha?.get(i) ?: 0f) > .35f) continue
            val r = scene.red[i]
            val g = scene.green[i]
            val b = scene.blue[i]
            if (r > g && g > b && r - b in .08f.. .27f) warmLight++
            warmSamples++
        }
        val warmPortrait = faceLight && warmSamples >= 12 && warmLight / warmSamples > .50f
        val contour = faceLight && !warmPortrait && bright < .025f && scene.deviation > .23f
        val structure = matte == null && features.horizon != null && features.horizonConfidence >= .65f &&
            balancedContrast && bright < .035f
        var look = CameraLook(
            film = when {
                warmPortrait -> Film.GOLD
                structure -> Film.DRAMATIC
                else -> Film.STANDARD
            },
            light = when {
                studio -> Lighting.STUDIO
                contour -> Lighting.CONTOUR
                else -> Lighting.NATURAL
            },
        )
        // Scene analysis recommends rendered values. The physical EV control remains separate.
        if (bright > .035f) look = look.adjusted(Adjustment.HIGHLIGHTS, -25)
        if (bright > .08f) look = look.adjusted(Adjustment.EXPOSURE, -15)
        if (lowLight) look = look.adjusted(Adjustment.NOISE_REDUCTION, 20)
        if (studio && !lowLight) look = look.adjusted(Adjustment.SHADOWS, 14)
        if (delta > 0f && !lowLight && !studio) look = look.adjusted(Adjustment.EXPOSURE, 12)
        val reason = when {
            lowLight -> "Large dark areas may show noise. Try gentle noise reduction and hold the phone steady."
            bright > .035f -> "Bright regions are near clipping. Reduce highlights to tone down detail still present in the image."
            studio -> "The detected subject is dim. Studio Light can gently lift the subject."
            warmPortrait -> "A portrait and warm midtones were detected. Gold complements the warm colors."
            contour -> "The detected face has usable light and the scene has strong contrast. Try subtle Contour Light."
            structure -> "A strong horizontal line and clear tonal separation were detected. Dramatic emphasizes the contrast."
            delta > 0f -> "Midtones are dark with room in the highlights. Try a small increase in rendered exposure."
            else -> "Exposure and contrast look balanced in the preview."
        }
        val strength = when {
            studio -> ((.59f - mid) * 120).roundToInt().coerceIn(15, 55)
            contour -> 25
            else -> 0
        }
        return AutoGrade(look, delta, strength, reason)
    }
}
