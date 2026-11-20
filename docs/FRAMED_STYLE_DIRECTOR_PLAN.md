# Framed-style live director — implementation and acceptance contract

Status: implementation planned; no new camera behavior is claimed by this document.

## Existing baseline
- Flutter/Kotlin Android 8+ app, v0.14.0+14.
- Keep AI Camera and Stock Camera Assist, on-device detectors, capture readiness, creative controls, and original photo export intact.
- No cloud API, paid dependency, automatic shutter, or invasive permissions.

## Live user journey
1. Open AI Camera, grant camera access, and see the live preview.
2. Tap **Scan scene** or enable **Auto Director**; the app evaluates detected people/objects, faces, luminance, horizon candidates, motion, and preview geometry.
3. Display up to three *distinct and feasible* composition candidates with a short reason and confidence/limitation wording; do not claim learned aesthetic certainty.
4. Select a candidate to lock a target. Draw an unobtrusive target frame, subject indicator and optional thirds guide.
5. Provide one actionable instruction at a time: move camera left/right/up/down, rotate, zoom in/out, hold steady, or capture. Label guidance as *image-space* movement unless physical directions are calibrated.
6. Reevaluate after each motion; do not show Ready while the subject is cut off, analysis is stale, camera motion is excessive, or exposure is unusable.
7. Display Ready only after the existing stability/readiness dwell period; capture remains user-triggered.
8. Allow **Find another shot**, **Rescan**, **Lock subject**, **Dismiss guides**, and **Manual** without disrupting shutter access.

## Engineering work packages
- Audit AI Camera analysis and overlay mapping; ensure sensor orientation, front-camera mirroring, zoom, crop, ratio and rotation are handled consistently.
- Add a candidate generation/ranking layer that considers subject prominence, rule of thirds, headroom, facing room, horizon, background clutter, and safe crop margins. Deduplicate near-identical suggestions.
- Maintain a selected target across consecutive frames with bounded smoothing, loss-of-subject fallback, and re-scan on substantial scene changes.
- Integrate candidate selection and live instructions into the Flutter camera UI without introducing a new camera session or changing photo capture.
- Adapt the same director decisions for Stock Camera Assist where preview pixels are available; never assume stock-camera zoom or focus control.
- Add offline JVM unit tests for candidate ordering, subject loss, mirroring, rotation, crop clipping, stale analysis, and readiness; add Flutter interaction tests.
- Update user documentation and release notes with verified evidence only.

## Nonfunctional acceptance
- No crash or blank viewfinder on permissions denial, resume, camera switch or rotation.
- No stale Ready indication after subject loss or preview interruption.
- No automatic photo capture; camera permission and screen-sharing consent remain explicit.
- Existing film presets, original JPEG saving, portrait controls and stock overlay remain functional.
- Run flutter analyze, flutter test, Android JVM tests, release lint and build in the existing workflow; perform physical-device testing before claiming camera behavior works.
- APK must be signed with a controlled key. Preserve original signing identity for in-place updates; never commit keystores or passwords.

## Release gating
This document is not an APK and does not implement the feature. A release is blocked until code changes, CI success, APK signature/version verification and real-device checks are evidenced.
