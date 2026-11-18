package com.jade.ai_photography_director

import kotlin.math.*

data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val x
        get() = (l + r) / 2

    val y
        get() = (t + b) / 2

    val w
        get() = r - l

    val h
        get() = b - t

    fun lerp(o: Box, a: Float) =
        Box(l + (o.l - l) * a, t + (o.t - t) * a, r + (o.r - r) * a, b + (o.b - b) * a)

    fun iou(o: Box): Float {
        val intersection = max(0f, min(r, o.r) - max(l, o.l)) * max(0f, min(b, o.b) - max(t, o.t))
        return intersection / (w * h + o.w * o.h - intersection).coerceAtLeast(.0001f)
    }
}

data class Subject(
    val box: Box,
    val confidence: Float,
    val label: String,
    val id: Int = 0,
    val face: Boolean = false,
    val eyes: Float? = null,
    val yaw: Float? = null,
)

data class Features(
    val subjects: List<Subject>,
    val brightness: Float,
    val clipped: Float,
    val motion: Float,
    val horizon: Float?,
    val horizonConfidence: Float,
    val clutter: Float,
    val symmetry: Float,
    val visual: SceneMap? = null,
)

data class Advice(
    val key: String,
    val text: String,
    val level: Int = 1,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val targetX: Float = .5f,
    val targetY: Float = .5f,
    val score: Float = 0f,
    val frame: Box? = null,
    val zoom: Float = 1f,
    val focusLabel: String? = null,
    val reason: String = "",
    val scope: String = "",
    val confidence: Float = 1f,
    val focusBounds: Box? = null,
)

object Coordinates {
    fun toDisplay(b: Box, roi: Box) =
        Box(roi.l + b.l * roi.w, roi.t + b.t * roi.h, roi.l + b.r * roi.w, roi.t + b.b * roi.h)

    fun rotate(b: Box, quarter: Int) =
        when ((quarter % 4 + 4) % 4) {
            1 -> Box(1 - b.b, b.l, 1 - b.t, b.r)
            2 -> Box(1 - b.r, 1 - b.b, 1 - b.l, 1 - b.t)
            3 -> Box(b.t, 1 - b.r, b.b, 1 - b.l)
            else -> b
        }
}

class Tracker {
    private var previous = emptyList<Subject>()
    private var nextId = 1

    fun reset() {
        previous = emptyList()
    }

    fun update(input: List<Subject>): List<Subject> {
        val remaining = previous.toMutableList()
        val result =
            input.map { s ->
                val match =
                    remaining.filter { it.label == s.label }.maxByOrNull { it.box.iou(s.box) }
                if (match != null && match.box.iou(s.box) > .25f) {
                    remaining.remove(match)
                    s.copy(box = match.box.lerp(s.box, .65f), id = match.id)
                } else s.copy(id = nextId++)
            }
        previous = result
        return result
    }
}

class CompositionEngine {
    private val director = ShotDirector()

    fun selectSubject(choice: SubjectChoice) {
        director.choose(choice)
        reset()
    }

    fun nextShot() {
        director.next()
        candidate = ""
        since = 0
        readyScope = null
        lastObservation = 0
        current = Advice("search", "Finding another frame")
    }

    private var candidate = ""
    private var since = 0L
    private var readyScope: String? = null
    private var lastObservation = 0L
    private var current = Advice("search", "Looking for a promising frame")

    fun reset(clearSelection: Boolean = false) {
        director.reset(clearSelection)
        candidate = ""
        since = 0
        readyScope = null
        lastObservation = 0
        current = Advice("search", "Looking for a promising frame")
    }

    fun evaluate(
        f: Features,
        mode: String,
        preference: String,
        threshold: Float,
        now: Long,
    ): Advice {
        if (lastObservation > 0 && now - lastObservation > 2000) reset()
        lastObservation = now
        val advice =
            if (f.visual != null && mode != "Manual Composition")
                director.evaluate(f, mode, preference, threshold)
            else decide(f, mode, preference, threshold)
        // Confirm the same framing across observations, without a countdown or timed delay.
        val aligned = advice.key == "ready"
        val proposed =
            if (!aligned) advice
            else if (readyScope == advice.scope) advice.copy(text = "Ready to capture")
            else advice.copy(key = "steady", text = "Hold steady", level = 1)
        readyScope = advice.scope.takeIf { aligned }
        if (aligned || proposed.level == 2 || current.key == "ready") {
            current = proposed
            candidate = proposed.key
            since = now
            return current
        }
        if (candidate != proposed.key) {
            candidate = proposed.key
            since = now
        }
        if (now - since >= 550 || current.key == proposed.key) current = proposed
        current =
            current.copy(
                frame = proposed.frame,
                zoom = proposed.zoom,
                dx = proposed.dx,
                dy = proposed.dy,
                targetX = proposed.targetX,
                targetY = proposed.targetY,
                focusLabel = proposed.focusLabel,
                focusBounds = proposed.focusBounds,
                reason = proposed.reason,
                scope = proposed.scope,
                confidence = proposed.confidence,
            )
        return current
    }

    fun decide(f: Features, mode: String, preference: String, threshold: Float): Advice {
        if (mode == "Manual Composition") return Advice("manual", "Manual guides • no AI readiness")
        if (f.brightness < .13f) return Advice("dark", "Preview is too dark • add light", 2)
        if (f.clipped > .42f) return Advice("bright", "Bright areas clipped • reduce exposure", 2)
        val subjects = f.subjects.filter { it.confidence >= threshold }
        val people = subjects.filter { it.label == "person" }
        val faces = subjects.filter { it.face }
        val relevant =
            when (mode) {
                "Portrait",
                "Group Photo" -> if (people.isNotEmpty()) people else faces
                "Food" -> subjects.filter { it.label in foodLabels }
                else -> if (people.isNotEmpty()) people else subjects.filter { !it.face }
            }
        if (mode == "Landscape") {
            if (f.horizon != null && f.horizonConfidence > .65f && abs(f.horizon) > 3)
                return Advice("horizon", "Align the candidate horizon with the level guide")
            if (f.motion > .055f) return Advice("steady", "Hold steady")
            return Advice("landscape", "Explore thirds • no reliable subject readiness")
        }
        if (relevant.isEmpty())
            return Advice(
                "search",
                if (mode == "Food") "No food detected • use manual guides"
                else "No confident subject • check preview area",
            )
        val group = mode == "Group Photo" || (mode == "Auto Director" && people.size > 1)
        val chosen =
            if (group) relevant else listOf(relevant.maxBy { it.box.w * it.box.h * it.confidence })
        val b =
            Box(
                chosen.minOf { it.box.l },
                chosen.minOf { it.box.t },
                chosen.maxOf { it.box.r },
                chosen.maxOf { it.box.b },
            )
        if (b.l < .025f || b.r > .975f || b.t < .025f || b.b > .975f)
            return Advice("crop", "Widen framing • subject reaches an edge", 2)
        if (f.motion > .055f) return Advice("steady", "Hold steady")
        if (faces.any { (it.eyes ?: 1f) < .35f })
            return Advice("eyes", "Wait for visible, open eyes")
        if ((mode == "Portrait" || group) && faces.any { abs(it.yaw ?: 0f) > 32 })
            return Advice("face", "Wait for faces to turn toward camera")
        val target =
            when {
                group || preference == "Center" -> .5f
                preference == "Left third" -> 1f / 3
                preference == "Right third" -> 2f / 3
                b.x < .5f -> 1f / 3
                else -> 2f / 3
            }
        val error = target - b.x
        if (abs(error) > .075f)
            return Advice(
                "place_${if(error>0) "r" else "l"}",
                if (error > 0) "Reframe so subject shifts right →"
                else "Reframe so subject shifts left ←",
                1,
                error,
                0f,
                target,
                b.y,
            )
        if ((mode == "Portrait" || mode == "Auto Director") && faces.isNotEmpty()) {
            val face = faces.maxBy { it.box.w * it.box.h }.box
            if (face.t > .28f)
                return Advice("headroom", "Reduce space above the face ↑", 1, 0f, -1f, target, .18f)
            val yaw = faces.first().yaw ?: 0f
            if (abs(yaw) > 15 && ((yaw > 0 && b.x > .62f) || (yaw < 0 && b.x < .38f)))
                return Advice("look", "Leave more space in the facing direction")
        }
        if (b.h < .25f && mode != "Street")
            return Advice("size", "Make the subject larger in the frame")
        if (f.clutter > .65f && mode == "Portrait")
            return Advice("clutter", "Try a simpler background • high edge detail")
        val score = (100 - abs(error) * 100 - f.motion * 150 - f.clipped * 20).coerceIn(0f, 100f)
        return Advice(
            "ready",
            "Great moment — capture now!",
            0,
            targetX = target,
            targetY = b.y,
            score = score,
        )
    }

    companion object {
        val foodLabels =
            setOf(
                "banana",
                "apple",
                "sandwich",
                "orange",
                "broccoli",
                "carrot",
                "hot dog",
                "pizza",
                "donut",
                "cake",
                "bowl",
            )
    }
}
