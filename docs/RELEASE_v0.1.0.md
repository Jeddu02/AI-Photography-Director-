# AI Photography Director — 0.1.0 (build 1)

**Signed initial device-test release. Prepared November 18, 2026. Hardware compatibility is not certified.**

The prior workspace was lost before saving. This reconstruction was rebuilt, tested and saved independently; prior test results were not substituted for new evidence.

## Completed verification

- Actual `flutter build apk --release`: successful.
- Package `com.jade.ai_photography_director`, minimum Android 8.0/API 26, target API 35.
- APK size 124,090,708 bytes, about 124.1 MB. Bundles ARM64, ARMv7 and x86-64 native libraries.
- RSA-3072 / APK signature scheme v2 verified with Android apksigner.
- ZIP integrity, bundled object-model hash, face/model/runtime presence, 16 KB ZIP alignment and 64-bit ELF LOAD alignment passed.
- Final APK SHA-256: `c44f37d8380d1f4b8a10e03250034fd1d81ee7d10623f8137744fc1afa8afc62`.
- Flutter analyzer: no issues. Seven Flutter controller/UI tests and 17 native JUnit tests passed (24 distinct tests, zero failures).
- Android release lint: zero errors, nine warnings. Warnings concern the pinned target/compile SDK, monochrome icon recognition across resource qualifiers, and optional KTX style suggestions. Exact output is included in docs/evidence/android-lint.txt.
- No INTERNET, CAMERA, microphone, external-storage, phone-state, network-state or wake-lock permission in the final APK. It declares overlay access, optional notifications and required foreground-service types. AndroidX adds an app-private signature receiver permission.

## Mandatory feasibility gate

| Capability | Status |
| --- | --- |
| Overlay and draggable controls over a stock camera | Implemented/release-compiled; device verification pending |
| Official projection consent and denial | Implemented; Flutter bridge denial tests pass; actual Android dialog pending |
| Usable camera preview frames | Device compatibility unknown; no connected device |
| Preview-to-display geometry | Pure mapping tests pass; physical alignment pending |
| Object/person and face analysis | Native runtimes/models bundled and compiled; actual Android inference pending |
| Positioned visual guidance | Engine tests pass; native rendering over camera pending |
| Stock camera touch controls remain usable | Touch-through window configured; device verification pending |
| Rotation, interruption, lock and process termination | Implemented; actual integration validation pending |
| Security restrictions | No bypass, accessibility service, hidden recording, automatic shutter or boot capture implemented |
| Manual fallback | Separate overlay-only session implemented; no projection consent requested; device behavior pending |

ADB reported no attached phone/emulator. KVM was unavailable. No installation, on-device inference, native-overlay accessibility, memory-leak, battery, thermal or sustained-operation measurement was performed. See docs/DEVICE_TEST_CHECKLIST.md before claiming device compatibility.

## Product behavior and limits

The stock camera remains the camera and owns its shutter/zoom/exposure. Main UI uses Flutter; the native Kotlin service owns overlays, projection, buffers, inference, transforms and cleanup. Frames stay transient and are not intentionally saved. Preferences stay local; cloud backup/device transfer is disabled. No paid API, account, server, subscription or advertising is required.

All seven modes are selectable. Auto uses detected people/objects and explicit rules; it is not a separate semantic scene classifier. Portrait/group/food/street use the supported geometry and image features. Landscape offers framing guides, motion feedback and a conservative image-edge line candidate. Landscape and Manual never claim capture readiness.

Rules, thresholds, routing weights and limitations are documented in docs/COMPOSITION_RULES.md. Background edge detail is not depth segmentation; an edge line is not a semantic horizon. Symmetry is measured but does not drive automatic advice. Face yaw/eye estimates exist only when the detector reports them; the internal face routing weight is not a calibrated probability. Full obstruction detection, smile-based readiness, GPU/NPU qualification and perfect-moment guarantees are unsupported.

Readiness requires sustained supported criteria and smoothing, and revokes immediately when they fail. A >2000 ms observation gap resets readiness. Processing uses one in-flight frame, dropping/backpressure, longest capture edge <=960 pixels, configurable intervals, thermal slowdown and cached overlay drawing. These are implementation limits, not measured phone FPS/battery claims.

Choose entire-display sharing for alignment. Some cameras protect the preview or reject overlays. Dark/black frames cannot distinguish protection from genuine darkness; the warning is cautious. Stop AI and select Use manual guides for an independent overlay-only session. Changing a photography mode inside an already active AI session does not end Android sharing; use Stop to end capture. No old projection token is reused.

App-name recognition is unsupported. Full-display capture can follow other apps inside the calibrated rectangle; stop before private content. App-only/split-screen capture and multiple displays are not qualified. Calibrate all four edges and recheck after rotation or camera-layout changes. Thin overlay lines may be included in capture and influence simple features.

ML Kit is a free proprietary SDK governed by Google terms, with published SDK-metrics disclosures. It is not represented as an open-source model. The application removes internet permission to block direct networking. See THIRD_PARTY_NOTICES.md.

## Toolchain used

Flutter 3.50.0 / Dart 3.14.0; Temurin JDK 21.0.13; Gradle 9.5.0; AGP 9.3.1; Kotlin 2.4.20; Android compile/target API 35, build tools 35.0.0 and NDK 28.2.13676358. MediaPipe Tasks Vision 0.10.35 and bundled ML Kit Face Detection 16.1.7. Worker/heap limits are configured for the build environment. Legacy Kotlin/AGP DSL deprecation warnings remain; future upgrades require migration rather than assuming compatibility.

## Phase record

| Phase | Outcome |
| --- | --- |
| 1 Environment | Empty/reset workspace inspected; SDK/JDK/Flutter installed; device inventory checked |
| 2 Foundation | Theme, navigation, onboarding, preferences and channel implemented |
| 3 Overlay | Native windows, movable controls, stop/pause/opacity/mode/grid and manual session implemented |
| 4 Capture | Consent, bounded transient buffers, resizing, interruption and cleanup implemented |
| 5 Vision | Bundled person/object and face models integrated and release-compiled; device inference pending |
| 6 Composition | Rules, tracking, score, confidence filtering, smoothing and readiness tested |
| 7 Guidance | Boxes, arrows, grid, targets, candidate level, instructions and readiness implemented |
| 8 Modes | Seven explicit modes with supported heuristics implemented |
| 9 UI | Dark/yellow interface, phone/narrow-screen tests, accessibility handling and overlay controls implemented |
| 10 Performance | Bounded queue, scaled capture, presets, thermal throttle and drawing caches implemented; phone benchmarks pending |
| 11 QA | Fresh source/unit/UI/lint/artifact checks passed; device acceptance pending |
| 12 Packaging | Signed APK, source, install guide and separate private signing backup saved |

**Build/test artifact: GO. Device testing: ready. Production/hardware compatibility certification: NO-GO until the device checklist passes.**
