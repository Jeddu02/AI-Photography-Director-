# AI Photography Director v0.14.0 source update

## Changes in AI Camera

Open the overlapping-circles **Film and lighting** control in a photo mode. The adjustment tray scrolls independently of the shutter controls.

| Panel | Controls |
| --- | --- |
| Styles | 20 color transforms, including all 14 requested styles and six legacy presets. Thumbnails use a camera snapshot when available, with a reference color swatch as fallback. Style intensity, tone, color, warmth, and the existing tone and color pad are adjustable. |
| Portrait | Six lighting modes, lighting strength for non-Natural effects, and adjustable background blur on Android 13+ with compatible hardware rendering. Portrait effects use the existing offline face/person detector and estimated subject mask. |
| Adjust | Rendered exposure, brilliance, highlights, shadows, contrast, brightness, black point, saturation, vibrance, warmth, tint, sharpness, definition, noise reduction, and vignette. Numeric sliders have individual reset buttons. Physical exposure compensation stays in the existing EV control. |
| AI Suggest | Uses detected subjects, preview luminance, highlight and shadow distribution, warm midtones, and confident horizontal lines. It offers a reason and waits for Apply. It fills default settings and keeps manually selected styles, lighting, and nonzero adjustments. The scene heuristics infer lighting cues, with no guaranteed recognition of scene type or time of day. |
| Presets | Store named local looks, restore them, or reset all creative controls. Existing single-look settings migrate on first use. |

## Rendering and device support

- Styles and basic adjustments use the existing hardware color-matrix preview. Android 13+ still modes use an AGSL GPU effect for advanced tonal controls, detail, vignette, and portrait blur. Older Android versions and devices without compatible GPU rendering expose basic controls. Cinematic video retains the existing matrix renderer.
- Studio and Contour simulate local lighting with a soft subject mask. Stage and High-Key modes modify the background with the same mask. Mask edges can miss fine hair, glasses, or overlapping subjects. Blur estimates foreground separation from image cues, with no metric depth information or true relighting.
- JPEG capture keeps the original. The export uses an independent high-resolution pass with corresponding processing parameters and re-estimates the portrait mask. Subject movement, differences between preview and still exposure, and kernel sampling can make the styled JPEG differ from the preview. Export uses the original resolution when the current memory budget allows. Styled JPEGs copy selected non-location EXIF tags and normalize orientation.
- The existing Stock Camera Assist continues to provide composition guidance. Creative effects apply to AI Camera output. Imported-image editing is outside this source update.

## Verification status

The project source and archive were reviewed and checked for packaging integrity. New Android JVM tests cover distinct style transforms, style-intensity zero, color adjustment composition, tonal ranges, spatial controls, and settings migration and round-trip behavior. Kotlin compilation and those tests require the Android build environment.

The release command returned `flutter: command not found`. The Gradle wrapper then failed to download Gradle 9.5.0 with `java.net.SocketException: Network is unreachable`. The Android SDK, an Android test device or emulator, and the release signing environment variables were unavailable. This source version has no compiled or signed release APK.

Before releasing, validate AGSL compilation and live rendering on Android 13+, the basic-control fallback on Android 8–12, the six portrait modes with front and rear cameras, preview/JPEG consistency, original and styled file export, screen rotation and lifecycle changes, composition and Stock Camera Assist regressions, and shutter/preview performance across devices. The existing `tools/build_release.sh` runs the configured Flutter and Gradle checks and builds the signed APK when the original release signing configuration is present.

## Build on GitHub Actions

The project includes a manually triggered **Build signed Android APK** workflow in `.github/workflows/android-release.yml`. Add the project to a repository you control and put the workflow on its default branch so it can be triggered from the Actions tab. Add these repository Actions secrets before running it.

| Secret | Value |
| --- | --- |
| `APD_KEYSTORE_BASE64` | Base64 of the original release keystore. Treat the encoded value like the keystore itself. |
| `APD_STORE_PASSWORD` | Keystore password |
| `APD_KEY_ALIAS` | Alias of the original release key |
| `APD_KEY_PASSWORD` | Password for that key |

The job runs the existing Flutter analysis, Flutter tests, Android JVM tests, and Android release lint. It builds the release and reuses the existing APK verifier. The upload also requires the package version to match `pubspec.yaml` and the signing certificate to match v0.13.0. A successful run provides **AI-Photography-Director-APK** under Artifacts with the APK and verification records. Device and camera tests still require a handset. This workflow has not run on a GitHub runner in the current environment.

Local checks passed for workflow YAML, Bash step syntax, and four version/signer fixtures. The fixtures covered an expected version and signer, a different signer, the old version, and a missing signer. The checks used verification-report fixtures and did not produce a new APK.

## Subject framing from the reference clip

AI Camera now draws a tracked outline and a label from the detected subject class. The badge adds **Ready** after the existing composition and focus checks pass. Tap **Auto frame** beside the zoom controls to apply a fresh, centered framing suggestion within the lens's supported zoom range. Off-center shots and crops that would cut the detected subject still require manual framing. After a zoom request, the live feed is checked again before showing **Ready**. Keep using the shutter to take a photo.

The outline follows model detections. Its label can be as broad as **Cup**, and unknown subjects still receive the existing scene guides. CameraX controls zoom timing on each handset. This source has not been Android-compiled or tested on a handset. Added JVM test cases for the crop, centering, confidence, and zoom-range rules.
