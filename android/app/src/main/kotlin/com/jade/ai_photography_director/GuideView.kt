package com.jade.ai_photography_director

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.*

class GuideView(c: Context) : View(c) {
    var settings = DirectorSettings()
    var previewArea: Box? = null
    var showSubjectBadge = false
    private val smooth = SmoothFrame()
    private val subjectMotion = SmoothFrame()
    var advice = Advice("start", "Open your stock camera")
        set(value) {
            field = value
            smooth.target(value.frame, value.confidence)
            if (showSubjectBadge) subjectMotion.target(value.focusBounds, value.confidence)
            invalidate()
        }

    var subjects = emptyList<Subject>()
    var horizon: Float? = null
    var levelAngle: Float? = null
        set(value) {
            if (value == field || (value != null && field?.let { abs(value - it) < .35f } == true))
                return
            field = value
            invalidate()
        }

    var compact = false
    var manual = false
    var picking = false
    var targetSelection: Box? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewRect = RectF()
    private val subjectRect = RectF()
    private val cardRect = RectF()
    private val proposalRect = RectF()
    private val compositionGridRect = RectF()
    private var animatedColor = 0xFFF4D34E.toInt()
    private var lastDraw = 0L
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var cachedLayout: StaticLayout? = null
    private var cachedWords = ""
    private var cachedWidth = 0
    private var cachedSize = 0f
    private val yellow = Color.rgb(247, 207, 65)

    private fun instructionLayout(color: Int): StaticLayout {
        textPaint.color = color
        textPaint.textSize =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        textPaint.typeface = typeface
        val words =
            (if (manual) "◇ "
            else
                when (advice.level) {
                    0 -> "✓ "
                    2 -> "! "
                    else -> "↗ "
                }) + advice.text
        val displayWords = words
        val available = (width - 64).coerceAtLeast(1)
        if (
            cachedLayout == null ||
                displayWords != cachedWords ||
                available != cachedWidth ||
                cachedSize != textPaint.textSize
        ) {
            cachedLayout =
                StaticLayout.Builder.obtain(
                        displayWords,
                        0,
                        displayWords.length,
                        textPaint,
                        available,
                    )
                    .setIncludePad(false)
                    .build()
            cachedWords = displayWords
            cachedWidth = available
            cachedSize = textPaint.textSize
        }
        return cachedLayout!!
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (compact && !picking) return
        val roi = previewArea ?: settings.roi
        val showGrid = settings.grid && (manual || !settings.autoPreview || previewArea != null)
        val r =
            previewRect.apply { set(roi.l * width, roi.t * height, roi.r * width, roi.b * height) }
        if (picking) {
            paint.style = Paint.Style.STROKE
            paint.color = yellow
            paint.strokeWidth = 3f
            canvas.drawRect(r, paint)
            targetSelection?.let {
                val box = Coordinates.toDisplay(it, roi)
                canvas.drawRect(box.l * width, box.t * height, box.r * width, box.b * height, paint)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density * 1.2f
        val desiredColor =
            when (advice.level) {
                0 -> Color.rgb(128, 230, 148)
                2 -> Color.rgb(255, 120, 120)
                else -> yellow
            }
        val now = SystemClock.uptimeMillis()
        val dt = if (lastDraw == 0L) 16f else (now - lastDraw).coerceIn(0, 80).toFloat()
        lastDraw = now
        val blend = (1 - exp(-dt / 140f)).coerceIn(0f, 1f)
        fun channel(a: Int, b: Int) = (a + (b - a) * blend).toInt().coerceIn(0, 255)
        animatedColor =
            Color.rgb(
                channel(Color.red(animatedColor), Color.red(desiredColor)),
                channel(Color.green(animatedColor), Color.green(desiredColor)),
                channel(Color.blue(animatedColor), Color.blue(desiredColor)),
            )
        val color = animatedColor
        val shot = smooth.sample(now)
        if (showGrid && (shot == null || manual)) drawThirds(canvas, r)
        paint.color = color
        val trackedSubject = if (showSubjectBadge && !manual && advice.focusBounds != null) subjectMotion.sample(now) else null
        if (shot != null && !manual) {
            val left = (r.left + shot.l * r.width()).coerceIn(r.left, r.right)
            val top = (r.top + shot.t * r.height()).coerceIn(r.top, r.bottom)
            val right = (r.left + shot.r * r.width()).coerceIn(r.left, r.right)
            val bottom = (r.top + shot.b * r.height()).coerceIn(r.top, r.bottom)
            proposalRect.set(left, top, right, bottom)
            if (showGrid && !proposalRect.isEmpty) {
                compositionGridRect.set(
                    r.left + shot.l * r.width(),
                    r.top + shot.t * r.height(),
                    r.left + shot.r * r.width(),
                    r.top + shot.b * r.height(),
                )
                canvas.save()
                canvas.clipRect(r)
                drawThirds(canvas, compositionGridRect)
                canvas.restore()
                paint.alpha = 65
                canvas.drawRect(r, paint)
            }
            paint.color = color
            // Restrained corner brackets keep the photograph visible.
            val corner = min(proposalRect.width(), proposalRect.height()) * .12f
            paint.strokeWidth = resources.displayMetrics.density * 2.1f
            val breath =
                (.5f + .5f * sin(now / 1400.0 * Math.PI * 2).toFloat()) *
                    if (advice.level == 0) .2f else 1f
            paint.alpha = if (trackedSubject == null) (190 + breath * 55).toInt() else 0
            for (ix in 0..1) for (iy in 0..1) {
                val x = if (ix == 0) left else right
                val y = if (iy == 0) top else bottom
                val sx = if (x == left) 1 else -1
                val sy = if (y == top) 1 else -1
                canvas.drawLine(x, y, x + sx * corner, y, paint)
                canvas.drawLine(x, y, x, y + sy * corner, paint)
            }
            paint.alpha = if (trackedSubject != null) 0 else if (advice.level == 0) 40 else 130
            paint.strokeWidth = resources.displayMetrics.density * .8f
            canvas.drawRoundRect(proposalRect, 12f, 12f, paint)
            paint.alpha = if (advice.level == 0) 0 else 255
            val cx = (r.left + shot.x * r.width()).coerceIn(r.left + 20, r.right - 20)
            val cy = (r.top + shot.y * r.height()).coerceIn(r.top + 20, r.bottom - 20)
            val radius = (13 + breath * 1.5f) * resources.displayMetrics.density
            paint.strokeWidth = resources.displayMetrics.density * 1.4f
            canvas.drawCircle(cx, cy, radius, paint)
            canvas.drawLine(cx - 6, cy, cx + 6, cy, paint)
            canvas.drawLine(cx, cy - 6, cx, cy + 6, paint)
            if (advice.dx != 0f || advice.dy != 0f) {
                val startX = r.centerX()
                val startY = r.centerY()
                val dx = cx - startX
                val dy = cy - startY
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > radius * 2) {
                    val endX = cx - dx / distance * (radius + 8)
                    val endY = cy - dy / distance * (radius + 8)
                    canvas.drawLine(startX, startY, endX, endY, paint)
                    val angle = atan2(dy, dx)
                    val wing = 12 * resources.displayMetrics.density
                    canvas.drawLine(
                        endX,
                        endY,
                        endX - wing * cos(angle - .5f),
                        endY - wing * sin(angle - .5f),
                        paint,
                    )
                    canvas.drawLine(
                        endX,
                        endY,
                        endX - wing * cos(angle + .5f),
                        endY - wing * sin(angle + .5f),
                        paint,
                    )
                }
            }
            paint.alpha = 255
            val end = advice.frame
            val settled =
                end != null &&
                    maxOf(abs(shot.w - end.w), abs(shot.x - end.x), abs(shot.y - end.y)) < .002f
            val endSubject = advice.focusBounds
            val subjectMoving = trackedSubject != null && endSubject != null &&
                maxOf(abs(trackedSubject.w - endSubject.w), abs(trackedSubject.h - endSubject.h),
                    abs(trackedSubject.x - endSubject.x), abs(trackedSubject.y - endSubject.y)) > .02f
            if (advice.level != 0 || !settled || subjectMoving) {
                if (settings.preset == "Battery Saver") postInvalidateDelayed(33)
                else postInvalidateOnAnimation()
            }
        }
        if (trackedSubject != null) {
            val label = advice.focusLabel.orEmpty()
            drawSubjectBadge(canvas, r, trackedSubject, label, color)
        }
        if (shot == null && showGrid)
            for (s in subjects) {
                val b = s.box
                subjectRect.set(
                    (roi.l + b.l * roi.w) * width,
                    (roi.t + b.t * roi.h) * height,
                    (roi.l + b.r * roi.w) * width,
                    (roi.t + b.b * roi.h) * height,
                )
                canvas.drawRoundRect(subjectRect, 10f, 10f, paint)
            }
        if (manual || settings.mode == "Manual Composition") {
            paint.color = yellow
            val tx =
                when (settings.preference) {
                    "Left third" -> 1f / 3
                    "Right third" -> 2f / 3
                    else -> .5f
                }
            canvas.drawCircle(
                r.left + tx * r.width(),
                r.top + r.height() * (if (settings.preference == "Lower third") 2f / 3 else 1f / 3),
                22f,
                paint,
            )
        }
        if (shot == null && showGrid && subjects.isNotEmpty() && !manual) {
            val x = r.left + advice.targetX * r.width()
            val y = r.top + advice.targetY * r.height()
            canvas.drawCircle(x, y, 18f, paint)
            if (abs(advice.dx) + abs(advice.dy) > 0) {
                val len = 48 * resources.displayMetrics.density
                val dx = advice.dx.sign * len
                val dy = advice.dy.sign * len
                val a = atan2(dy, dx)
                canvas.drawLine(x - dx, y - dy, x, y, paint)
                canvas.drawLine(x, y, x - 20 * cos(a - .6f), y - 20 * sin(a - .6f), paint)
                canvas.drawLine(x, y, x - 20 * cos(a + .6f), y - 20 * sin(a + .6f), paint)
            }
        }
        if (shot == null && showGrid && settings.mode == "Landscape") {
            paint.color = yellow
            val cx = r.centerX()
            val cy = r.centerY()
            val len = r.width() * .3f
            canvas.drawLine(cx - len, cy, cx + len, cy, paint)
            horizon?.let {
                val dy = tan(Math.toRadians(it.toDouble())).toFloat() * len
                paint.color = color
                canvas.drawLine(cx - len, cy - dy, cx + len, cy + dy, paint)
            }
        }
        levelAngle?.let { angle ->
            val centerX = r.centerX()
            val centerY = r.top + r.height() * .11f
            val length = r.width() * .10f
            val line = resources.displayMetrics.density
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = line * 3
            paint.color = Color.argb(200, 20, 22, 24)
            canvas.drawLine(centerX - length, centerY, centerX + length, centerY, paint)
            paint.strokeWidth = line * 1.5f
            paint.color = Color.WHITE
            canvas.drawLine(centerX - length, centerY, centerX + length, centerY, paint)
            canvas.save()
            canvas.rotate(angle, centerX, centerY)
            paint.color = if (abs(angle) <= 2f) Color.rgb(128, 230, 148) else yellow
            canvas.drawLine(
                centerX - length * .72f,
                centerY,
                centerX + length * .72f,
                centerY,
                paint,
            )
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
        val layout = instructionLayout(color)
        val cardHeight = layout.height + 32f
        val cardTop = min(r.bottom + 12f, height - cardHeight - 16f).coerceAtLeast(16f)
        cardRect.set(16f, cardTop, width - 16f, cardTop + cardHeight)
        paint.color = Color.rgb(19, 21, 23)
        canvas.drawRoundRect(cardRect, 20f, 20f, paint)
        canvas.save()
        canvas.translate(32f, cardTop + 16f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawThirds(canvas: Canvas, bounds: RectF) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density * .85f
        paint.color = Color.argb(115, 235, 235, 225)
        for (i in 1..2) {
            val x = bounds.left + bounds.width() * i / 3
            val y = bounds.top + bounds.height() * i / 3
            canvas.drawLine(x, bounds.top, x, bounds.bottom, paint)
            canvas.drawLine(bounds.left, y, bounds.right, y, paint)
        }
        canvas.drawRect(bounds, paint)
    }

    private fun drawSubjectBadge(canvas: Canvas, preview: RectF, box: Box, label: String, color: Int) {
        if (label.isBlank()) return
        val density = resources.displayMetrics.density
        val bounds = subjectRect.apply {
            set(
                preview.left + box.l * preview.width(), preview.top + box.t * preview.height(),
                preview.left + box.r * preview.width(), preview.top + box.b * preview.height(),
            )
        }
        if (!bounds.intersect(preview) || bounds.width() < 2f || bounds.height() < 2f) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 1.7f
        paint.color = color
        canvas.drawRoundRect(bounds, density * 9, density * 9, paint)
        val heading = label.replaceFirstChar { it.uppercaseChar() }
        val caption = heading + if (advice.key == "ready") "  ·  Ready" else ""
        paint.style = Paint.Style.FILL
        textPaint.typeface = typeface
        textPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        val padding = density * 8f
        val maxTextWidth = preview.width() - padding * 2
        if (maxTextWidth <= 0f) return
        val words = android.text.TextUtils.ellipsize(caption, textPaint, maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()
        val badgeWidth = textPaint.measureText(words) + padding * 2
        val badgeHeight = maxOf(density * 24f, textPaint.descent() - textPaint.ascent() + density * 6f)
        val left = (bounds.centerX() - badgeWidth / 2f).coerceIn(preview.left, maxOf(preview.left, preview.right - badgeWidth))
        val top = (bounds.top - badgeHeight - density * 4f).coerceAtLeast(preview.top)
        paint.color = Color.argb(215, 16, 18, 23)
        canvas.drawRoundRect(left, top, left + badgeWidth, top + badgeHeight, badgeHeight / 2f, badgeHeight / 2f, paint)
        textPaint.color = color
        canvas.drawText(words, left + padding, top + (badgeHeight - textPaint.ascent() - textPaint.descent()) / 2f, textPaint)
    }

    fun clear(text: String) {
        subjects = emptyList()
        horizon = null
        smooth.reset()
        subjectMotion.reset()
        lastDraw = 0L
        advice = Advice("status", text)
        invalidate()
    }
}
