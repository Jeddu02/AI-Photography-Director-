# AI Photography Director v0.13.0
Android 8.0+ · version code 13

## Camera guidance and controls
Opening AI CAM restores the saved automatic composition preference or uses Auto Director. An older Manual Composition preference no longer disables framing and capture-readiness checks in AI CAM. To use manual guides intentionally, open More and select Manual. Tap AI CAM to resume automated guidance. Portrait uses portrait-specific composition. Image quality and detector confidence still determine whether the assistant can recommend a shot.

The top bar has original vector icons for Back, flash, exposure, camera tools and film/lighting, plus the current aspect ratio. The shutter has a solid center and a contrasting outer ring. The selected camera mode has a yellow pill. Tools and adjustments display two labeled choices per page with pinned navigation. Tap the right chevron to cycle through the pages or the left chevron to return to shooting modes. The 3:4 viewfinder preserves capture geometry; very tall displays can have unused space around the preview.

## Portrait lighting and AI Auto
Open AI Camera and select Photo or Portrait. Aim at a person. In **Film & light → Lighting**, choose Studio, Contour, Stage, Stage Mono, or High-Key Mono. Natural is the neutral option. **Portrait intensity** controls the effect from 0 to 100%. Stage dims the background, and High-Key Mono lifts it. The app preserves the original captured JPEG and creates a separate styled copy. The copy is bounded at 3,072 pixels on the long edge.

Choose **Film & light → AI Auto** to adjust the current scene. One tap evaluates clipped highlights, shadow coverage, contrast, near-neutral colors and the detected subject’s brightness. It sets contrast, saturation and warmth, selects a lighting style and intensity, and requests an EV change when supported and unlocked. The app adjusts once per tap. Manual changes stay in control afterward. In Stock Camera Assist, expand the floating control and tap **AI Auto suggestion** for advice about the current preview.

## How the portrait effect works
The existing offline object and face detectors supply person/face candidates. The largest qualifying face guides selection of the corresponding person. A 64×64 RGB scene map estimates a soft subject mask from foreground/background color histograms, face and body location, and four edge-aware refinement passes. The preview composites a spatial light field above the camera image and below the composition guides. Still capture estimates a new mask from the oriented saved image. Stage Mono and High-Key Mono combine that field with intensity-dependent monochrome conversion. All processing is local. There is no face reshaping, skin smoothing, or inferred 3D depth.

Similar-colored backgrounds, groups, fine hair, fast movement and missed detections can reduce separation. The mask updates at the existing analysis cadence. Preview and capture use the same algorithm, but detections and vendor processing may differ. The feature targets still photography. Cinematic video retains its global color effects. No synthetic depth accuracy or physical-device latency claim is made.

## Verification
See docs/evidence-v0.13.0. No phone was attached. Camera launch, OEM extensions, installation over an existing version, appearance of portrait boundaries, preview responsiveness and saved-image matching still require on-device checks.

The preceding v0.11.0 release compiled with Flutter, version code 11. Signing certificate matches v0.9.0. ZIP integrity, release permissions, bundled model hashes, native library alignment and package identity checks passed. The APK contains the portrait and AI Auto controls. Flutter static analysis passed and all seven Flutter tests passed. Synthetic JVM checks confirmed subject/background separation, Stage Light attenuation, zero-intensity transparency, clipped-scene exposure advice and conservative shadowed-portrait adjustments.

99 Android JVM tests passed, including the narrow portrait layout and portrait lighting/AI Auto tests.

## v0.12.0 testing
99 Android JVM tests passed, including the 320dp portrait camera layout, quick tools and More grid navigation. Seven Flutter tests passed. No physical camera was attached.

## v0.13.0 verification
100 Android JVM tests passed, including restoring AI CAM with a saved Manual Composition preference. All seven Flutter tests passed. No physical handset was connected.
