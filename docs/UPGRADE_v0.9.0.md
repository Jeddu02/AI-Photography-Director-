# v0.9.0 baseline audit and implementation plan
The source baseline is v0.8.0+8. Flutter owns onboarding and settings. Kotlin owns CameraX preview/analysis/capture and the independent MediaProjection service. Both use Vision, CompositionEngine, ShotPlanner and GuideView. EfficientDet-Lite0 and bundled ML Kit face detection provide boxes and face attributes. Existing crop scoring uses detail, margins, brightness, headroom, horizontal placement, and external clutter. Geometric registration carries the selected crop forward. Readiness already confirms matching observations and immediately revokes a lost subject. A saved photo previously opened only in an external viewer.

Preserve the bounded analyzer, tracker, coordinate transforms, camera viewport, release signer, settings defaults and crop protections. Extend the pure crop scorer first, then use its measurements in an independent saved-photo reviewer. Add camera controls after the shared logic. Validate using synthetic geometry, regression tests, then signed release checks. Small grids can alias thin edges. Review needs upright decoding, bounded memory, read-only URI access, and lifecycle isolation. Stock-camera metadata, focus, and physical camera translation cannot be inferred from screen pixels. Hardware performance requires a device.

## Composition and guidance changes

ShotPlanner continues to sample up to four same-aspect scales. Every candidate must retain the selected subject and pass existing bystander guards. The base score includes interest density, edge detail, average luminance, retained detail, border clutter and subject-placement or scene-balance terms. New evidence can change the order of eligible candidates. The base score alone must beat the full frame by 0.07 for subjects or 0.045 for scenery before zoom can be offered. Users can request the full view.

SceneCoach samples luminance and gradients from the transient 64×64 SceneMap. Strategies vote from 0 to 1. Evidence under 0.30 is ignored. Only the strongest eligible strategy contributes, adding at most 0.035 to the ranking. Evidence over 0.45 can explain a crop. Values describe heuristic evidence and have no interpretation as probabilities.

| Strategy | Evidence and scope | Limits |
|---|---|---|
| Convergence | Six trial intersections and two diagonal rays per trial. Both rays must sustain appropriately oriented gradients. When a subject is present the intersection must be near it on both axes. Enabled for Street, Landscape and scene-oriented Auto. | Coarse, inward-converging perspective only. A missed line falls back to ordinary composition. |
| Symmetry | Compare mirrored luminance along both axes on a 10×10 crop grid. Require enough detail, a close mirror match and an advantage over the competing axis. Prefer centering a subject that is near the axis. | Blank walls and many repetitive textures are ambiguous. Intentional asymmetric perspective can be preferable. |
| Surrounding frame | At least three of four surrounding edge bands must be substantially stronger than the interior. | A geometric framing cue, without object names such as door or arch. |
| Negative space | Check the average edge activity on the side opposite the selected subject against crop-wide activity. | Quiet background can be intentional. Texture is a coarse proxy for clutter. |
| Vertical thirds | Allow upper third, lower third or nearest intersection. Evaluate vertical alignment in candidate coordinates alongside headroom, horizontal placement and safe margins. | An explicit preference can be impossible within the visible image. Wider framing remains available. |
| Lighting | Sample a 24×24 grid globally and inside the selected subject. Global mean below .13 or clipping above .42 triggers a warning. Subject mean below .26 and global-minus-subject mean above .20 suggests strong backlighting. | Rendered brightness depends on the camera. Lighting direction and real-world distance are unknown. |

The engine retains its selected goal through geometric tracking and alternative-frame selection. The drawing layer damps lower-confidence subject movement more and hides the center reticle at readiness. The grid remains optional. Significant edge hazards and lost selections take priority. Readiness combines goal alignment, stability across observations, available lighting and motion checks. During a supported tap-focus operation AI Camera suppresses the green cue until the focus result arrives. A failed result prompts refocus for a short interval. Passive autofocus metadata is unavailable in this implementation. Optional face evidence in the strategy explanation can report likely open or closed eyes without forcing a countdown.

## Camera, overlay and review changes

- AI Camera adds a scrollable tools row, a **Why this frame?** panel, and manual exposure compensation on compatible cameras. Exposure choices use the active lens's supported index range and EV increment. The panel also reports elapsed time for the last live analysis.
- AI Camera retains the CameraX shared viewport, sensor rotation and lens configuration. Its analyzer drops stale frames and adjusts its interval to processing time and severe thermal status. The low-resolution luminance buffer is shared between motion measurement and scene scoring.
- Stock Camera Assist uses the same upgraded scoring and warnings. The draggable control is clamped inside display cutout safe insets on supported systems; automatic/manual preview calibration and Android consent still apply.
- **Analyze photo** opens the latest saved in-app JPEG via a read-only stream and a non-exported activity. It reads bounds before subsampling to a maximum long edge of 1280 pixels, then applies JPEG EXIF rotation/reflection. A separate worker and detector keep the review independent of live tracking. Users can dismiss individual findings and return to the camera.
- Review covers measured tonal range, shadow/highlight fractions, subject boundaries, structural patterns, optional sloping-edge evidence and approximate fine contrast. A single small image cannot reliably distinguish motion blur from defocus or a naturally smooth surface. There is no overall artistic score or automatic edit.
- No new network requests, permissions, SDKs or model files. The dependency and model inventory remains in THIRD_PARTY_NOTICES.md.

## Verification boundaries

The shot planner was replayed against the visible preview portion of the supplied alley screenshot with no injected detections. The top proposal used 58% of each dimension and the second proposal retained the full view. The reason was concentrated detail. Screenshot graphics and previously drawn guides can influence this replay. The user's screenshot is excluded from the source bundle.

The reproducible host benchmark is tools/CompositionBenchmark.kt. It measures the pure crop planner with a warmed 64×64 synthetic perspective scene, excluding models, camera startup, photo encoding, drawing and preview conversion. Device-specific latency, frame timing, steady-state memory, battery drain and thermal behavior require a phone. Grayscale/edge heuristics cannot certify a particular scene or prove photographic quality.

CameraX HDR/Night extensions were evaluated as a compatibility choice. They require an OEM-supported extension backend and a compatible use-case combination. This build keeps the preview, analyzer and capture group together. No HDR/Night extension or quality advantage over the manufacturer's camera is claimed. Reference: https://developer.android.com/media/camera/camerax/extensions-api

## Device acceptance still open

Install over v0.8 on the same phone. Check the narrow-screen tools, supported EV changes, failed focus and recapture, moving-subject readiness, facing-camera saves, EXIF orientation, multiple aspect ratios, dismissal and returning from review. Test blocked stock-camera previews, session interruption, rotation and cutouts. Compare the captured framing with the preview. Use a fixed scene and matched lighting to benchmark startup, shutter latency, frame times, memory, thermal status and battery over sustained sessions. Capture photographs only by pressing the shutter.
