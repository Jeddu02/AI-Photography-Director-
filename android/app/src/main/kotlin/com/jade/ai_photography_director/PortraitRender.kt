package com.jade.ai_photography_director

import android.graphics.*

internal fun portraitMatrix(look:CameraLook,matte:PortraitMatte?,strength:Int):FloatArray {
    val base=look.copy(light=Lighting.NATURAL).matrix()
    if(matte==null || !look.light.mono)return base
    val t=strength.coerceIn(0,100)/100f
    for(col in 0..4){
        val grey=.2126f*base[col]+.7152f*base[5+col]+.0722f*base[10+col]
        for(row in 0..2){val i=row*5+col;base[i]+=t*(grey-base[i])}
    }
    return base
}

internal fun lightBitmap(m:PortraitMatte,style:Lighting,percent:Int):Bitmap =
    Bitmap.createBitmap(PortraitDirector.field(m,style,percent),m.side,m.side,Bitmap.Config.ARGB_8888)

internal fun uprightPhoto(source:Bitmap,orientation:Int):Bitmap {
    val transform=Matrix().apply {
        when(orientation){
            2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
            7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
        }
    }
    return Bitmap.createBitmap(source,0,0,source.width,source.height,transform,true)
}
