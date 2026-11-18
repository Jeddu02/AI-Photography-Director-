# AI Photography Director v0.6.0

Signed release, November 18, 2026.

Automatic preview calibration is on by default for new and upgraded installs. It estimates the preview band from differences in texture and brightness around common camera aspect ratios. A changed layout must be confirmed on a second observation. If the preview is unclear, capture readiness clears. Analysis, guides and subject-selection coordinates use the same calibrated rectangle. Manual calibration remains available for unsupported layouts.

After Android screen-sharing consent, the assistant opens the stock camera where supported. Auto Director adapts crop scoring to detected people, groups, and recognized food. The user presses the stock-camera shutter after the ready checkmark.

The signed release built successfully and the certificate matches v0.5.0. APK integrity, permission, model, and native alignment checks passed. The Kotlin suite passed 71 tests and Flutter passed seven tests. Flutter analyzer reported no issues. Android release lint reported zero errors.

No Android device or emulator was connected. Touch interaction, live camera tracking, and cue rendering need phone verification. See docs/DEVICE_TEST_CHECKLIST.md.

APK SHA-256 `34ea1ecde4a378a5f29aa22f7554305444c1279b0465cf41d73dd1debfed71ae`
