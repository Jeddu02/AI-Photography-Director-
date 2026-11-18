# AI Photography Director v0.4.0

Signed Android update, November 18, 2026.

The composition planner scores explicit thirds, centering, headroom and room in the direction a detected face is turned. Automatic placement centers groups. The optional thirds grid follows the proposed crop. Subject selection, crop protection and the stock-camera overlay workflow carry over from v0.3. The headroom estimate depends on the detected face box. Placement preferences can yield to wider framing when the tighter crop is unsafe or barely improves the score. See docs/COMPOSITION_PLACEMENT.md.

The release build and APK verification passed. Version 0.4.0 uses build code 4, targets API 35 and supports Android 8.0 and later. Its signer matches v0.3.0. APK integrity, permission audit, bundled model checks and native alignment passed. The APK is 124,107,092 bytes with SHA-256 `33a9913f11277572b4288ac3c57fd9358cf5a282a2689407fe93d1341a42d672`.

63 Kotlin tests and seven Flutter tests passed. Flutter analyzer found no issues. Android release lint reported zero errors. See docs/evidence-v0.4.0 for detailed outputs.

No connected Android device or emulator was available. Live framing, grid rendering and control interaction on the stock camera still need phone verification. Analysis runs on device with the existing bundled models and permission set.
