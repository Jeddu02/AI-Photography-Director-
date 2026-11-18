# AI Photography Director v0.7.0

Signed release, November 18, 2026.

AI Camera opens a CameraX viewfinder. The existing local detector and framing engine analyze camera frames to show guidance and a capture checkmark. The user takes the photo with the Capture button. This mode requests camera access on entry and uses neither an Android overlay nor screen projection. Stock Camera Assist remains available from the home screen.

Select 4:3, 16:9, 1:1 or 3:2. Portrait uses the corresponding inverted ratio. Preview, image analysis, and JPEG capture share a CameraX viewport. The app can suggest a ratio based on the scene, while the user chooses whether to apply it. Ratio suggestions use a simple subject-coverage check. They are optional and can miss subjects.

Controls include tap-to-focus, pinch zoom, front/back switching, a grid, and flash on supported lenses. On Android 10 and above, photos save to Pictures/Photography Director and appear in gallery apps. On Android 8–9, they are stored privately and can be opened from View photo. Manufacturer-specific processing and modes can differ from the stock camera.

The signed release built successfully and the certificate matches v0.6.0. APK integrity, permission, model, and native alignment checks passed. The Kotlin suite passed 75 tests and Flutter passed seven tests. Flutter analyzer reported no issues. Android release lint reported zero errors.

No Android device or emulator was connected. Touch interaction, live camera tracking, and cue rendering need phone verification. See docs/DEVICE_TEST_CHECKLIST.md.

APK SHA-256 `813717689c12b8e8398b1bfcb3082867ed336bf0b40690b0e5bc0875396834fc`
