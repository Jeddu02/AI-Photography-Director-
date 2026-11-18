package com.jade.ai_photography_director

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Applies the same grade as the viewfinder with a bounded cache of source scanlines. */
internal fun gradePhoto(source: Bitmap, target: Bitmap, look: CameraLook, matrix: FloatArray, matte: PortraitMatte?) {
    val grade = CreativeGrade(look)
    val width = source.width
    val height = source.height
    val span = minOf(width, height)
    val fine = (span * .0018f).roundToInt().coerceAtLeast(1)
    val wide = (span * .007f).roundToInt().coerceAtLeast(1)
    val blur = if (matte == null) 0 else (span * .016f * grade.blur).roundToInt()
    val rows = PhotoRows(source)
    val output = IntArray(width)
    val inverse = 1f / 255f
    fun lum(r: Float, g: Float, b: Float) = .2126f * r + .7152f * g + .0722f * b
    fun pixelLuminance(pixel: Int) = lum(red(pixel), green(pixel), blue(pixel))
    fun alpha(x: Int, y: Int): Float {
        val field = matte ?: return 0f
        val px = ((x + .5f) * field.side / width - .5f).coerceIn(0f, field.side - 1f)
        val py = ((y + .5f) * field.side / height - .5f).coerceIn(0f, field.side - 1f)
        val x0 = px.toInt()
        val y0 = py.toInt()
        val x1 = (x0 + 1).coerceAtMost(field.side - 1)
        val y1 = (y0 + 1).coerceAtMost(field.side - 1)
        val sx = px - x0
        val sy = py - y0
        val top = field.alpha[y0 * field.side + x0] * (1f - sx) + field.alpha[y0 * field.side + x1] * sx
        val bottom = field.alpha[y1 * field.side + x0] * (1f - sx) + field.alpha[y1 * field.side + x1] * sx
        return top * (1f - sy) + bottom * sy
    }
    val blurX = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)
    val blurY = intArrayOf(0, 0, 1, -1, 1, -1, -1, 1)
    val fineEnabled = grade.sharpness > 0f || grade.denoise > 0f
    val wideEnabled = grade.definition > 0f
    for (y in 0 until height) {
        val centerRow = rows.at(y)
        val fineUp = if (fineEnabled) rows.at(y - fine) else centerRow
        val fineDown = if (fineEnabled) rows.at(y + fine) else centerRow
        val wideUp = if (wideEnabled) rows.at(y - wide) else centerRow
        val wideDown = if (wideEnabled) rows.at(y + wide) else centerRow
        val blurRows = if (blur > 0) Array(blurY.size) { rows.at(y + blurY[it] * blur) } else null
        fun surround(x: Int, radius: Int, up: IntArray, down: IntArray): Float {
            val left = centerRow[(x - radius).coerceAtLeast(0)]
            val right = centerRow[(x + radius).coerceAtMost(width - 1)]
            return (pixelLuminance(left) + pixelLuminance(right) +
                pixelLuminance(up[x]) + pixelLuminance(down[x])) * .25f
        }
        for (x in 0 until width) {
            val pixel = centerRow[x]
            var r = red(pixel)
            var g = green(pixel)
            var b = blue(pixel)
            val center = lum(r, g, b)
            val near = if (fineEnabled) surround(x, fine, fineUp, fineDown) else center
            val broad = if (wideEnabled) surround(x, wide, wideUp, wideDown) else center
            var texture = grade.detail(center, near, broad)
            if (blurRows != null) {
                val background = 1f - alpha(x, y)
                val weight = background * background / 8f
                val baseR = r
                val baseG = g
                val baseB = b
                for (offset in blurX.indices) {
                    val sx = x + blurX[offset] * blur
                    val sy = y + blurY[offset] * blur
                    val neighbor = blurRows[offset][sx.coerceIn(0, width - 1)]
                    val w = weight * (1f - alpha(sx, sy))
                    r += w * (red(neighbor) - baseR)
                    g += w * (green(neighbor) - baseG)
                    b += w * (blue(neighbor) - baseB)
                }
                texture *= 1f - background * background
            }
            fun transform(row: Int) = (matrix[row] * r + matrix[row + 1] * g +
                matrix[row + 2] * b + matrix[row + 4] * inverse).coerceIn(0f, 1f)
            val outR = transform(0)
            val outG = transform(5)
            val outB = transform(10)
            val light = lum(outR, outG, outB)
            val color = grade.colorWeight(outR, outG, outB)
            val bias = light + grade.tone(light) + texture
            val edge = grade.edge((x + .5f) * 2 / width - 1, (y + .5f) * 2 / height - 1)
            output[x] = grade.pack(
                (bias + (outR - light) * color) * edge,
                (bias + (outG - light) * color) * edge,
                (bias + (outB - light) * color) * edge,
            )
        }
        target.setPixels(output, 0, width, 0, y, width, 1)
    }
}

private fun red(pixel: Int) = (pixel ushr 16 and 255) / 255f
private fun green(pixel: Int) = (pixel ushr 8 and 255) / 255f
private fun blue(pixel: Int) = (pixel and 255) / 255f

private class PhotoRows(private val source: Bitmap) {
    // At most seven distinct source rows are used by the three kernel radii.
    private val rows = object : LinkedHashMap<Int, IntArray>(12, .75f, true) {}
    fun at(y: Int): IntArray {
        val row = y.coerceIn(0, source.height - 1)
        return rows.getOrPut(row) {
            val buffer = if (rows.size < 9) IntArray(source.width) else {
                val oldest = rows.entries.iterator()
                oldest.next().value.also { oldest.remove() }
            }
            source.getPixels(buffer, 0, source.width, 0, row, source.width, 1)
            buffer
        }
    }
}
