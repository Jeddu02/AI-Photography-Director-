package com.jade.ai_photography_director

import kotlin.math.*

/** Finds the photographic band between comparatively uniform camera controls. */
internal object PreviewCalibration {
    fun detect(pixels: IntArray, width: Int, height: Int, screenRatio: Float): Box? {
        val portrait = screenRatio <= 1f
        val length = if (portrait) height else width
        val across = if (portrait) width else height
        val margin = across / 10
        val strength = FloatArray(length)
        val brightness = FloatArray(length)
        for (a in 0 until length) {
            var sum = 0f
            var square = 0f
            var nearby = 0f
            var count = 0
            for (b in margin until across - margin) {
                val index = if (portrait) a * width + b else b * width + a
                val y = luma(pixels[index])
                sum += y
                square += y * y
                if (b > margin) nearby += abs(y - luma(pixels[index - if (portrait) 1 else width]))
                count++
            }
            val mean = sum / count
            val spread = sqrt((square / count - mean * mean).coerceAtLeast(0f))
            brightness[a] = mean
            strength[a] = spread + .45f * nearby / count
        }

        fun average(values: FloatArray, start: Int, end: Int): Float {
            if (start >= end) return 0f
            var sum = 0f
            for (i in start until end) sum += values[i]
            return sum / (end - start)
        }

        val shortSide = min(screenRatio, 1 / screenRatio)
        val candidates = listOf(1f, 4f / 3, 3f / 2, 16f / 9, 2f, 2.2f)
        val edge = max(3, length / 32)
        var bestScore = .075f
        var best: Box? = null
        for (start in 0..(length * .40f).toInt()) {
            for (end in max(start + length / 3, (length * .60f).toInt())..length) {
                val fraction = (end - start).toFloat() / length
                val aspect = fraction / shortSide
                val aspectError = candidates.minOf { abs(it - aspect) / it }
                if (fraction < .96f && aspectError > .11f) continue
                val inside = average(strength, start, end)
                val light = average(brightness, start, end)
                if (inside < .039f || light < .07f || light > .96f) continue
                val before = average(strength, 0, start)
                val after = average(strength, end, length)
                val outside =
                    (before * start + after * (length - end)) /
                        (start + length - end).coerceAtLeast(1)
                val leading =
                    if (start < edge) 0f
                    else boundary(strength, brightness, start, edge, insideAfter = true)
                val trailing =
                    if (end > length - edge) 0f
                    else boundary(strength, brightness, end, edge, insideAfter = false)
                val full = start <= 1 && end >= length - 1
                if (full) {
                    if (
                        average(strength, 0, edge) < .055f ||
                            average(strength, length - edge, length) < .055f ||
                            strength.count { it < .038f } > length / 12
                    )
                        continue
                } else {
                    if (start >= edge && leading < .055f) continue
                    if (end <= length - edge && trailing < .055f) continue
                    if (leading + trailing < .09f || outside > inside * .70f) continue
                }
                val score =
                    if (full) .076f
                    else
                        .40f * (leading + trailing) + .7f * (inside - outside) + .06f * fraction -
                            .25f * aspectError
                if (score <= bestScore) continue
                bestScore = score
                val a = if (start < edge) 0f else start.toFloat() / length
                val b = if (end > length - edge) 1f else end.toFloat() / length
                best = if (portrait) Box(0f, a, 1f, b) else Box(a, 0f, b, 1f)
            }
        }
        return best
    }

    private fun boundary(
        spread: FloatArray,
        luma: FloatArray,
        at: Int,
        radius: Int,
        insideAfter: Boolean,
    ): Float {
        var strength = 0f
        var light = 0f
        for (i in 0 until radius) {
            val before = at - i - 1
            val after = at + i
            strength += spread[after] - spread[before]
            light += luma[after] - luma[before]
        }
        return ((if (insideAfter) strength else -strength) + .24f * abs(light)) / radius
    }

    private fun luma(c: Int): Float =
        (.2126f * (c ushr 16 and 255) + .7152f * (c ushr 8 and 255) + .0722f * (c and 255)) / 255f
}

/** Treat a layout change as uncalibrated until confirmed by the next observation. */
internal class PreviewCalibrationState {
    private var settled: Box? = null
    private var pending: Box? = null

    fun update(candidate: Box?): Box? {
        if (candidate == null) {
            pending = null
            return null
        }
        if (settled?.let { close(it, candidate) } == true) {
            pending = null
            return settled
        }
        if (pending?.let { close(it, candidate) } == true) {
            settled = candidate
            pending = null
            return settled
        }
        pending = candidate
        return null
    }

    fun reset() {
        settled = null
        pending = null
    }

    private fun close(a: Box, b: Box) =
        maxOf(abs(a.l - b.l), abs(a.t - b.t), abs(a.r - b.r), abs(a.b - b.b)) <= .024f
}
