package com.jade.ai_photography_director

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CreativePreview(private val view: View) {
    private val shader = RuntimeShader(PROGRAM)
    private val emptyMask = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var previousMatte: PortraitMatte? = null
    private var mask = emptyMask

    fun apply(look: CameraLook, matrix: FloatArray, matte: PortraitMatte?) {
        val grade = CreativeGrade(look)
        val subject = matte.takeIf { grade.blur > 0f }
        if (subject !== previousMatte) {
            mask = subject?.let {
                Bitmap.createBitmap(
                    IntArray(it.alpha.size) { i -> ((it.alpha[i].coerceIn(0f, 1f) * 255f).toInt() shl 24) },
                    it.side, it.side, Bitmap.Config.ARGB_8888,
                )
            } ?: emptyMask
            previousMatte = subject
        }
        val width = view.width.coerceAtLeast(1).toFloat()
        val height = view.height.coerceAtLeast(1).toFloat()
        shader.setInputShader("mask", BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        })
        shader.setFloatUniform("maskSize", mask.width.toFloat())
        shader.setFloatUniform("size", width, height)
        for (row in 0..2) {
            val index = row * 5
            shader.setFloatUniform(listOf("r", "g", "b")[row], matrix[index], matrix[index + 1],
                matrix[index + 2], matrix[index + 4] / 255f)
        }
        shader.setFloatUniform("tones", grade.brilliance, grade.highlights, grade.shadows, grade.black)
        shader.setFloatUniform("detail", grade.sharpness, grade.definition, grade.denoise, grade.vibrance)
        shader.setFloatUniform("edges", grade.vignette, if (subject == null) 0f else grade.blur)
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "image"))
    }

    companion object {
        private const val PROGRAM = """
            uniform shader image;
            uniform shader mask;
            uniform float maskSize;
            uniform float2 size;
            uniform float4 r, g, b;
            uniform float4 tones, detail;
            uniform float2 edges;

            float3 readPhoto(float2 p) {
                return float3(image.eval(clamp(p, float2(.5), max(float2(.5), size - .5))).rgb);
            }
            float luminance(float3 rgb) {
                return dot(rgb, float3(.2126, .7152, .0722));
            }
            float matte(float2 p) {
                return mask.eval(p / size * maskSize).a;
            }
            float3 cleanBackground(float2 p, float2 delta, float3 base) {
                float2 sampleAt = p + delta;
                return mix(readPhoto(sampleAt), base, matte(sampleAt));
            }
            half4 main(float2 p) {
                float3 rgb = readPhoto(p);
                float y = luminance(rgb);
                float span = min(size.x, size.y);
                float fine = 0.;
                float broad = 0.;
                if (detail.x > 0. || detail.z > 0.) {
                    float2 dx = float2(max(1., span * .0018), 0.);
                    fine = y - luminance((readPhoto(p+dx) + readPhoto(p-dx) +
                        readPhoto(p+dx.yx) + readPhoto(p-dx.yx)) * .25);
                }
                if (detail.y > 0.) {
                    float2 dx = float2(max(1., span * .007), 0.);
                    broad = y - luminance((readPhoto(p+dx) + readPhoto(p-dx) +
                        readPhoto(p+dx.yx) + readPhoto(p-dx.yx)) * .25);
                }
                float texture = (.80 * detail.x - .85 * detail.z * clamp(1.-12.*abs(fine),0.,1.)) * fine
                    + .48 * detail.y * broad;
                if (edges.y > 0.) {
                    float2 d = float2(span * .016 * edges.y, 0.);
                    float3 blur = (cleanBackground(p,d,rgb) + cleanBackground(p,-d,rgb) +
                        cleanBackground(p,d.yx,rgb) + cleanBackground(p,-d.yx,rgb) +
                        cleanBackground(p,d+d.yx,rgb) + cleanBackground(p,-d-d.yx,rgb) +
                        cleanBackground(p,d-d.yx,rgb) + cleanBackground(p,-d+d.yx,rgb)) * .125;
                    float bg = 1.-matte(p);
                    rgb = mix(rgb, blur, bg*bg);
                    texture *= 1.-bg*bg;
                }
                float4 sampleColor = float4(rgb, 1.);
                float3 graded = clamp(float3(dot(r,sampleColor), dot(g,sampleColor), dot(b,sampleColor)),0.,1.);
                y = luminance(graded);
                float shadow = 1.-y;
                float tone = y*shadow*(.55*tones.z*shadow*shadow + .55*tones.y*y*y +
                    .24*tones.x*shadow - .55*tones.w*shadow*shadow*shadow);
                float spread = max(graded.r, max(graded.g, graded.b)) - min(graded.r, min(graded.g, graded.b));
                bool warm = graded.r >= graded.g && graded.g >= graded.b &&
                    graded.r - graded.g <= 1.8*(graded.g - graded.b);
                float color = 1. + .55*detail.w*(1.-spread)*(warm ? .25 : 1.);
                float2 centered = p / size * 2. - 1.;
                float edge = clamp((dot(centered, centered)-.22)/1.50,0.,1.);
                return half4(clamp((y + (graded-y)*color + tone + texture) *
                    (1.-.58*edges.x*edge*edge), 0., 1.), 1.);
            }
        """
    }
}
