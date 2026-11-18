package com.jade.ai_photography_director

import android.content.Context
import org.json.JSONObject

data class DirectorSettings(
    val mode: String = "Auto Director",
    val grid: Boolean = false,
    val opacity: Float = .5f,
    val threshold: Float = .55f,
    val preset: String = "Balanced",
    val preference: String = "Nearest third",
    val top: Float = .14f,
    val bottom: Float = .28f,
    val left: Float = .02f,
    val right: Float = .02f,
    val autoPreview: Boolean = true,
) {
    val roi = Box(left, top, 1 - right, 1 - bottom)
    val interval
        get() =
            when (preset) {
                "Battery Saver" -> 1000L
                "High Responsiveness" -> 220L
                else -> 450L
            }

    fun map() =
        mapOf(
            "mode" to mode,
            "grid" to grid,
            "opacity" to opacity.toDouble(),
            "threshold" to threshold.toDouble(),
            "preset" to preset,
            "preference" to preference,
            "top" to top.toDouble(),
            "bottom" to bottom.toDouble(),
            "left" to left.toDouble(),
            "right" to right.toDouble(),
            "autoPreview" to autoPreview,
        )

    fun save(c: Context) {
        c.getSharedPreferences("director", 0)
            .edit()
            .putString("settings", JSONObject(map()).toString())
            .apply()
    }

    companion object {
        val modes =
            listOf(
                "Auto Director",
                "Portrait",
                "Group Photo",
                "Landscape",
                "Food",
                "Street",
                "Manual Composition",
            )

        fun load(c: Context): DirectorSettings {
            val prefs = c.getSharedPreferences("director", 0)
            val old =
                try {
                    JSONObject(prefs.getString("settings", "{}")!!)
                } catch (_: Exception) {
                    JSONObject()
                }
            if (prefs.getInt("settingsSchema", 0) < 2) {
                // v0.2 switches automatic guidance to a minimal shot frame; keep calibration and
                // other choices.
                old.put("grid", false)
                prefs
                    .edit()
                    .putString("settings", old.toString())
                    .putInt("settingsSchema", 2)
                    .apply()
            }
            return from(old)
        }

        fun from(j: JSONObject) =
            DirectorSettings(
                j.optString("mode", "Auto Director").takeIf { it in modes } ?: "Auto Director",
                j.optBoolean("grid", false),
                j.optDouble("opacity", .5).toFloat().coerceIn(.2f, .65f),
                j.optDouble("threshold", .55).toFloat().coerceIn(.4f, .9f),
                j.optString("preset", "Balanced").takeIf {
                    it in listOf("Battery Saver", "Balanced", "High Responsiveness")
                } ?: "Balanced",
                j.optString("preference", "Nearest third").takeIf {
                    it in
                        listOf(
                            "Nearest third",
                            "Center",
                            "Left third",
                            "Right third",
                            "Upper third",
                            "Lower third",
                            "Nearest intersection",
                        )
                } ?: "Nearest third",
                j.optDouble("top", .14).toFloat().coerceIn(0f, .3f),
                j.optDouble("bottom", .28).toFloat().coerceIn(0f, .4f),
                j.optDouble("left", .02).toFloat().coerceIn(0f, .4f),
                j.optDouble("right", .02).toFloat().coerceIn(0f, .4f),
                j.optBoolean("autoPreview", true),
            )
    }
}
