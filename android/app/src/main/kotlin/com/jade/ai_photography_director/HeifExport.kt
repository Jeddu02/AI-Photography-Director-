package com.jade.ai_photography_director

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import java.io.File

@RequiresApi(28)
internal object HeifExport {
    @DoNotInline
    fun save(ctx:Context,src:Uri):Uri {
        val resolver=ctx.contentResolver
        val info=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        resolver.openInputStream(src)!!.use {BitmapFactory.decodeStream(it,null,info)}
        val opts=BitmapFactory.Options().apply {
            inSampleSize=1
            while(maxOf(info.outWidth,info.outHeight)/inSampleSize>3072)inSampleSize*=2
        }
        val input=resolver.openInputStream(src)!!.use {BitmapFactory.decodeStream(it,null,opts)} ?: error("Decode failed")
        val file=File.createTempFile("heif-", ".heic",ctx.cacheDir)
        try {
            val orientation=resolver.openInputStream(src)!!.use {ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}
            val m=Matrix().apply {
                when(orientation){
                    2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
                    5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
                    7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
                }
            }
            val upright=Bitmap.createBitmap(input,0,0,input.width,input.height,m,true)
            try {
                HeifWriter.Builder(file.path,upright.width,upright.height,HeifWriter.INPUT_MODE_BITMAP)
                    .setMaxImages(1).setQuality(94).build().use {writer->
                        writer.start();writer.addBitmap(upright);writer.stop(10_000)
                    }
            }finally{if(upright!==input)upright.recycle()}
            return publishCreativeFile(ctx,file,"Director_"+System.currentTimeMillis()+".heic",false)
        }finally{input.recycle();file.delete()}
    }
}
