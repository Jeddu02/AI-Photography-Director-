# AI Photography Director v0.8.0

Signed release, November 18, 2026.

Scene-only framing now discounts deep shadow near the upper and side edges. Direction prompts say where to aim. The scene planner retains a full view as an alternative when a tighter crop ranks first.

AI Camera adds **Next frame**, an optional **Level** guide, and an off/3-second/10-second shutter timer. Press Capture to start the selected timer. Tap Cancel timer to stop it. Leaving the camera or losing its live preview also cancels it. Level uses the gravity sensor or accelerometer when available.

A replay of the supplied screenshot used only its visible preview as input. The new scene crop shifts left, reducing inclusion of the right overhang. Replay cannot establish device capture quality.

The signed release built successfully and the certificate matches v0.7.0. APK integrity, permission, model, and native alignment checks passed. The Kotlin suite passed 78 tests and Flutter passed seven tests. Flutter analyzer reported no issues. Release packaging completed Android vital lint checks.

No Android device or emulator was connected. Touch interaction, live camera tracking, and cue rendering need phone verification. See docs/DEVICE_TEST_CHECKLIST.md.

APK SHA-256 `d3bcf1b8a9b38e1e732597b6a0a33aa0208023cc2b8b6f77934b548cb09879fc`
