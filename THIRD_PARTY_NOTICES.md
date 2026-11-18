# Third-party notices

| Component | License/terms | Upstream |
| --- | --- | --- |
| Flutter / Dart | BSD-3-Clause | https://github.com/flutter/flutter/blob/stable/LICENSE |
| Kotlin / AndroidX | Apache-2.0 | https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt |
| AndroidX Camera Video and Extensions 1.6.2, Camera Media3 effect 1.0.0-alpha04 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/camera |
| AndroidX Media3 Effect 1.9.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/media3 |
| AndroidX HEIF Writer 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/heifwriter |
| Robolectric 4.16.1 (test dependency) | MIT | https://github.com/robolectric/robolectric/blob/master/LICENSE |
| CameraX 1.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/camera |
| MediaPipe Tasks Vision 0.10.35 / bundled LiteRT | Apache-2.0 and transitive notices | https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE |
| EfficientDet-Lite0 float32 v1, COCO classes | Google MediaPipe model distribution; Apache-2.0 | https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector |
| ML Kit Face Detection 16.1.7, bundled | Free proprietary SDK; Google ML Kit and Google API terms; not represented as an open-source model | https://developers.google.com/ml-kit/terms and https://developers.google.com/terms |
| JUnit 4.13.2, test only | EPL-1.0 | https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt |

The unmodified bundled object model comes from https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite . Expected SHA-256: 40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58. MediaPipe handles its embedded normalization/label metadata.

Apache-2.0 text is included under android/app/src/main/assets/LICENSE-Apache-2.0.txt. SDK-provided notices and Flutter’s license registry remain available. Free development tooling does not imply every SDK is open source. ML Kit documents on-device image processing and SDK metrics; this application removes internet permission to block direct networking. Do not add internet permission without reassessing disclosures. Do not extract or reverse-engineer ML Kit’s model. No copied proprietary visual assets, advertising SDK or service credentials are included.
