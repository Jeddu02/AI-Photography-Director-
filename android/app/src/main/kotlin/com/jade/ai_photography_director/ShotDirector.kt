package com.jade.ai_photography_director

import kotlin.math.*

/** Low-resolution scene measurements. No image/descriptor is persisted. */
class SceneMap(val side: Int, val red: FloatArray, val green: FloatArray, val blue: FloatArray) {
    init {
        require(
            side >= 8 && red.size == side * side && green.size == red.size && blue.size == red.size
        )
    }

    val light = FloatArray(red.size) { .2126f * red[it] + .7152f * green[it] + .0722f * blue[it] }
    val mean = light.average().toFloat()
    val deviation =
        sqrt(light.sumOf { ((it - mean) * (it - mean)).toDouble() } / light.size).toFloat()
    val edge = FloatArray(light.size)
    val interest = FloatArray(light.size)
    val totalInterest: Float

    init {
        val ar = red.average().toFloat()
        val ag = green.average().toFloat()
        val ab = blue.average().toFloat()
        for (y in 1 until side - 1) for (x in 1 until side - 1) {
            val i = y * side + x
            edge[i] =
                min(
                    1f,
                    (abs(light[i + 1] - light[i - 1]) + abs(light[i + side] - light[i - side])) *
                        2.5f,
                )
            val colour =
                sqrt((red[i] - ar).pow(2) + (green[i] - ag).pow(2) + (blue[i] - ab).pow(2))
                    .coerceAtMost(1f)
            val contrast =
                abs(
                    light[i] - (light[i - 1] + light[i + 1] + light[i - side] + light[i + side]) / 4
                ) * 4
            interest[i] = (.46f * edge[i] + .34f * colour + .20f * contrast).coerceIn(0f, 1f)
        }
        totalInterest = interest.sum().coerceAtLeast(.0001f)
    }

    fun sample(data: FloatArray, x: Float, y: Float): Float {
        val fx = (x * (side - 1)).coerceIn(0f, (side - 1).toFloat())
        val fy = (y * (side - 1)).coerceIn(0f, (side - 1).toFloat())
        val ix = fx.toInt()
        val iy = fy.toInt()
        val nx = min(ix + 1, side - 1)
        val ny = min(iy + 1, side - 1)
        val dx = fx - ix
        val dy = fy - iy
        return (data[iy * side + ix] * (1 - dx) + data[iy * side + nx] * dx) * (1 - dy) +
            (data[ny * side + ix] * (1 - dx) + data[ny * side + nx] * dx) * dy
    }

    companion object {
        fun fromPixels(pixels: IntArray, side: Int) =
            SceneMap(
                side,
                FloatArray(pixels.size) { ((pixels[it] shr 16) and 255) / 255f },
                FloatArray(pixels.size) { ((pixels[it] shr 8) and 255) / 255f },
                FloatArray(pixels.size) { (pixels[it] and 255) / 255f },
            )
    }
}

data class Focus(val bounds: Box, val label: String, val members: List<Subject> = emptyList()) {
    val signature
        get() = label + members.map { it.id }.sorted().joinToString(",")
}

data class ShotProposal(
    val frame: Box,
    val score: Float,
    val focus: Focus?,
    val reason: String = "",
    val baseScore: Float = score,
)

/** Ranks bounded same-aspect-ratio crops, including scenes outside COCO vocabulary. */
class ShotPlanner {
    fun focus(
        f: Features,
        mode: String,
        threshold: Float,
        choice: SubjectChoice = SubjectChoice.Automatic,
    ): Focus? {
        val accepted = f.subjects.filter { it.confidence >= threshold }
        if (choice == SubjectChoice.WholeScene) return null
        if (choice is SubjectChoice.Region) return Focus(choice.bounds, "selected area")
        if (choice is SubjectChoice.Detected) {
            val selected =
                accepted.firstOrNull { it.id == choice.id && it.label == choice.label }
                    ?: return null
            return Focus(selected.box, selected.label, listOf(selected))
        }
        val people = accepted.filter { it.label == "person" }
        val faces = accepted.filter { it.face }
        val list =
            when (mode) {
                "Group Photo" -> people + faces
                "Portrait" -> if (people.isNotEmpty()) people else faces
                "Food" -> accepted.filter { it.label in CompositionEngine.foodLabels }
                "Landscape" -> emptyList()
                else -> if (people.isNotEmpty()) people else accepted.filter { !it.face }
            }
        if (list.isEmpty()) return null
        val group = mode == "Group Photo" || (mode == "Auto Director" && people.size > 1)
        val chosen = if (group) list else listOf(list.maxBy { it.box.w * it.box.h * it.confidence })
        return Focus(
            Box(
                chosen.minOf { it.box.l },
                chosen.minOf { it.box.t },
                chosen.maxOf { it.box.r },
                chosen.maxOf { it.box.b },
            ),
            if (group) "group" else chosen.first().label,
            chosen,
        )
    }

    fun rank(
        f: Features,
        mode: String,
        preference: String,
        threshold: Float,
        choice: SubjectChoice = SubjectChoice.Automatic,
    ): List<ShotProposal> {
        val map = f.visual ?: return emptyList()
        val subject = focus(f, mode, threshold, choice)
        if (choice is SubjectChoice.Detected && subject == null) return emptyList()
        val protectedSubjects = f.subjects.filter { it.confidence >= threshold }
        if (
            subject == null &&
                choice != SubjectChoice.WholeScene &&
                (map.deviation < .035f || map.interest.average() < .025)
        )
            return emptyList()
        val style =
            if (mode == "Auto Director") {
                when {
                    subject?.label == "group" -> "Group Photo"
                    subject?.label == "person" || subject?.members?.any { it.face } == true ->
                        "Portrait"
                    subject?.label in CompositionEngine.foodLabels -> "Food"
                    else -> mode
                }
            } else mode
        val framing = subject?.let { SubjectFraming(it, protectedSubjects, style, preference) }
        val candidates = mutableListOf<ShotProposal>()
        for (size in listOf(1f, .86f, .72f, .58f)) {
            val steps = if (size == 1f) 0..0 else 0..5
            val frames = linkedSetOf<Box>()
            fun addFrame(left: Float, top: Float) {
                val l = left.coerceIn(0f, 1 - size)
                val t = top.coerceIn(0f, 1 - size)
                frames += Box(l, t, l + size, t + size)
            }
            for (y in steps) for (x in steps) addFrame((1 - size) * x / 5, (1 - size) * y / 5)
            if (size < 1f && framing != null) {
                addFrame(framing.anchorX - framing.targetX * size, framing.preferredTop(size))
            }
            for (b in frames) {
                if (!CropProtection.accepts(b, subject, protectedSubjects)) continue
                val score = score(map, b, framing, subject?.bounds) ?: continue
                val evidence = SceneCoach.strategy(map, b, subject?.bounds, style)
                val reason =
                    if (evidence.strength > .45f) evidence.reason
                    else if (b.w > .99f) "Keep this wider view"
                    else framing?.reason(b) ?: "Concentrate visible detail"
                candidates +=
                    ShotProposal(b, score + evidence.strength * .035f, subject, reason, score)
            }
        }
        val sorted = candidates.sortedByDescending { it.score }
        if (sorted.isEmpty()) return emptyList()
        val full = sorted.firstOrNull { it.frame.w > .99f }
        if (choice == SubjectChoice.WholeScene) return listOfNotNull(full)
        // Cropping must earn a clear benefit; keep wider framing for marginal differences.
        val requiredGain = if (subject != null) .07f else .045f
        val eligible =
            sorted.filter {
                it.frame.w > .99f || full == null || it.baseScore - full.baseScore >= requiredGain
            }
        val first = eligible.firstOrNull() ?: return listOfNotNull(full)
        // Expose at most three feasible choices to the live director: the strongest crop,
        // a wider fallback when available, and one distinct alternative.
        val diverse = mutableListOf(first)
        if (full != null && full != first) diverse += full
        for (p in eligible) {
            if (diverse.size == 3) break
            if (
                p !in diverse &&
                    diverse
                        .filter { it != full || it == first }
                        .none { it.frame.iou(p.frame) > .68f }
            )
                diverse += p
        }
        return diverse
    }

    private fun score(map: SceneMap, b: Box, framing: SubjectFraming?, target: Box?): Float? {
        var mass = 0f
        var edges = 0f
        var light = 0f
        var cx = 0f
        var cy = 0f
        var count = 0
        var border = 0f
        var borderCount = 0
        var distraction = 0f
        var outsideCount = 0
        var darkEdges = 0f
        var edgeWeight = 0f
        val n = map.side
        val x0 = (b.l * n).toInt().coerceIn(0, n - 1)
        val x1 = (b.r * n).toInt().coerceIn(x0 + 1, n)
        val y0 = (b.t * n).toInt().coerceIn(0, n - 1)
        val y1 = (b.b * n).toInt().coerceIn(y0 + 1, n)
        for (y in y0 until y1) for (x in x0 until x1) {
            val i = y * n + x
            val value = map.interest[i]
            mass += value
            cx += value * (x + .5f) / n
            cy += value * (y + .5f) / n
            edges += map.edge[i]
            light += map.light[i]
            count++
            if (
                target != null &&
                    ((x + .5f) / n !in (target.l - .025f)..(target.r + .025f) ||
                        (y + .5f) / n !in (target.t - .025f)..(target.b + .025f))
            ) {
                distraction += map.edge[i] + .65f * ((map.light[i] - .78f) / .22f).coerceIn(0f, 1f)
                outsideCount++
            }
            if (framing == null) {
                val top = y - y0 < (y1 - y0) * .25f
                val side = min(x - x0, x1 - 1 - x) < (x1 - x0) * .14f
                if (top || side) {
                    val weight = if (top) 1.4f else 1f
                    darkEdges += weight * ((.20f - map.light[i]) / .20f).coerceIn(0f, 1f)
                    edgeWeight += weight
                }
            }
            if (x - x0 < 2 || x1 - x <= 2 || y - y0 < 2 || y1 - y <= 2) {
                border += map.edge[i]
                borderCount++
            }
        }
        if (count == 0) return null
        val density = mass / count
        val exposure = 1 - abs(light / count - .5f) * 1.6f
        val retention = mass / map.totalInterest
        val detail = (edges / count / .28f).coerceIn(0f, 1f)
        val borderClutter = border / max(1, borderCount)
        var value =
            .35f * density + .12f * detail + .13f * exposure + .04f * retention + .025f * b.w -
                .10f * borderClutter
        if (framing != null) {
            // Dense edges and bright highlights outside the target compete for attention.
            value += framing.score(b) - .55f * distraction / outsideCount.coerceAtLeast(1)
        } else if (mass > 0f) {
            val x = (cx / mass - b.l) / b.w
            val y = (cy / mass - b.t) / b.h
            val balance = 1 - (abs(x - .5f) + abs(y - .5f)) * .65f
            // Concentration improves composition without rewarding arbitrary endless zoom.
            value +=
                .28f * sqrt(density.coerceAtLeast(0f)) + .13f * balance -
                    .16f * darkEdges / edgeWeight.coerceAtLeast(1f)
        }
        return value
    }
}

/**
 * Maps a point in the new preview into the preceding preview. Zoom is relative, never lens
 * metadata.
 */
data class SceneTransform(
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
    val error: Float,
) {
    fun toCurrent(b: Box) =
        Box(
            (b.l - centerX) / scale + .5f,
            (b.t - centerY) / scale + .5f,
            (b.r - centerX) / scale + .5f,
            (b.b - centerY) / scale + .5f,
        )
}

class SceneMatcher {
    fun estimate(previous: SceneMap, current: SceneMap): SceneTransform? {
        if (previous.deviation < .025f || current.deviation < .025f) return null
        val delta = (current.mean - previous.mean) * .4f
        fun loss(scale: Float, cx: Float, cy: Float): Float {
            var sum = 0f
            var count = 0
            for (y in 0 until 12) for (x in 0 until 12) {
                val u = .15f + .7f * (x + .5f) / 12
                val v = .15f + .7f * (y + .5f) / 12
                val px = cx + (u - .5f) * scale
                val py = cy + (v - .5f) * scale
                if (px !in 0f..1f || py !in 0f..1f) continue
                val pl = previous.sample(previous.light, px, py)
                val cl = current.sample(current.light, u, v)
                val pr =
                    previous.sample(previous.red, px, py) - previous.sample(previous.green, px, py)
                val cr = current.sample(current.red, u, v) - current.sample(current.green, u, v)
                val pb =
                    previous.sample(previous.blue, px, py) - previous.sample(previous.green, px, py)
                val cb = current.sample(current.blue, u, v) - current.sample(current.green, u, v)
                sum += .65f * abs(cl - pl - delta) + .175f * abs(cr - pr) + .175f * abs(cb - pb)
                count++
            }
            return if (count < 100) 1f else sum / count
        }
        val identity = loss(1f, .5f, .5f)
        if (identity < .009f) return SceneTransform(1f, .5f, .5f, identity)
        var best = SceneTransform(1f, .5f, .5f, identity)
        for (scale in listOf(.60f, .70f, .80f, .90f, 1f, 1.10f, 1.25f, 1.45f)) for (dx in
            -3..3) for (dy in -3..3) {
            val cx = .5f + dx * .04f
            val cy = .5f + dy * .04f
            val error = loss(scale, cx, cy)
            // A small regularizer avoids moving a target on ambiguous/flat images.
            if (error + .002f * abs(scale - 1) < best.error + .002f * abs(best.scale - 1))
                best = SceneTransform(scale, cx, cy, error)
        }
        val coarse = best
        for (ds in -1..1) for (dx in -1..1) for (dy in -1..1) {
            val scale = (coarse.scale + ds * .035f).coerceIn(.55f, 1.5f)
            val cx = coarse.centerX + dx * .018f
            val cy = coarse.centerY + dy * .018f
            val error = loss(scale, cx, cy)
            if (error < best.error) best = SceneTransform(scale, cx, cy, error)
        }
        if (best.error > .105f) return null
        return if (identity - best.error < .004f && identity < .06f)
            SceneTransform(1f, .5f, .5f, identity)
        else best
    }
}

/** Locks one goal frame, tracks it through pan/zoom, and only replans on loss or explicit Next. */
class ShotDirector {
    private val planner = ShotPlanner()
    private val matcher = SceneMatcher()
    private var goal: Box? = null
    private var previous: SceneMap? = null
    private var previousFocus: Focus? = null
    private var selected: ShotProposal? = null
    private var alternate = 0
    private var misses = 0
    private var choice: SubjectChoice = SubjectChoice.Automatic
    private var regionReference: SceneMap? = null
    private var trackedRegion: Box? = null
    private var regionLost = false

    fun choose(value: SubjectChoice) {
        choice = value
        trackedRegion = (value as? SubjectChoice.Region)?.bounds
        regionReference = null
        regionLost = false
        reset()
    }

    fun reset(clearSelection: Boolean = false) {
        if (clearSelection) {
            choice = SubjectChoice.Automatic
            trackedRegion = null
            regionReference = null
            regionLost = false
        }
        goal = null
        previous = null
        previousFocus = null
        selected = null
        alternate = 0
        misses = 0
    }

    fun next() {
        val next = alternate + 1
        reset()
        alternate = next
    }

    fun evaluate(f: Features, mode: String, preference: String, threshold: Float): Advice {
        val map = f.visual ?: return Advice("search", "Pan slowly to look for a shot")
        var regionTransform: SceneTransform? = null
        val region =
            trackedRegion?.let {
                val reference = regionReference
                regionTransform = reference?.let { matcher.estimate(it, map) }
                val updated = if (reference == null) it else regionTransform?.toCurrent(it)
                if (
                    updated == null || CropProtection.margin(updated, Box(0f, 0f, 1f, 1f)) < -.015f
                ) {
                    regionLost = true
                } else if (!regionLost) {
                    trackedRegion = updated
                    regionReference = map
                }
                updated
            }
        if (regionLost)
            return Advice("subject_lost", "Target lost. Tap Subject to select again.", 2)
        val activeChoice = region?.let { SubjectChoice.Region(it) } ?: choice
        val currentFocus = planner.focus(f, mode, threshold, activeChoice)
        if (choice is SubjectChoice.Detected && currentFocus == null) {
            reset()
            return Advice("subject_lost", "Selected subject lost • choose again", 2)
        }
        val subjectBox = currentFocus?.bounds
        val margin =
            if (currentFocus?.members?.any { it.label == "person" } == true) .035f else .02f
        if (subjectBox != null && CropProtection.margin(subjectBox, SceneCoach.full) < margin) {
            goal = null
            selected = null
            previous = null
            previousFocus = null
            return Advice(
                "uncrop",
                "Widen framing • leave space around the subject",
                2,
                frame = SceneCoach.full,
                zoom = .9f,
                reason = "Keep the full subject away from the edges",
            )
        }
        val light = SceneCoach.lighting(map)
        val lightingCue =
            SceneCoach.lightingCue(light, currentFocus?.let { SceneCoach.lighting(map, it.bounds) })
        if (f.brightness < .13f || light.mean < .13f)
            return Advice("dark", "Add light before taking this shot", 2, frame = goal)
        if (f.clipped > .42f || light.highlights > .42f)
            return Advice("bright", "Reduce exposure before taking this shot", 2, frame = goal)
        if (lightingCue != null) return Advice("backlight", lightingCue, 2, frame = goal)
        if (
            currentFocus == null &&
                choice == SubjectChoice.Automatic &&
                mode in listOf("Portrait", "Group Photo")
        ) {
            reset()
            return Advice("scan", "Bring people clearly into the preview")
        }
        if (previousFocus?.signature != currentFocus?.signature) {
            goal = null
            selected = null
            previous = null
        }
        val last = previous
        val currentGoal = goal
        if (currentGoal != null && last != null) {
            val old = previousFocus
            val transform =
                if (choice is SubjectChoice.Region) regionTransform
                else if (
                    old != null && currentFocus != null && old.signature == currentFocus.signature
                ) {
                    val scale =
                        sqrt(
                                (old.bounds.w / currentFocus.bounds.w.coerceAtLeast(.01f)) *
                                    (old.bounds.h / currentFocus.bounds.h.coerceAtLeast(.01f))
                            )
                            .coerceIn(.55f, 1.5f)
                    SceneTransform(
                        scale,
                        old.bounds.x - scale * (currentFocus.bounds.x - .5f),
                        old.bounds.y - scale * (currentFocus.bounds.y - .5f),
                        0f,
                    )
                } else matcher.estimate(last, map)
            if (transform == null) misses++
            else {
                goal = transform.toCurrent(currentGoal)
                misses = 0
            }
            if (
                misses >= 2 ||
                    goal!!.w !in .25f..2.2f ||
                    goal!!.x !in -.4f..1.4f ||
                    goal!!.y !in -.4f..1.4f
            ) {
                goal = null
                selected = null
                misses = 0
            }
        }
        previous = map
        previousFocus = currentFocus
        if (goal == null) {
            if (f.motion > .14f) return Advice("scan", "Pan slowly — looking for a frame")
            val choices = planner.rank(f, mode, preference, threshold, activeChoice)
            if (choices.isEmpty())
                return Advice("scan", "Pan slowly to find a clearer subject or detail")
            selected = choices[alternate % choices.size]
            goal = selected!!.frame
        }
        val target = goal!!
        val zoom = (1 / target.w).coerceIn(.5f, 2f)
        val base =
            Advice(
                "aim",
                "Aim at the highlighted frame",
                frame = target,
                zoom = zoom,
                focusLabel = currentFocus?.label,
                focusBounds = currentFocus?.bounds,
                reason = selected?.reason ?: "",
                scope = currentFocus?.signature ?: "scene",
                confidence = currentFocus?.members?.minOfOrNull { it.confidence } ?: .65f,
            )
        if (misses > 0)
            return base.copy(
                key = "reacquire",
                text = "Hold briefly — following the suggested frame",
            )
        if (
            currentFocus != null &&
                choice !is SubjectChoice.Region &&
                CropProtection.competingOverlap(
                    currentFocus,
                    f.subjects.filter { it.confidence >= threshold },
                )
        )
            return base.copy(
                key = "separate",
                text = "Subjects overlap • try a clearer angle",
                reason = "Keep your selected subject distinct",
            )
        val dx = target.x - .5f
        val dy = target.y - .5f
        if (target.w > 1.13f)
            return base.copy(
                key = "zoom_out",
                text = "Zoom out slightly to fit the highlighted frame",
                zoom = zoom,
            )
        if (abs(dx) > .055f || abs(dy) > .055f) {
            val arrow =
                if (abs(dx) > abs(dy)) {
                    if (dx > 0) "→" else "←"
                } else {
                    if (dy > 0) "↓" else "↑"
                }
            return base.copy(
                key = "aim",
                text =
                    if (currentFocus == null) {
                        when (arrow) {
                            "→" -> "Aim right toward the highlighted area"
                            "←" -> "Aim left toward the highlighted area"
                            "↓" -> "Aim lower toward the highlighted area"
                            else -> "Aim higher toward the highlighted area"
                        } + " $arrow"
                    } else "Center the highlighted area $arrow",
                dx = dx,
                dy = dy,
                targetX = target.x,
                targetY = target.y,
            )
        }
        if (zoom > 1.13f)
            return base.copy(
                key = "zoom_in",
                text = "Zoom in about ${formatZoom(zoom)}× from this view",
            )
        if (f.motion > .055f) return base.copy(key = "steady", text = "Hold steady on this frame")
        val relevantFaces =
            if (currentFocus?.members?.any { it.face || it.label == "person" } == true)
                f.subjects.filter {
                    it.face &&
                        it.confidence >= threshold &&
                        overlap(it.box, currentFocus.bounds) /
                            (it.box.w * it.box.h).coerceAtLeast(.0001f) > .8f
                }
            else emptyList()
        val moment =
            when {
                relevantFaces.any { (it.eyes ?: 1f) < .35f } ->
                    "Eyes may be closed. Watch for the next expression."
                relevantFaces.any { (it.eyes ?: 0f) > .75f } -> "Open eyes detected."
                else -> null
            }
        return base.copy(
            key = "ready",
            text = "Ready to capture",
            reason =
                listOfNotNull(base.reason.takeIf { it.isNotBlank() }, moment).joinToString(" · "),
            level = 0,
            score = ((selected?.score ?: .5f) * 100).coerceIn(0f, 100f),
        )
    }

    companion object {
        fun formatZoom(value: Float) = String.format(java.util.Locale.US, "%.1f", value)
    }
}

/** Display-time interpolation, independent of inference rate. */
class SmoothFrame(private val timeConstant: Float = .18f) {
    private var value: Box? = null
    private var target: Box? = null
    private var last = 0L

    private var confidence = 1f

    fun target(box: Box?, reliability: Float = 1f) {
        confidence = reliability.coerceIn(0f, 1f)
        val old = target
        if (
            box != null &&
                old != null &&
                maxOf(
                    abs(box.l - old.l),
                    abs(box.t - old.t),
                    abs(box.r - old.r),
                    abs(box.b - old.b),
                ) < .006f + .005f * (1 - confidence)
        )
            return
        target = box
    }

    fun reset() {
        value = null
        target = null
        last = 0
    }

    fun sample(now: Long): Box? {
        val next = target ?: return null
        val current = value
        if (current == null) {
            value = next
            last = now
            return next
        }
        val dt = ((now - last).coerceIn(0, 80)) / 1000f
        last = now
        val alpha = (1 - exp(-dt / (timeConstant * (1f + .5f * (1 - confidence))))).coerceIn(0f, 1f)
        value = current.lerp(next, alpha)
        return value
    }
}
