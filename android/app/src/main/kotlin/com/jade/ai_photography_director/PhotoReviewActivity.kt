package com.jade.ai_photography_director

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

/**
 * Only opens the original for reading. Keep model/tracker lifetime independent of the live camera.
 */
class PhotoReviewActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val uri =
            intent.data
                ?: run {
                    finish()
                    return
                }
        val body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (20 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                fitsSystemWindows = true
            }
        val close =
            Button(this).apply {
                text = "Back to camera"
                setOnClickListener { finish() }
            }
        val photo =
            ImageView(this).apply {
                adjustViewBounds = true
                maxHeight = resources.displayMetrics.heightPixels / 2
                contentDescription = "Captured photo"
            }
        val report =
            TextView(this).apply {
                textSize = 18f
                text = "Analyzing photo on this device…"
            }
        body.addView(close)
        body.addView(
            photo,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        body.addView(report)
        setContentView(ScrollView(this).apply { addView(body) })
        val settings = DirectorSettings.load(this)
        worker.execute {
            try {
                val bitmap = readUpright(uri)
                val lines =
                    Vision(applicationContext).use {
                        SceneCoach.review(
                            it.analyze(bitmap, settings.mode),
                            settings.threshold,
                            settings.mode,
                        )
                    }
                runOnUiThread {
                    if (closed || isFinishing) bitmap.recycle()
                    else {
                        photo.setImageBitmap(bitmap)
                        report.text = "Photo review"
                        for (line in lines) {
                            val row =
                                LinearLayout(this).apply {
                                    gravity = android.view.Gravity.CENTER_VERTICAL
                                }
                            row.addView(
                                TextView(this).apply {
                                    text = line
                                    textSize = 16f
                                },
                                LinearLayout.LayoutParams(0, -2, 1f),
                            )
                            row.addView(
                                Button(this).apply {
                                    text = "Dismiss"
                                    contentDescription =
                                        "Dismiss ${line.substringBefore('·').trim()} suggestion"
                                    setOnClickListener { body.removeView(row) }
                                }
                            )
                            body.addView(row)
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!closed)
                        report.text = "Photo analysis unavailable. Your saved photo is unchanged."
                }
            }
        }
    }

    private fun readUpright(uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 1280) sample *= 2
        val decoded =
            contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            } ?: error("Unable to decode JPEG")
        val orientation =
            contentResolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            }
        val transform = Matrix()
        when (orientation) {
            2 -> transform.setScale(-1f, 1f)
            3 -> transform.setRotate(180f)
            4 -> transform.setScale(1f, -1f)
            5,
            7 -> {
                transform.setRotate(if (orientation == 5) 90f else -90f)
                transform.postScale(-1f, 1f)
            }
            6 -> transform.setRotate(90f)
            8 -> transform.setRotate(-90f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    override fun onDestroy() {
        closed = true
        worker.shutdown()
        super.onDestroy()
    }
}
