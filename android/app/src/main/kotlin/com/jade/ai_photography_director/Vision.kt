package com.jade.ai_photography_director

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.*

class Vision(context: Context) : AutoCloseable {
    private val objects =
        ObjectDetector.createFromOptions(
            context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath("efficientdet.tflite").build()
                )
                .setMaxResults(10)
                .setScoreThreshold(.35f)
                .build(),
        )
    private val faces =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        )
    private val tracker = Tracker()
    private var previous: FloatArray? = null

    fun reset() {
        previous = null
        tracker.reset()
    }

    fun analyze(bitmap: Bitmap, mode: String): Features {
        val image = BitmapImageBuilder(bitmap).build()
        try {
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val subjects =
                objects
                    .detect(image)
                    .detections()
                    .mapNotNull { d ->
                        val category =
                            d.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                        val b = d.boundingBox()
                        Subject(
                            Box(
                                (b.left / w).coerceIn(0f, 1f),
                                (b.top / h).coerceIn(0f, 1f),
                                (b.right / w).coerceIn(0f, 1f),
                                (b.bottom / h).coerceIn(0f, 1f),
                            ),
                            category.score(),
                            category.categoryName(),
                        )
                    }
                    .toMutableList()
            if (mode in listOf("Portrait", "Group Photo", "Auto Director", "Street")) {
                val results = Tasks.await(faces.process(InputImage.fromBitmap(bitmap, 0)))
                subjects +=
                    results.map { f ->
                        val b = f.boundingBox
                        Subject(
                            Box(
                                (b.left / w).coerceIn(0f, 1f),
                                (b.top / h).coerceIn(0f, 1f),
                                (b.right / w).coerceIn(0f, 1f),
                                (b.bottom / h).coerceIn(0f, 1f),
                            ),
                            .9f,
                            "face",
                            face = true,
                            eyes =
                                if (
                                    f.leftEyeOpenProbability != null &&
                                        f.rightEyeOpenProbability != null
                                )
                                    min(f.leftEyeOpenProbability!!, f.rightEyeOpenProbability!!)
                                else null,
                            yaw = f.headEulerAngleY,
                        )
                    }
            }
            val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val pixels = IntArray(4096)
            small.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            if (small !== bitmap) small.recycle()
            val scene = SceneMap.fromPixels(pixels, 64)
            val luminance = scene.light
            val prev = previous
            val motion =
                if (prev == null) 1f
                else
                    luminance.indices.sumOf { abs(luminance[it] - prev[it]).toDouble() }.toFloat() /
                        4096
            previous = luminance
            var edges = 0
            var outer = 0
            var symmetry = 0f
            for (y in 1..62) for (x in 1..62) {
                val i = y * 64 + x
                if (x < 20 || x > 43) {
                    outer++
                    if (
                        abs(luminance[i] - luminance[i - 1]) +
                            abs(luminance[i] - luminance[i - 64]) > .2f
                    )
                        edges++
                }
                symmetry += abs(luminance[i] - luminance[y * 64 + 63 - x])
            }
            // A conservative image-edge candidate, not semantic horizon recognition.
            var best = 0f
            var runner = 0f
            var angle = 0
            for (slope in -12..12 step 2) {
                var lineBest = 0f
                for (row in 14..48) {
                    var energy = 0f
                    for (x in 4..59) {
                        val y =
                            (row + tan(Math.toRadians(slope.toDouble())) * (x - 32))
                                .toInt()
                                .coerceIn(1, 62)
                        energy += abs(luminance[y * 64 + x] - luminance[(y - 1) * 64 + x])
                    }
                    lineBest = max(lineBest, energy / 56)
                }
                if (lineBest > best) {
                    runner = best
                    best = lineBest
                    angle = slope
                } else runner = max(runner, lineBest)
            }
            val hc = if (best > .18f && best > runner * 1.12f) .7f else .2f
            return Features(
                tracker.update(subjects),
                luminance.average().toFloat(),
                luminance.count { it > .97f }.toFloat() / 4096,
                motion,
                if (hc > .65f)
                    Math.toDegrees(atan(tan(Math.toRadians(angle.toDouble())) * h / w)).toFloat()
                else null,
                hc,
                edges.toFloat() / outer,
                1 - symmetry / (62 * 62),
                scene,
            )
        } finally {
            image.close()
        }
    }

    override fun close() {
        objects.close()
        faces.close()
    }
}
