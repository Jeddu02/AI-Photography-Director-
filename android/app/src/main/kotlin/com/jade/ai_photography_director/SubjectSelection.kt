package com.jade.ai_photography_director

import kotlin.math.*

/** Session-only selection; tracker IDs are never identities or persisted preferences. */
sealed class SubjectChoice {
    object Automatic : SubjectChoice()

    object WholeScene : SubjectChoice()

    data class Region(val bounds: Box) : SubjectChoice()

    data class Detected(val id: Int, val label: String) : SubjectChoice()
}

data class SubjectOption(val subject: Subject, val title: String)

object SubjectMenu {
    fun candidates(subjects: List<Subject>, threshold: Float): List<SubjectOption> {
        val accepted =
            subjects.filter { it.confidence >= threshold && it.box.w > 0 && it.box.h > 0 }
        val people = accepted.filter { it.label == "person" }
        return accepted
            .filter { s ->
                !s.face || people.none { overlap(s.box, it.box) / (s.box.w * s.box.h) > .8f }
            }
            .sortedWith(
                compareByDescending<Subject> { it.label == "person" }
                    .thenByDescending { it.box.w * it.box.h * it.confidence }
            )
            .take(4)
            .map { s ->
                val horizontal =
                    when {
                        s.box.x < .36f -> "left"
                        s.box.x > .64f -> "right"
                        else -> "center"
                    }
                val vertical =
                    when {
                        s.box.y < .36f -> "top"
                        s.box.y > .64f -> "bottom"
                        else -> "middle"
                    }
                SubjectOption(
                    s,
                    "${s.label.replaceFirstChar { it.uppercaseChar() }} · $horizontal / $vertical",
                )
            }
    }

    fun isAvailable(choice: SubjectChoice.Detected, subjects: List<Subject>, threshold: Float) =
        subjects.any {
            it.id == choice.id && it.label == choice.label && it.confidence >= threshold
        }
}

fun overlap(a: Box, b: Box) =
    max(0f, min(a.r, b.r) - max(a.l, b.l)) * max(0f, min(a.b, b.b) - max(a.t, b.t))

object CropProtection {
    fun margin(box: Box, frame: Box) =
        min(min(box.l - frame.l, frame.r - box.r), min(box.t - frame.t, frame.b - box.b)) / frame.w

    fun accepts(frame: Box, focus: Focus?, subjects: List<Subject>): Boolean {
        if (focus != null) {
            val box = focus.bounds
            if (overlap(box, frame) / (box.w * box.h).coerceAtLeast(.0001f) < .995f) return false
            val needed = if (focus.members.any { it.label == "person" }) .06f else .045f
            if (frame.w < .99f && margin(box, frame) < needed) return false
        }
        // A tighter proposal must not cut through another detected person, even if not selected.
        if (frame.w < .99f)
            for (s in subjects.filter { it.label == "person" }) {
                val fraction = overlap(s.box, frame) / (s.box.w * s.box.h).coerceAtLeast(.0001f)
                if (fraction > .08f && fraction < .995f) return false
            }
        return true
    }

    /** Box overlap only: does not infer depth, foreground order or semantic obstruction. */
    fun competingOverlap(focus: Focus, subjects: List<Subject>): Boolean {
        val protectedIds = focus.members.map { it.id }.toSet()
        return subjects.any { s ->
            !s.face &&
                s.id !in protectedIds &&
                s.box.w * s.box.h > focus.bounds.w * focus.bounds.h * .1f &&
                overlap(s.box, focus.bounds) /
                    (focus.bounds.w * focus.bounds.h).coerceAtLeast(.0001f) > .28f
        }
    }
}

/** Rectangle in calibrated preview coordinates. A tap gets a small bounded target. */
internal fun targetBounds(start: Pair<Float, Float>, end: Pair<Float, Float>): Box {
    fun span(a: Float, b: Float): Pair<Float, Float> {
        val left = min(a, b).coerceIn(0f, 1f)
        val right = max(a, b).coerceIn(0f, 1f)
        if (right - left >= .045f) return left to right
        val center = ((left + right) / 2).coerceIn(.055f, .945f)
        return center - .055f to center + .055f
    }
    val (l, r) = span(start.first, end.first)
    val (t, b) = span(start.second, end.second)
    return Box(l, t, r, b)
}
