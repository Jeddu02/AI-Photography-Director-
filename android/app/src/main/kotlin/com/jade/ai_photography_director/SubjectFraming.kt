package com.jade.ai_photography_director

import kotlin.math.abs

/** Scores composition in the proposed crop's coordinate space. */
internal class SubjectFraming(
    private val focus: Focus,
    detections: List<Subject>,
    mode: String,
    private val preference: String,
) {
    private val group = focus.label == "group" || mode == "Group Photo"
    private val face =
        if (!group && focus.members.any { it.face || it.label == "person" })
            detections
                .filter { it.face && overlap(it.box, focus.bounds) >= .8f * it.box.w * it.box.h }
                .maxByOrNull { it.box.w * it.box.h }
        else null
    // ML Kit yaw is in image coordinates. Positive means facing image-right.
    private val facing = (face?.yaw ?: 0f).takeIf { abs(it) in 15f..60f }
    private val headroom = if (focus.members.any { it.label == "person" }) .11f else .17f
    private val desiredHeight =
        when {
            group -> .64f
            focus.members.any { it.face } -> .42f
            mode == "Portrait" -> .70f
            mode == "Food" -> .64f
            else -> .58f
        }

    val anchorX = face?.box?.x ?: focus.bounds.x
    val targetX =
        when (preference) {
            "Center" -> .5f
            "Left third" -> 1f / 3
            "Right third" -> 2f / 3
            else ->
                when {
                    group -> .5f
                    facing != null -> if (facing > 0f) 1f / 3 else 2f / 3
                    anchorX <= .5f -> 1f / 3
                    else -> 2f / 3
                }
        }

    private val anchorY = face?.box?.y ?: focus.bounds.y
    private val targetY =
        when (preference) {
            "Upper third" -> 1f / 3
            "Lower third" -> 2f / 3
            "Nearest intersection" -> if (anchorY < .5f) 1f / 3 else 2f / 3
            else -> null
        }

    fun preferredTop(size: Float): Float =
        targetY?.let { anchorY - it * size }
            ?: face?.let { it.box.t - headroom * size }
            ?: (focus.bounds.y - .5f * size)

    fun score(frame: Box): Float {
        val fill =
            (1 - abs(focus.bounds.h / frame.h - desiredHeight) / desiredHeight).coerceIn(0f, 1f)
        val x = (anchorX - frame.l) / frame.w
        val placement = (1 - abs(x - targetX) * 2).coerceIn(0f, 1f)
        val top = face?.let { (it.box.t - frame.t) / frame.h }
        val headroomFit = top?.let { (1 - abs(it - headroom) / .23f).coerceIn(0f, 1f) } ?: 0f
        val lookingRoom =
            if (facing != null) {
                val space = if (facing > 0f) frame.r - focus.bounds.r else focus.bounds.l - frame.l
                (space / frame.w / .30f).coerceIn(0f, 1f)
            } else 0f
        val verticalFit =
            targetY?.let { (1 - 2 * abs((anchorY - frame.t) / frame.h - it)).coerceIn(0f, 1f) }
                ?: 0f
        return .24f * verticalFit +
            .90f * fill +
            .32f * placement +
            .12f * headroomFit +
            .13f * lookingRoom
    }

    fun reason(frame: Box): String {
        val x = (anchorX - frame.l) / frame.w
        if (targetY != null && abs((anchorY - frame.t) / frame.h - targetY) <= .07f)
            return if (targetY < .5f) "Keep the subject on the upper third"
            else "Keep the subject on the lower third"
        if (abs(x - targetX) <= .07f) {
            if (targetX == .5f) return "Keep the subject centered"
            if (facing != null && preference == "Nearest third")
                return "Leave room in the direction they face"
            return if (targetX < .5f) "Place the subject on the left third"
            else "Place the subject on the right third"
        }
        if (face != null && abs((face.box.t - frame.t) / frame.h - headroom) <= .06f)
            return "Leave space above the head"
        return "Give ${focus.label} more of the frame"
    }
}
