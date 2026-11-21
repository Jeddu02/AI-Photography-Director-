package com.jade.ai_photography_director

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w320dp-h700dp-port", application=Application::class)
class CameraLayoutTest {
    private fun buttons(view: View):List<Button> = when(view){
        is Button->listOf(view)
        is ViewGroup->(0 until view.childCount).flatMap {buttons(view.getChildAt(it))}
        else->emptyList()
    }
    @Test fun legacyManualPreferenceDoesNotDisableAiCam(){
        val app=org.robolectric.RuntimeEnvironment.getApplication()
        val prefs=app.getSharedPreferences("ai_camera",0)
        prefs.edit().putString("coachMode","Manual Composition").putString("shooting","Photo").commit()
        val activity=Robolectric.buildActivity(AiCameraActivity::class.java).setup()
        try {
            val root=activity.get().findViewById<ViewGroup>(android.R.id.content)
            fun findGuide(view:View):GuideView?{
                if(view is GuideView)return view
                if(view is ViewGroup)for(i in 0 until view.childCount)findGuide(view.getChildAt(i))?.let{return it}
                return null
            }
            assertEquals("Auto Director",findGuide(root)?.settings?.mode)
            buttons(root).single {it.contentDescription=="MORE"}.performClick()
            val modesDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(modesDialog)
            assertTrue(buttons(modesDialog.window!!.decorView).any { it.text.toString().contains("Manual") })
            modesDialog.dismiss()
        }finally {activity.pause().stop().destroy();prefs.edit().clear().commit()}
    }

    @Test fun primaryControlsFitANarrowPortraitScreen() {
        val activity=Robolectric.buildActivity(AiCameraActivity::class.java).setup()
        try {
            val root=activity.get().findViewById<View>(android.R.id.content)
            val density=root.resources.displayMetrics.density
            val width=(320*density).toInt();val height=(700*density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            root.layout(0,0,width,height)
            val controls=buttons(root)
            for(label in listOf("Back","Exposure compensation","Camera tools","AI CAM","Film and lighting","Open last capture","Capture photo","Switch camera")){
                val b=controls.single {it.contentDescription?.toString()==label}
                assertTrue("$label: ${b.width}x${b.height}px, density=$density", b.width>=47*density && b.height>=47*density)
            }
            controls.single {it.contentDescription=="Camera tools"}.performClick()
            val toolsDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(toolsDialog)
            assertTrue(buttons(toolsDialog.window!!.decorView).isNotEmpty())
            toolsDialog.dismiss()
            controls.single {it.contentDescription=="MORE"}.performClick()
            val modesDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(modesDialog)
            assertTrue(buttons(modesDialog.window!!.decorView).any { it.text.toString().contains("Time-lapse") })
            modesDialog.dismiss()
            assertFalse(controls.any { it.text=="Timer off" })
            assertFalse(controls.single {it.contentDescription=="Capture photo"}.isEnabled)
        } finally {activity.pause().stop().destroy()}
    }
}
