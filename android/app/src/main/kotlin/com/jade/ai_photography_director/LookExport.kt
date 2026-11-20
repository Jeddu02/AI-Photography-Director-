package com.jade.ai_photography_director

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

internal fun exportLook(context: Context, source: Uri, look: CameraLook, strength:Int): Uri {
    val resolver=context.contentResolver
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    resolver.openInputStream(source)!!.use { BitmapFactory.decodeStream(it,null,bounds) }
    require(bounds.outWidth>0 && bounds.outHeight>0)
    val orientation=resolver.openInputStream(source)!!.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)
    }
    val options=BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = 1
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val bitmapCount = if (orientation in 2..8) 3L else 2L
        val budget = (available / 2L).coerceAtLeast(1L)
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        while (pixels * 4L * bitmapCount / inSampleSize / inSampleSize > budget) {
            inSampleSize *= 2
        }
    }
    val src=resolver.openInputStream(source)!!.use { BitmapFactory.decodeStream(it,null,options) } ?: error("Decode failed")
    val file=File.createTempFile("look-", ".jpg",context.cacheDir)
    try {
        val upright=uprightPhoto(src,orientation)
        try {
            val matte=if((look.light==Lighting.NATURAL || strength==0) && look.blur==0)null else {
                val scale=minOf(1f,640f/maxOf(upright.width,upright.height))
                val sample=Bitmap.createScaledBitmap(upright,maxOf(1,(upright.width*scale).toInt()),maxOf(1,(upright.height*scale).toInt()),true)
                try { Vision(context.applicationContext).use {PortraitDirector.matte(it.analyze(sample,"Portrait"))} }
                finally {if(sample!==upright)sample.recycle()}
            }
            val dst=Bitmap.createBitmap(upright.width,upright.height,Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(dst)
                val matrix = portraitMatrix(look, matte, strength)
                if (look.adjustments.keys.any { it.advanced } || (look.blur > 0 && matte != null)) {
                    gradePhoto(upright, dst, look, matrix, matte)
                } else {
                    canvas.drawBitmap(upright, 0f, 0f, Paint().apply {
                        colorFilter = ColorMatrixColorFilter(matrix)
                    })
                }
                if(matte!=null && look.light!=Lighting.NATURAL && strength>0){
                    val field=lightBitmap(matte,look.light,strength)
                    try {canvas.drawBitmap(field,null,Rect(0,0,dst.width,dst.height),Paint(Paint.FILTER_BITMAP_FLAG))}
                    finally {field.recycle()}
                }
                file.outputStream().use {check(dst.compress(Bitmap.CompressFormat.JPEG,95,it))}
            } finally {dst.recycle()}
        } finally {if(upright!==src)upright.recycle()}
        copyCaptureMetadata(context, source, file, orientation)
        val name="Director_Look_"+System.currentTimeMillis()+".jpg"
        if(Build.VERSION.SDK_INT>=29) {
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Photography Director");put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            try {
                resolver.openOutputStream(uri)!!.use { out->file.inputStream().use { it.copyTo(out) } }
                values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);check(resolver.update(uri,values,null,null)>0)
                return uri
            } catch(e:Exception){resolver.delete(uri,null,null);throw e}
        }
        val output=File(File(context.filesDir,"photos").apply { mkdirs() },name)
        try { file.copyTo(output);return FileProvider.getUriForFile(context,context.packageName+".photos",output) }
        catch(e:Exception){output.delete();throw e}
    } finally {src.recycle();file.delete()}
}

private fun copyCaptureMetadata(context: Context, source: Uri, output: File, orientation: Int) {
    // The rendered image is already upright. Keep useful capture metadata and leave location out.
    runCatching {
        val tags = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
        )
        val target = ExifInterface(output.absolutePath)
        context.contentResolver.openInputStream(source)?.use { input ->
            val original = ExifInterface(input)
            tags.forEach { tag -> original.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
        }
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        }
        target.saveAttributes()
    }
}
