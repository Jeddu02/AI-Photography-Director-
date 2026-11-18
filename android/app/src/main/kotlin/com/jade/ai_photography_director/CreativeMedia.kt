package com.jade.ai_photography_director

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

internal fun publishCreativeFile(ctx: Context,file:File,name:String,movie:Boolean):Uri {
    if(Build.VERSION.SDK_INT<29){
        val dest=File(File(ctx.filesDir,"photos").apply {mkdirs()},name)
        try{file.copyTo(dest);return FileProvider.getUriForFile(ctx,ctx.packageName+".photos",dest)}
        catch(e:Exception){dest.delete();throw e}
    }
    val resolver=ctx.contentResolver
    val collection=if(movie)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values=ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,when {movie->"video/mp4";name.endsWith(".heic")->"image/heic";else->"image/jpeg"})
        put(MediaStore.MediaColumns.RELATIVE_PATH,if(movie)"Movies/Photography Director" else "Pictures/Photography Director")
        put(MediaStore.MediaColumns.IS_PENDING,1)
    }
    val uri=requireNotNull(resolver.insert(collection,values))
    try {
        resolver.openOutputStream(uri)!!.use {out->file.inputStream().use {it.copyTo(out)}}
        values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);check(resolver.update(uri,values,null,null)>0)
        return uri
    } catch(e:Exception){resolver.delete(uri,null,null);throw e}
}
