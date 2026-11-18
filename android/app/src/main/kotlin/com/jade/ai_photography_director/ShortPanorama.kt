package com.jade.ai_photography_director

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlin.math.*

internal data class Offset(val x:Int,val y:Int)
internal object PanoMatch {
    // Match middle strips in adjacent 64 × 48 images as the camera pans right.
    fun align(a:IntArray,b:IntArray):Offset? {
        if(a.size!=3072 || b.size!=3072)return null
        val matches=mutableListOf<Pair<Offset,Double>>()
        for(dx in 10..39) for(dy in -5..5){
            var sx=0.0;var sy=0.0;var xx=0.0;var yy=0.0;var xy=0.0;var n=0
            for(y in 6..40 step 2)for(x in dx+1..62 step 2){
                val u=a[y*64+x].toDouble();val v=b[(y-dy)*64+x-dx].toDouble()
                sx+=u;sy+=v;xx+=u*u;yy+=v*v;xy+=u*v;n++
            }
            val vx=xx-sx*sx/n;val vy=yy-sy*sy/n
            if(min(vx,vy)<n*30)continue
            matches+=Offset(dx,dy) to (xy-sx*sy/n)/sqrt(vx*vy)
        }
        val best=matches.maxByOrNull {it.second} ?: return null
        val other=matches.filter {abs(it.first.x-best.first.x)>2 || abs(it.first.y-best.first.y)>2}.maxOfOrNull {it.second} ?: -1.0
        return best.first.takeIf {best.second>.77 && best.second-other>.035}
    }
}

internal class ShortPanorama {
    private val images=mutableListOf<Bitmap>()
    private val positions=mutableListOf<Offset>()
    private var previous=IntArray(0)
    val count get()=images.size
    fun add(frame:Bitmap):Boolean {
        if(count>=5 || images.firstOrNull()?.let { it.width!=frame.width || it.height!=frame.height }==true)return false
        val small=Bitmap.createScaledBitmap(frame,64,48,true)
        val values=IntArray(3072);small.getPixels(values,0,64,0,0,64,48)
        if(small!==frame)small.recycle()
        for(i in values.indices){val v=values[i];values[i]=((v shr 16 and 255)*54+(v shr 8 and 255)*183+(v and 255)*19)/256}
        val shift=if(count==0)Offset(0,0) else PanoMatch.align(previous,values) ?: return false
        val last=positions.lastOrNull() ?: Offset(0,0)
        positions+=Offset(last.x+(shift.x*frame.width/64f).roundToInt(),last.y+(shift.y*frame.height/48f).roundToInt())
        previous=values;images+=frame
        return true
    }
    fun stitch():Bitmap {
        require(count>=2)
        val w=images.first().width;val h=images.first().height
        val top=positions.maxOf {it.y};val bottom=positions.minOf {it.y}+h
        val bitmap=Bitmap.createBitmap(positions.last().x+w,bottom-top,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        for(i in images.indices){
            val p=positions[i]
            val x=if(i==0)0 else (positions[i-1].x+w+p.x)/2-p.x
            canvas.drawBitmap(images[i],Rect(x,top-p.y,w,bottom-p.y),Rect(p.x+x,0,p.x+w,bottom-top),null)
        }
        return bitmap
    }
    fun clear(){images.forEach {it.recycle()};images.clear();positions.clear();previous=IntArray(0)}
}
