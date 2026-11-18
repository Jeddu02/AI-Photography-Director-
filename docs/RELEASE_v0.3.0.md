# AI Photography Director — 0.3.0 (build 3)

**Signed device-test update. Prepared November 18, 2026.**

## Result

- Floating-panel subject selector: Automatic, Whole scene (full view/no zoom), or up to four detected subjects identified by class and image position. Selection uses transient tracker IDs; no identity recognition or persistent subject data.
- Explicit subject choice overrides automatic priority. Losing that ID clears the goal/readiness instead of silently selecting a different same-class object.
- Stronger detected-body/object crop margins, rejection of crops through unselected detected people, near-edge widening feedback and a box-overlap separation warning.
- Tighter subject crops need >=.07 score gain over full view; scene crops >=.045. Marginal differences retain wider framing. Find another shot offers only qualifying alternatives.
- Short reasons accompany suggestions. Green says Framing aligned • capture when ready, not a guarantee of photographic quality. A different subject signature restarts readiness.
- Tiny target changes below .006 normalized units are ignored; meaningful movement still interpolates. Green-state pulse amplitude is reduced to 20%. No hardware smoothness/FPS claim.
- Flutter UI/version/help updated. No new models, SDK dependencies, permissions, network access or paid services. Existing user preferences/calibration are retained; subject choices are session-only and clear when the detector intentionally resets.

## Actual validation

- `flutter build apk --release` succeeded for package `com.jade.ai_photography_director`, versionName 0.3.0/versionCode 3; minimum Android 8.0/API 26, target API 35.
- APK size 124,107,096 bytes. SHA-256: `da2c35ef820a848e8a7568c162d785536ab2070ccb22044f6d08117e6e3fb042`.
- APK signature verified and certificate matches v0.2 for in-place update. APK ZIP integrity, bundled model hash/runtime presence, permission audit, 16 KB ZIP alignment and 64-bit ELF LOAD alignment passed.
- 57 native JUnit tests plus seven Flutter tests passed: **64 distinct tests, zero failures**. Flutter analyzer: no issues.
- Android release lint: zero errors, 12 warnings. Categories: pinned SDK/dependency versions, adaptive-icon qualifier recognition, optional KTX style suggestions and English native-menu text localization. Exact report is in docs/evidence-v0.3.0/android-lint.txt. Existing Kotlin/AGP DSL deprecation notices remain.
- New tests cover routing overrides, whole-scene selection, strict ID/class/confidence matching, loss, bounded menu/duplicate faces, margins/person cuts, overlap, unrelated faces, scope-sensitive readiness and animation deadband. Two old always-crop/always-alternative expectations were revised for the approved cautious-zoom behavior; concentrated scene detail still has an independent tighter-crop test.
- Final manifest has no INTERNET, CAMERA, microphone, external-storage, phone-state, network-state or wake-lock permission. Required overlay/foreground-service declarations and optional notification permission remain.

## Device feasibility gate

| Capability | Evidence/status |
| --- | --- |
| Previous overlay and photography use | User reports operation and supplied camera/photos from earlier versions |
| Selection/crop/zoom/readiness/geometry/animation logic | Automated pure Kotlin tests passed |
| Flutter state/settings/channel/permission denial/narrow-screen flows | Automated Flutter tests passed |
| Native floating subject-menu wiring and service/vision/render integration | Release-compiled and linted; live interaction pending |
| v0.3 detection/selected-lock persistence, appearance/smoothness, stock-camera touch-through | Not tested on physical phone/emulator here |
| Consent, usable preview capture, rotation/lock/interruption, sustained memory/battery/thermal behavior | Existing implementation; this update's device checks pending |
| Protected camera preview | Unsupported for AI; separate manual-guide fallback remains |

ADB reported no connected device/emulator. No phone installation, live inference, animation FPS, battery, thermal, memory-leak or sustained-operation measurement was performed. Native menu interaction is not covered by Flutter widget tests. See docs/DEVICE_TEST_CHECKLIST.md.

## Limits and behavior

Detection is limited to the existing model vocabulary and confidence. A wristwatch is not promised as a separate detectable category. Small subjects, low light, foreground obstructions and detector dropout can prevent selection. A dropout can assign a new tracker ID, requiring manual reselection. The existing class/IoU tracker can confuse nearby similar objects; this is not identity verification.

Crop protection covers detected rectangular bounds, not body-part segmentation. Face-only detection cannot protect unseen feet. Overlapping boxes can falsely suggest a conflict; the app cannot infer depth, which item is in front, or all wires/leaves/clutter. Face/eye checks apply to the selected person/group's associated reported faces, and missing estimates remain unknown.

The planner ranks visible content with local heuristics; it cannot see unseen places, infer safe physical movement, judge beauty or guarantee the best moment. Zoom is relative to the current visible preview, never read from or applied to the stock camera. The user owns zoom/exposure/shutter. Whole scene is an explicit wide-framing preference and may qualify on a plain image; green indicates supported alignment/stability criteria only. Manual Composition never claims AI readiness.

Full-display capture can follow other applications within the calibrated rectangle. Stop before private content; frames remain transient. Boundaries and foreground app names are not automatically identified. Share the entire screen and calibrate all four preview edges. Changing a photography mode during an AI session does not end screen sharing; use Stop before entering the independent manual-guide session. No Android security restriction is bypassed.

## Architecture and provenance

Flutter owns navigation/settings; Kotlin owns official projection, native windows, one-in-flight frames, bundled inference, geometry and lifecycle cleanup. SubjectSelection/CropProtection add pure local selection and geometry checks; the service's small subject snapshot contains metadata only. Existing capture scaling, frame dropping, inference intervals, thermal throttling and cleanup remain.

Pinned tooling/dependencies are unchanged: Flutter 3.50.0/Dart 3.14.0, JDK 21.0.13, Gradle 9.5.0, AGP 9.3.1, Kotlin 2.4.20, SDK/build tools 35, NDK 28.2.13676358; MediaPipe Tasks Vision 0.10.35 and ML Kit Face Detection 16.1.7. ML Kit is a free proprietary SDK; see THIRD_PARTY_NOTICES.md.

Current contracts: docs/SUBJECT_DIRECTOR.md; prior shot/rule contracts remain version-labeled. Fresh v0.3 evidence is separate from prior evidence directories and release records. User photographs, derived private fixtures, SDKs/caches, APKs and private signing material are excluded from the source archive.
