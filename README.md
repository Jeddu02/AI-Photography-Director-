# AI Photography Director

**Version 0.14.0 source adds photographic styles, portrait controls, and professional adjustments to AI Camera.** See [controls, compatibility, and verification status](docs/CREATIVE_CAMERA_v0.14.0.md).

Version 0.9 adds structure-aware crop ranking, vertical placement, subject lighting checks, manual exposure compensation, explanations and offline saved-photo review. See [upgrade design and limitations](docs/UPGRADE_v0.9.0.md).

Version 0.8 added a level guide, shutter timer, and alternate framing control. Scene-only framing weighs deep shadow along the frame edges. Version 0.7 added an in-app CameraX viewfinder with live guidance, photo capture, ratio choices, and optional ratio suggestions. Stock Camera Assist remains available. Version 0.6 found common camera preview boundaries automatically and adapts framing to recognized subject types in Auto Director. Version 0.5 added a live capture checkmark, preview target selection and crop scoring for distracting backgrounds. Version 0.4 added crop ranking based on the rule of thirds, center balance, headroom and room in the direction a detected face is turned. The optional thirds grid follows the proposed shot. See [Composition placement](docs/COMPOSITION_PLACEMENT.md).

Flutter + Kotlin offline visual direction over a compatible Android stock camera. Version 0.3 adds explicit subject selection, stronger crop protection, cautious zoom advice and calmer animation. The user reports earlier camera use; this update still needs phone testing.

## Use

Install the signed APK on Android 8+. Open the app, read the introduction and grant display-over-other-apps access. Start the AI assistant, optionally allow stop notifications, authorize sharing the **entire screen**, then open your stock camera. Drag ◉ out of the way; tap to expand controls. Center the suggested frame, follow its relative zoom cue, then hold steady. Open Subject: Automatic in the floating panel to choose a detected subject or Whole scene. Use Find another shot for another qualifying alternative. Press your stock camera’s shutter yourself.

**Settings → Preview calibration** defaults to automatic detection. It estimates the visible camera band from live screen pixels and checks for layout changes. If the preview has no clear boundaries or uses an unusual layout, turn off automatic detection and set its edges. Directions describe movement in the image.

Stop before private apps: whole-display analysis follows whatever appears inside the preview rectangle. Automatic foreground-app identification is unsupported. Locking ends capture and a fresh session needs consent. Stop from the floating controls, foreground notification if enabled, or Android Active apps controls. Some cameras reject capture or all overlays; stop AI and try manual guides. The separate Use manual guides session does not capture the screen. Changing a photography mode inside an already active AI session does not end Android screen sharing; use Stop, then Use manual guides for overlay-only operation.

## Capabilities

Content-ranked crop proposals for detected subjects and unlabelled scenery; relative zoom/aim/steady/capture cues; a locked goal tracked across pan/zoom; alternative proposals; display-time interpolation, reticle and subtle pulse. Full grid is optional and defaults off.

Bundled EfficientDet-Lite0 object/person detection; bundled ML Kit face detection; geometric tracking; exposure/motion/image-edge features; rule-based composition; readiness smoothing; boxes, arrows, grid, target and candidate-level line; seven selectable modes; persisted local settings; performance presets and thermal throttle; manual fallback.

No account, paid API, subscription, advertising, auto shutter or cloud service is required. AI Camera uses runtime camera permission. The release manifest removes internet, microphone, phone-state, and external-storage permissions. Frames are transient, not intentionally saved. ML Kit is a free proprietary SDK with published metrics terms; see THIRD_PARTY_NOTICES.md.

## Build

Tested toolchain is recorded with the finished release. Reconstruction is pinned to Flutter 3.50.0, JDK 21, Android SDK 36 and build tools 35 or newer, Gradle 9.5.0, AGP 9.3.1 and Kotlin 2.4.20. Install Android NDK 28.2.13676358 when Flutter requests it. Legacy DSL deprecation warnings remain on these pinned versions; future upgrades require migration.

Run `flutter pub get`, `flutter analyze`, `flutter test`, and `cd android && ./gradlew :app:testDebugUnitTest`. For a signed release, set APD_KEYSTORE (absolute path), APD_STORE_PASSWORD, APD_KEY_ALIAS and APD_KEY_PASSWORD, then run `flutter build apk --release`. Keep the same key for updates and increase the +build number in pubspec.yaml. Do not publish the private signing backup.

The source includes Gradle wrapper scripts/JAR and an optional GitHub test workflow. It excludes SDKs, machine-local paths, caches, private signing material and APK build outputs.

## Architecture and limitations

`lib/` owns Flutter navigation, settings and the channel controller. MainActivity owns official authorization. DirectorService owns windows, capture lifecycle and the single-in-flight pipeline. Vision owns local inference/features. Composition owns pure decision logic, tracking and mapping. GuideView caches drawing geometry and instruction layouts.

Horizon candidates are image edges, not semantic horizons. Background edges are not depth/segmentation. Eye/yaw estimates exist only when reported by the face model. Strong mirrored patterns can contribute to crop ranking. No separate scene classifier, complete obstruction detector, smile readiness or GPU/NPU qualification is claimed. Landscape can recommend a geometrically aligned, stable frame; Manual never claims AI readiness. Whole-screen capture may include the assistant’s own thin lines. “Capture now” is a recommendation, never a perfect-moment guarantee.

See docs/COMPOSITION_RULES.md and docs/DEVICE_TEST_CHECKLIST.md. See RELEASE_STATUS.md for current verification, docs/evidence-v0.9.0 for this APK, and docs/evidence for the previous version. Subject selection, crop/overlap protection, cautious zoom thresholds and calmer animation are documented in docs/SUBJECT_DIRECTOR.md; it supersedes overlapping parts of docs/SHOT_DIRECTOR.md.
