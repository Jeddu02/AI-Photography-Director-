package com.jade.ai_photography_director

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

internal class CreativeControls(
    private val activity: AiCameraActivity,
    private val current: () -> SavedLook,
    private val change: (SavedLook, Boolean) -> Unit,
    private val close: () -> Unit,
    private val recommendation: () -> Unit,
    private val tonePad: () -> Unit,
    private val thumbnail: Bitmap?,
    private val portraitStatus: () -> String,
    private val stills: Boolean,
    private val advanced: Boolean,
) : LinearLayout(activity) {
    private val gold = 0xfff7cf41.toInt()
    private val glass = 0xc6242933.toInt()
    private val glassSelected = 0xa66c572c.toInt()
    private val body = LinearLayout(activity).apply { orientation = VERTICAL }
    private val tabs = LinearLayout(activity)
    private val actions = LinearLayout(activity)
    private var styleControl = 0
    private var page = 0
    private var control = Adjustment.EXPOSURE
    private var portraitBlur = false

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = surface(0xd71b202a.toInt(), 24)
        isClickable = true
        addView(tabs)
        addView(body)
        addView(actions)
        render()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()

    private fun surface(color: Int, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(color, 0xd0161a22.toInt())
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0x55ffffff)
    }

    private fun button(label: String, selected: Boolean = false, action: () -> Unit) = Button(activity).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        isSelected = selected
        gravity = Gravity.CENTER
        setTextColor(if (selected) gold else Color.WHITE)
        setPadding(dp(8), 0, dp(8), 0)
        minimumHeight = dp(48)
        minHeight = dp(48)
        minimumWidth = dp(48)
        minWidth = dp(48)
        background = surface(if (selected) glassSelected else glass, 16)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            action()
        }
    }

    private fun LinearLayout.weighted(view: View) {
        addView(view, LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
    }

    private fun scroll(choices: LinearLayout) = HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled = false
        addView(choices)
        body.addView(this)
    }

    private fun update(persist: Boolean = true, transform: (CameraLook) -> CameraLook) {
        val value = current()
        change(value.copy(look = transform(value.look)), persist)
    }

    private fun render() {
        tabs.removeAllViews()
        body.removeAllViews()
        actions.removeAllViews()

        val heading = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(2), dp(10))
        }
        val title = LinearLayout(activity).apply { orientation = VERTICAL }
        title.addView(TextView(activity).apply {
            text = "CREATIVE STUDIO"
            textSize = 11f
            letterSpacing = .16f
            setTextColor(gold)
        })
        title.addView(TextView(activity).apply {
            text = "Filters & effects"
            textSize = 20f
            setTextColor(Color.WHITE)
        })
        heading.addView(title, LayoutParams(0, -2, 1f))
        heading.addView(button("✕", action = close).apply {
            contentDescription = "Close filters and effects"
        }, LayoutParams(dp(48), dp(48)))
        tabs.addView(heading)

        val navigation = LinearLayout(activity).apply {
            setPadding(0, 0, 0, dp(8))
        }
        listOf("FILTERS", "PORTRAIT", "ADJUST").forEachIndexed { i, name ->
            navigation.weighted(button(name, i == page) { page = i; render() }.apply {
                isEnabled = i != 1 || stills
                if (!isEnabled) contentDescription = "Portrait lighting available in photo modes"
            })
        }
        tabs.addView(navigation)

        when (page) {
            0 -> styles()
            1 -> portrait()
            else -> adjust()
        }

        val footer = LinearLayout(activity).apply {
            setPadding(0, dp(8), 0, 0)
        }
        if (stills) footer.weighted(button("AI Suggest") { recommendation() })
        footer.weighted(button("Presets") { presets() })
        footer.weighted(button("Reset all") {
            change(SavedLook(CameraLook(), 70), true)
            render()
        })
        actions.addView(footer)
    }

    private fun styles() {
        val strip = LinearLayout(activity)
        val scroll = scroll(strip)
        val look = current().look
        val sample = thumbnail ?: referenceSwatch()
        Film.entries.forEach { film ->
            val selected = film == look.film
            val card = LinearLayout(activity).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), 0)
                background = surface(if (selected) glassSelected else glass, 14)
                contentDescription = if (selected) "${film.label}, selected" else film.label
                isSelected = selected
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    update { it.copy(film = film) }
                    render()
                }
            }
            card.addView(ImageView(activity).apply {
                setImageBitmap(sample)
                scaleType = ImageView.ScaleType.CENTER_CROP
                colorFilter = ColorMatrixColorFilter(CameraLook(film = film).matrix())
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, dp(86), dp(55))
            card.addView(TextView(activity).apply {
                text = film.label
                textSize = 11f
                setTextColor(if (selected) gold else Color.WHITE)
                gravity = Gravity.CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, dp(96), dp(30))
            strip.addView(card, LayoutParams(dp(102), dp(98)).apply { marginEnd = dp(8) })
            if (selected) scroll.post { scroll.scrollTo((card.left - scroll.width / 3).coerceAtLeast(0), 0) }
        }
        val values = LinearLayout(activity)
        val categories = listOf("Strength", "Tone", "Color", "Warmth")
        categories.forEachIndexed { i, title ->
            values.weighted(button(title, i == styleControl) { styleControl = i; render() })
        }
        // The tone pad remains available as a dedicated adjustment rather than crowding the filter row.
        body.addView(values)
        when (styleControl) {
            0 -> slider("Intensity", 0, 100, look.intensity, 100) { n, save -> update(save) { it.copy(intensity = n) } }
            1 -> slider("Tone", 50, 150, look.tone, 100) { n, save -> update(save) { it.copy(tone = n) } }
            2 -> slider("Color", 0, 200, look.saturation, 100) { n, save -> update(save) { it.copy(saturation = n) } }
            3 -> slider("Warmth", -30, 30, look.warmth, 0) { n, save -> update(save) { it.copy(warmth = n) } }
        }
    }

    private fun referenceSwatch(): Bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888).apply {
        val colors = intArrayOf(0xff455b75.toInt(), 0xff897366.toInt(), 0xffbd9983.toInt(),
            0xffe3c9a5.toInt(), 0xff365f50.toInt(), 0xff554d65.toInt())
        val canvas = Canvas(this)
        val paint = Paint()
        colors.forEachIndexed { i, color ->
            paint.color = color
            val x = (i % 3) * 4
            val y = (i / 3) * 4
            canvas.drawRect(Rect(x, y, x + 4, y + 4), paint)
        }
    }

    private fun portrait() {
        val selected = current()
        val strip = LinearLayout(activity)
        val scroll = scroll(strip)
        Lighting.entries.forEach { light ->
            val choice = button(light.label, light == selected.look.light) {
                update { it.copy(light = light) }
                render()
            }
            strip.addView(choice, LayoutParams(-2, dp(48)).apply { marginEnd = dp(4) })
            if (light == selected.look.light) scroll.post { scroll.scrollTo(choice.left, 0) }
        }
        val rows = LinearLayout(activity)
        val natural = selected.look.light == Lighting.NATURAL
        rows.weighted(button("Light strength", !portraitBlur && !natural) { portraitBlur = false; render() }.apply {
            isEnabled = !natural
            if (natural) contentDescription = "Natural Light preserves the original lighting"
        })
        rows.weighted(button("Background blur", portraitBlur) { portraitBlur = true; render() }.apply {
            isEnabled = advanced
            if (!advanced) contentDescription = "Preview blur requires Android 13 or later and GPU rendering"
        })
        body.addView(rows)
        body.addView(TextView(activity).apply {
            text = portraitStatus()
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            val label = this
            val update = object : Runnable {
                override fun run() {
                    if (!label.isAttachedToWindow) return
                    val currentStatus = portraitStatus()
                    if (label.text != currentStatus) label.text = currentStatus
                    label.postDelayed(this, 700L)
                }
            }
            addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) { label.post(update) }
                override fun onViewDetachedFromWindow(view: View) { label.removeCallbacks(update) }
            })
        })
        if ((portraitBlur || natural) && advanced) {
            slider("Blur", 0, 100, selected.look.blur, 0) { n, save -> update(save) { it.copy(blur = n) } }
        } else if (!natural) {
            slider("Intensity", 0, 100, selected.lightingStrength, 70) { n, save ->
                change(current().copy(lightingStrength = n), save)
            }
        }
    }

    private fun adjust() {
        val strip = LinearLayout(activity)
        val scroll = scroll(strip)
        Adjustment.entries.forEach { item ->
            val choice = button(item.label, item == control) { control = item; render() }
            strip.addView(choice, LayoutParams(-2, dp(48)).apply { marginEnd = dp(4) })
            if (item == control) scroll.post { scroll.scrollTo((choice.left - scroll.width / 4).coerceAtLeast(0), 0) }
        }
        val selected = control
        if (selected.advanced && !advanced) {
            body.addView(TextView(activity).apply {
                text = "Live ${selected.label.lowercase()} requires a compatible Android 13+ photo preview."
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }, LayoutParams(-1, dp(52)))
        } else {
            slider(selected.label, selected.minimum, 100, current().look.value(selected), 0) { n, save ->
                update(save) { it.adjusted(selected, n) }
            }
        }
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, default: Int, set: (Int, Boolean) -> Unit) {
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val caption = TextView(activity).apply { setTextColor(Color.WHITE); textSize = 12f }
        fun show(n: Int) { caption.text = "$label  $n" }
        show(value)
        val bar = SeekBar(activity).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, this.max)
            contentDescription = label
            progressTintList = ColorStateList.valueOf(gold)
            thumbTintList = ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                private var dragging = false
                override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(bar: SeekBar) { dragging = false; set(min + bar.progress, true) }
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        show(min + progress)
                        set(min + progress, !dragging)
                    }
                }
            })
        }
        val stack = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(caption, LayoutParams(0, dp(38), 1f))
        header.addView(button("Reset") {
            set(default, true)
            bar.progress = default - min
            show(default)
        }.apply { contentDescription = "Reset $label" }, LayoutParams(dp(80), dp(42)))
        stack.addView(header)
        stack.addView(bar, LayoutParams(-1, dp(48)))
        body.addView(stack)
    }

    private fun presets() {
        val prefs = activity.getSharedPreferences("creative_presets", 0)
        val names = prefs.all.keys.sorted()
        val options = listOf("Save current look") + names
        AlertDialog.Builder(activity).setTitle("Creative presets").setItems(options.toTypedArray()) { _, index ->
            if (index == 0) {
                val input = EditText(activity).apply {
                    hint = "Preset name"
                    isSingleLine = true
                    filters = arrayOf(android.text.InputFilter.LengthFilter(32))
                }
                AlertDialog.Builder(activity).setTitle("Save creative preset").setView(input)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) prefs.edit().putString(name, current().encode().toString()).apply()
                    }.show()
            } else {
                val json = prefs.getString(names[index - 1], null) ?: return@setItems
                runCatching { SavedLook.decode(org.json.JSONObject(json)) }.getOrNull()?.let {
                    change(it, true)
                    render()
                }
            }
        }.setNegativeButton("Close", null).show()
    }
}
