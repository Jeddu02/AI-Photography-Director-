package com.jade.ai_photography_director

import android.graphics.Bitmap
import android.media.*
import java.io.File

internal class IntervalMovie(private val file: File,private val w:Int,private val h:Int):AutoCloseable {
    private val codec=MediaCodec.createEncoderByType("video/avc")
    private var muxer:MediaMuxer?=null
    private var track=-1
    private var running=false
    private var frames=0L
    private val pixels=IntArray(w*h)
    init {
        try {
            codec.configure(MediaFormat.createVideoFormat("video/avc",w,h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_FRAME_RATE,30);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                setInteger(MediaFormat.KEY_BIT_RATE,2_000_000)
            },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer=MediaMuxer(file.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            codec.start();running=true
        } catch(e:Exception){close();throw e}
    }
    private fun drain(wait: Boolean=false):Boolean {
        val info=MediaCodec.BufferInfo()
        while(true){
            val out=codec.dequeueOutputBuffer(info,if(wait)10_000L else 0L)
            when {
                out==MediaCodec.INFO_TRY_AGAIN_LATER->return false
                out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{track=muxer!!.addTrack(codec.outputFormat);muxer!!.start()}
                out>=0->{
                    if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0)
                        muxer!!.writeSampleData(track,codec.getOutputBuffer(out)!!,info)
                    codec.releaseOutputBuffer(out,false)
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)return true
                }
            }
        }
    }
    fun append(bitmap:Bitmap) {
        require(bitmap.width==w && bitmap.height==h)
        drain()
        val slot=codec.dequeueInputBuffer(15_000);if(slot<0)return
        bitmap.getPixels(pixels,0,w,0,0,w,h)
        val image=requireNotNull(codec.getInputImage(slot))
        image.use {
            for((index,plane) in it.planes.withIndex()){
                val ss=if(index==0)1 else 2
                val data=plane.buffer;val base=data.position()
                for(y in 0 until h step ss)for(x in 0 until w step ss){
                    val rgb=pixels[y*w+x];val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255
                    val v=when(index){0->16+((66*r+129*g+25*b+128) shr 8);1->128+((-38*r-74*g+112*b+128) shr 8);else->128+((112*r-94*g-18*b+128) shr 8)}
                    data.put(base+y/ss*plane.rowStride+x/ss*plane.pixelStride,v.coerceIn(0,255).toByte())
                }
            }
        }
        codec.queueInputBuffer(slot,0,w*h*3/2,frames*1_000_000/30,0);frames++;drain()
    }
    fun finish(){
        require(frames>0)
        var slot=-1
        for(i in 0..100){drain();slot=codec.dequeueInputBuffer(10_000);if(slot>=0)break}
        check(slot>=0);codec.queueInputBuffer(slot,0,0,frames*1_000_000/30,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        for(i in 0..200)if(drain(true)){muxer!!.stop();track=-1;return}
        error("Encoder finalization timed out")
    }
    override fun close(){
        if(running)runCatching {codec.stop()};running=false
        runCatching {codec.release()};if(track>=0)runCatching {muxer?.stop()};runCatching {muxer?.release()};muxer=null
    }
}
