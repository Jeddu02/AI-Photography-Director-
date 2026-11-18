package com.jade.ai_photography_director

import android.content.SharedPreferences
import org.json.JSONObject

internal data class SavedLook(val look: CameraLook, val lightingStrength: Int) {
    fun encode() = JSONObject().apply {
        put("film", look.film.name)
        put("lighting", look.light.name)
        put("saturation", look.saturation)
        put("tone", look.tone)
        put("warmth", look.warmth)
        put("intensity", look.intensity)
        put("blur", look.blur)
        put("lightingStrength", lightingStrength)
        put("adjustments", JSONObject().apply {
            look.adjustments.forEach { (control, value) -> put(control.name, value) }
        })
    }

    companion object {
        fun decode(json: JSONObject): SavedLook {
            val values = json.optJSONObject("adjustments") ?: JSONObject()
            var look = CameraLook(
                film = Film.entries.find { it.name == json.optString("film") } ?: Film.STANDARD,
                light = Lighting.entries.find { it.name == json.optString("lighting") } ?: Lighting.NATURAL,
                saturation = json.optInt("saturation", 100).coerceIn(0, 200),
                tone = json.optInt("tone", 100).coerceIn(50, 150),
                warmth = json.optInt("warmth").coerceIn(-30, 30),
                intensity = json.optInt("intensity", 100).coerceIn(0, 100),
                blur = json.optInt("blur").coerceIn(0, 100),
            )
            Adjustment.entries.forEach { look = look.adjusted(it, values.optInt(it.name)) }
            return SavedLook(look, json.optInt("lightingStrength", 70).coerceIn(0, 100))
        }
    }
}

internal fun SharedPreferences.readCreativeLook(): SavedLook {
    val current = getString("creativeLook", null)
    if (current != null) runCatching { SavedLook.decode(JSONObject(current)) }.getOrNull()?.let { return it }
    return SavedLook.decode(JSONObject().apply {
        put("film", getString("film", "STANDARD"))
        put("lighting", getString("lighting", "NATURAL"))
        put("saturation", getInt("sat", 100))
        put("tone", getInt("tone", 100))
        put("warmth", getInt("warmth", 0))
        put("lightingStrength", getInt("portraitIntensity", 70))
    })
}
