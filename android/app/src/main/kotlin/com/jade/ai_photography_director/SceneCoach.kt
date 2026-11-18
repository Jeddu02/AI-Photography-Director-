package com.jade.ai_photography_director

import kotlin.math.*

/** Evidence is measured in normalized crop coordinates. Each vote has a bounded contribution. */
internal object SceneCoach {
    private val structuralModes = setOf("Landscape", "Street", "Auto Director")
    private val apexX = floatArrayOf(.35f,.5f,.65f)
    private val apexY = floatArrayOf(.33f,.5f)
    private val feet = floatArrayOf(.03f,.97f)
    val full = Box(0f, 0f, 1f, 1f)
    data class Strategy(val strength: Float, val reason: String)
    data class Light(val mean: Float, val shadows: Float, val highlights: Float, val detail: Float)

    private fun index(m: SceneMap, x: Float, y: Float): Int =
        (y * m.side).toInt().coerceIn(1, m.side - 2) * m.side +
            (x * m.side).toInt().coerceIn(1, m.side - 2)

    fun lighting(m: SceneMap, b: Box = full): Light {
        var lum = 0f
        var shadows = 0
        var highlights = 0
        var detail = 0f
        for (y in 0 until 24) for (x in 0 until 24) {
            val i = index(m, b.l + b.w * (x + .5f) / 24, b.t + b.h * (y + .5f) / 24)
            val v = m.light[i]
            lum += v
            if (v < .10f) shadows++
            if (v > .97f) highlights++
            detail += abs(4 * v - m.light[i-1] - m.light[i+1] - m.light[i-m.side] - m.light[i+m.side])
        }
        return Light(lum / 576, shadows / 576f, highlights / 576f, detail / 576)
    }

    fun lightingCue(scene: Light, subject: Light?): String? = when {
        scene.mean < .13f -> "Add light before taking this shot"
        scene.highlights > .42f -> "Reduce exposure to protect bright detail"
        subject != null && subject.mean < .26f && scene.mean - subject.mean > .20f ->
            "Subject looks dark against the background · try light from the front"
        else -> null
    }

    fun strategy(m: SceneMap, b: Box, target: Box?, mode: String): Strategy {
        var vertical = 0f
        var horizontal = 0f
        var texture = 0f
        var left = 0f
        var right = 0f
        val sides = FloatArray(4)
        var interior = 0f
        for (y in 0..9) for (x in 0..9) {
            val u = (x + .5f) / 10
            val v = (y + .5f) / 10
            val i = index(m, b.l + b.w*u, b.t + b.h*v)
            val e = m.edge[i]
            texture += e / 100
            vertical += abs(m.light[i] - m.light[index(m,b.l+b.w*(1-u),b.t+b.h*v)]) / 100
            horizontal += abs(m.light[i] - m.light[index(m,b.l+b.w*u,b.t+b.h*(1-v))]) / 100
            if (x < 3) left += e/30
            if (x > 6) right += e/30
            if (x in 1..2) sides[0] += e/20
            if (x in 7..8) sides[1] += e/20
            if (y in 1..2) sides[2] += e/20
            if (y in 7..8) sides[3] += e/20
            if (x in 3..6 && y in 3..6) interior += e/16
        }
        var best = Strategy(0f, "")
        fun vote(raw: Float, reason: String) {
            val strength = raw.coerceIn(0f, 1f)
            if (strength > .30f && strength > best.strength) best = Strategy(strength, reason)
        }
        val structural = mode in structuralModes
        if (structural && m.deviation > .055f) {
            val centered = target == null || abs((target.x-b.l)/b.w-.5f)<.18f
            if (centered && texture in .075f.. .55f && min(vertical,horizontal)<.045f && max(vertical,horizontal) - min(vertical,horizontal) > .045f) {
                vote((1 - min(vertical,horizontal)/.085f) * min(1f,texture/.23f),
                    if (vertical <= horizontal) "Balance the matching left and right sides"
                    else "Balance the repeated shapes above and below")
            }
            vote((sides.sorted()[1] - interior - .06f)*2, "Keep the surrounding edges around the open center")
            // Trace two inward-facing diagonal edges to the same candidate intersection.
            for (vx in apexX) for (vy in apexY) {
                if (target != null && (abs(target.x-(b.l+b.w*vx))>b.w*.20f || abs(target.y-(b.t+b.h*vy))>b.h*.25f)) continue
                var weakest = 1f
                for (foot in feet) {
                    var hits = 0
                    val slope = (foot-vx)*b.w/((1f-vy)*b.h)
                    for (k in 2..11) {
                        val t = k/12f
                        val i = index(m,b.l+b.w*(vx+(foot-vx)*t),b.t+b.h*(vy+(1-vy)*t))
                        val gx = m.light[i+1]-m.light[i-1]
                        val gy = m.light[i+m.side]-m.light[i-m.side]
                        if (abs(gx)>.09f && abs(gx*slope+gy)<.9f*abs(gx)) hits++
                    }
                    weakest = min(weakest,hits/10f)
                }
                vote((weakest-.25f)*1.55f,"Keep both converging edges leading into the frame")
            }
        }
        if (target != null) {
            val quiet = if (target.x < b.x) right else left
            vote((texture-quiet-.03f)*2.2f, "Leave quiet space beside the subject")
        }
        return best
    }

    fun review(f: Features, threshold: Float, mode: String): List<String> {
        val m = f.visual ?: return listOf("Image measurements unavailable")
        val subject = ShotPlanner().focus(f,mode,threshold)
        val light = lighting(m)
        val result = mutableListOf<String>()
        val composition = strategy(m,full,subject?.bounds,mode)
        result += "Composition · " + when {
            subject != null && CropProtection.margin(subject.bounds,full)<.035f ->
                "The subject touches an edge. Try a wider frame."
            composition.reason.isNotEmpty() -> composition.reason
            subject != null -> "Compare a centered crop with a third-line placement."
            else -> "No clear framing pattern measured. Choose the main point of interest."
        }
        result += "Light · " + (lightingCue(light,subject?.let { lighting(m,it.bounds) }) ?: when {
            light.highlights>.08f -> "Bright areas approach white. Try a lower exposure."
            light.shadows>.32f -> "Deep shadows cover much of the frame. Check important detail."
            else -> "Most tones fall within the measured brightness range."
        })
        result += "Detail · " + if (light.detail<.045f)
            "Little fine contrast at review scale. Enlarge the subject to check focus."
            else "Visible edge detail at review scale. Enlarge to inspect sharpness."
        if (f.horizon != null && f.horizonConfidence>=.65f && abs(f.horizon)>3)
            result += "Alignment · A strong edge slopes. Check whether the tilt is intentional."
        return result
    }
}
