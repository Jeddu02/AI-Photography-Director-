# AI Photography Director — 0.2.0 (build 2)

**Signed device-test update. Prepared November 18, 2026.**

## What changed

- Content-based shot finder ranks actual visible crops, including scenery outside the object detector's vocabulary. One selected frame is tracked through aiming/zooming; Find another shot offers alternatives.
- Visual sequence: center highlighted area → relative zoom → hold steady → capture recommendation. The stock camera remains fully manual.
- Indicator geometry interpolates independently of inference; color/progress ease, the reticle pulses gently, and the default full grid is replaced by a minimal frame. Grid remains optional.
- Settings migration turns the grid off once and preserves calibration, mode and other settings. No new dependency/model/permission, cloud processing or paid service.

## Completed verification

- Actual `flutter build apk --release` succeeded for package `com.jade.ai_photography_director`, version 0.2.0/code 2, Android 8.0/API 26 minimum and target API 35.
- APK: 124,107,092 bytes. SHA-256: `1a4d67a5f42682d2cba17f6812fd0e55c8c6797559fe4b0f3109f9c4bc3adf0d`.
- Valid APK signature; signing certificate matches the previous release for in-place updates.
- ZIP integrity, packaged model hash/runtime presence, permission audit, 16 KB ZIP alignment and all 64-bit native ELF LOAD alignments passed.
- 36 native Kotlin JUnit tests and 7 Flutter tests passed (43 distinct tests, zero failures). Flutter analyzer: no issues.
- Android release lint: zero errors, 10 warnings (pinned target/compile SDK, adaptive-icon qualifier recognition and optional KTX style suggestions). Full evidence is in docs/evidence-v0.2.0.
- No INTERNET, CAMERA, microphone, external-storage, phone-state, network-state or wake-lock permission in the final APK. Offline bundled models are unchanged.
- Private numeric screenshot probe selected a lower-scene crop and downward aim followed by ~1.2× relative zoom. This is algorithm verification, not an aesthetic or live-capture benchmark. User image/derived pixel fixture is excluded from source.

## Feasibility and device status

| Capability | Evidence/status |
| --- | --- |
| Previous overlay on user's stock camera | User reports v0.1 working; screenshot shows overlay |
| v0.2 crop ranking, transforms, zoom progress, arrow signs, readiness, interpolation | Automated pure Kotlin tests passed |
| Flutter settings/channel/permission denial and narrow-screen interface | Automated Flutter tests passed |
| v0.2 Android service/vision/drawing integration | Release-compiled and linted; live phone testing pending |
| Official screen consent, usable frames, touch-through, dragging, rotation/lock/interruption | Existing implementation; this update's integration testing pending |
| Indicator smoothness, sustained tracking, battery, thermals, memory leaks, native accessibility | No physical measurements; device testing pending |
| Protected camera preview | Unsupported for AI analysis; separate manual-guide fallback |

ADB showed no connected device/emulator. No installation or on-device test of v0.2 was performed here. No fixed FPS, inference latency, battery or thermal result is claimed. See docs/DEVICE_TEST_CHECKLIST.md.

## Behavior and limitations

Shot ranking uses detections plus contrast/detail, exposure, balance and crop retention. It cannot understand beauty, unseen surroundings or where physically to stand. Relative zoom is estimated from image geometry; camera lens/zoom metadata is not read. The user adjusts the stock camera and presses its shutter. Large movement, moving subjects, parallax, lens switching, similar objects, repeated texture or captured overlay lines may disrupt tracking. Suggestions should be judged by the user.

Landscape can now qualify a geometrically aligned, stable frame; this does not imply semantic moment recognition. Portrait/Group require detected people, and Manual Composition never claims AI readiness. Some earlier standalone headroom/looking-room/background/horizon rules remain only in the legacy fallback rather than the new shot score. Exact inputs, thresholds, hysteresis and animation limits are documented in docs/SHOT_DIRECTOR.md.

Readiness requires at least 1.3 seconds of supported qualifying observations plus instruction smoothing, and is revoked on detected failure. Missing eye/yaw detections do not prove visibility or no obstruction. No separate scene classifier, complete obstruction detector, smile readiness, GPU/NPU qualification, automatic shutter or perfect-moment guarantee is claimed.

Choose entire-screen sharing and calibrate all four camera-preview edges. Boundaries and foreground-app names are not automatically identified; stop before private apps. Frames remain transient. Stop AI before selecting the independent manual-guide session; changing a mode inside AI does not end screen sharing. Some cameras reject capture or overlays; no restriction is bypassed.

## Architecture, tooling and provenance

Flutter owns UI/navigation/local settings; Kotlin owns projection/services/buffers/vision/geometry/drawing. SceneMap/ShotPlanner/ShotDirector/SceneMatcher/SmoothFrame add bounded local analysis without additional SDKs. Existing one-in-flight acquisition, dropping, <=960-pixel capture edge, adaptive intervals, thermal throttling and cleanup remain.

Flutter 3.50.0, Dart 3.14.0, Temurin JDK 21.0.13, Gradle 9.5.0, AGP 9.3.1, Kotlin 2.4.20, SDK/build tools 35 and NDK 28.2.13676358. MediaPipe Tasks Vision 0.10.35 and bundled ML Kit Face Detection 16.1.7. See THIRD_PARTY_NOTICES.md for licensing, including proprietary ML Kit terms. No required runtime network access.

Previous release evidence remains in docs/evidence and docs/RELEASE_v0.1.0.md; it is not substituted for v0.2 checks. Private signing material and user screenshots are excluded from the source archive.
