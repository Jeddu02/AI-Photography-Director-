# AI Photography Director v0.5.0

Signed release, November 18, 2026.

The live guidance overlay shows a single cue and a green **Ready to capture** checkmark after two consecutive qualifying observations. The previous elapsed-time gate and visual progress ring were removed. A change in framing, motion, or subject scope clears readiness. The app reads the preview and the user controls the stock-camera shutter.

The Subject panel adds **Select on preview**. Tap a point or drag around an area. Image registration follows the selected area during small camera moves and reports loss when registration fails. The selection covers a region of the scene. It may drift or need reselection if the object moves independently, the camera moves quickly, or the scene has little texture. Detected-subject selection remains available.

Crop scoring discounts strong edges and bright highlights outside the subject. **Find another shot** offers a wide alternative when the selected crop is tighter. Crop protection still applies.

The signed release built successfully and the certificate matches v0.4.0. APK integrity, permission, model, and native alignment checks passed. The Kotlin suite passed 66 tests and Flutter passed seven tests. Flutter analyzer reported no issues. Release packaging completed Android vital lint checks.

No Android device or emulator was connected. Touch interaction, live camera tracking, and cue rendering need phone verification. See docs/DEVICE_TEST_CHECKLIST.md.

APK SHA-256 `f5116b2257617403edfe89b7226d5d4c6c4025b355b6f50cb60de21af6d40f3b`
