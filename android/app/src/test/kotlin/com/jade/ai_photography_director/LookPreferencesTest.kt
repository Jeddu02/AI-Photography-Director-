package com.jade.ai_photography_director

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LookPreferencesTest {
    @Test fun oldLookRetainsItsFilmAndManualSettings() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("legacy_look_test", 0)
        prefs.edit().putString("film", "RICH").putString("lighting", "CONTOUR")
            .putInt("sat", 120).putInt("tone", 112).putInt("warmth", -9).putInt("portraitIntensity", 39).commit()
        assertEquals(SavedLook(CameraLook(Film.RICH, Lighting.CONTOUR, 120, 112, -9), 39), prefs.readCreativeLook())
    }

    @Test fun presetRoundTripsAllLayers() {
        val photo = CameraLook(Film.COOL_ROSE, Lighting.STAGE_MONO, intensity = 82, blur = 32)
            .adjusted(Adjustment.TINT, -19).adjusted(Adjustment.SHARPNESS, 24)
        val original = SavedLook(photo, 49)
        assertEquals(original, SavedLook.decode(JSONObject(original.encode().toString())))
    }

    @Test fun outOfRangeOrUnknownValuesDecodeSafely() {
        val malformed = JSONObject("""{"film":"future","intensity":200,"blur":-50,"adjustments":{"SHARPNESS":-30,"TINT":-200}}""")
        val look = SavedLook.decode(malformed).look
        assertEquals(Film.STANDARD, look.film)
        assertEquals(100, look.intensity)
        assertEquals(0, look.blur)
        assertEquals(0, look.value(Adjustment.SHARPNESS))
        assertEquals(-100, look.value(Adjustment.TINT))
    }
}
