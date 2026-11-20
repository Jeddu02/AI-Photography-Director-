package com.jade.ai_photography_director

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.video.*
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.extensions.ExtensionMode
import androidx.camera.core.DynamicRange
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class, androidx.media3.common.util.UnstableApi::class)
class AiCameraActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("ai_camera", MODE_PRIVATE) }
    private val exporter = Executors.newSingleThreadExecutor()
    private lateinit var portraitLayer: android.widget.ImageView
    private var latestMatte: PortraitMatte?=null
    private var latestAuto: AutoGrade?=null
    private var analyzedAt=0L
    private var lightStrength=70
    private var creativePreview: CreativePreview? = null
    private var creativeGpuFailed = false
    private val creativeSupported get() = android.os.Build.VERSION.SDK_INT >= 33 &&
        preview.isHardwareAccelerated && !creativeGpuFailed
    private val stillLighting get()=shooting=="Manual" || shooting=="Photo" || shooting=="Portrait" || shooting=="Night"

    private fun refreshPortrait() {
        val matte=latestMatte
        if(!stillLighting || matte==null || look.light==Lighting.NATURAL || lightStrength==0){
            portraitLayer.setImageDrawable(null)
            portraitLayer.visibility=View.GONE
        } else {
            portraitLayer.setImageBitmap(lightBitmap(matte,look.light,lightStrength))
            portraitLayer.visibility=View.VISIBLE
        }
    }

    private fun showGradeRecommendation() {
        val proposal = latestAuto
        if (!streaming || proposal == null || SystemClock.elapsedRealtime() - analyzedAt > 3000) {
            AlertDialog.Builder(this).setTitle("Scene adjustments")
                .setMessage("Aim at your scene to get a suggestion from the live preview.")
                .setPositiveButton("OK", null).show()
            return
        }
        val proposedSession = session
        val proposed = proposal.look
        val notes = buildList {
            if (look.film == Film.STANDARD && proposed.film != Film.STANDARD) add(proposed.film.label)
            if (look.light == Lighting.NATURAL && proposed.light != Lighting.NATURAL) {
                add("${proposed.light.label} at ${proposal.strength}%")
            }
            proposed.adjustments.forEach { (control, value) ->
                if (look.value(control) == 0 && (!control.advanced || creativeSupported)) {
                    add("${control.label} ${if (value > 0) "+" else ""}$value")
                }
            }
        }
        val message = if (notes.isEmpty()) "Keep your current look. No new adjustments are suggested." else
            "${proposal.reason}\n\n${notes.joinToString(" · ")}"
        val dialog = AlertDialog.Builder(this).setTitle("Scene adjustments").setMessage(message)
            .setNegativeButton("Close", null)
        if (notes.isNotEmpty()) dialog.setPositiveButton("Apply") { _, _ ->
            if (!streaming || !stillLighting || session != proposedSession) {
                status.text = "Scene changed · refresh the suggestion"
                return@setPositiveButton
            }
            var next = look
            if (next.film == Film.STANDARD) next = next.copy(film = proposed.film)
            if (next.light == Lighting.NATURAL && proposed.light != Lighting.NATURAL) {
                next = next.copy(light = proposed.light)
                lightStrength = proposal.strength
            }
            proposed.adjustments.forEach { (control, value) ->
                if (next.value(control) == 0 && (!control.advanced || creativeSupported)) {
                    next = next.adjusted(control, value)
                }
            }
            closeTray()
            applyLook(next)
            status.text = "Suggested look applied · adjust any setting"
        }
        dialog.show()
    }
    private var look = CameraLook()
    private lateinit var dock: FrameLayout
    private lateinit var zoomRow: LinearLayout
    private lateinit var zoomControls: LinearLayout
    private lateinit var autoFrame: Button
    @Volatile private var autoFrameInFlight = false
    private var zoomGeneration = 0
    @Volatile private var frameAfterZoom = 0L
    private var dockPage="modes"
    private lateinit var modeControl: Button
    private lateinit var looksControl: Button
    private var extensions: ExtensionsManager?=null
    private var shooting="Photo"
    private val videoMode get()=shooting in listOf("Video","Cinematic","Slo-mo")
    private val displayRatio get()=if(videoMode) PhotoRatio.WIDE else ratio
    private var video: VideoCapture<Recorder>?=null
    private var recording: Recording?=null
    private var latestVideo: Uri?=null
    private var videoQuality=Quality.FHD
    private var fps=0
    private var cineEffect:androidx.camera.media3.effect.Media3Effect?=null
    private var stabilise=false
    private var focusDistance:Float?=null
    private var exposureLocked=false
    private var correction:Int?=null
    private var requestedSize:Size?=null
    private var format="JPEG"
    private var tray: View? = null
    private lateinit var ratioMenu: Button
    private lateinit var toolsButton: Button
    private val power by lazy { getSystemService(android.os.PowerManager::class.java) }
    private val analyzer = Executors.newSingleThreadExecutor()
    private val engine = CompositionEngine()
    private val main = Handler(Looper.getMainLooper())
    private var vision: Vision? = null // Accessed by the analyzer executor only.
    @Volatile private var session = 0
    @Volatile private var closed = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var flash = ImageCapture.FLASH_MODE_OFF
    private var lastAnalysisMs: Long? = null
    private var focusRequest = 0
    @Volatile private var focusPending = false
    @Volatile private var focusFailed = false
    private lateinit var analyzePhoto: Button
    private lateinit var exposure: Button
    private var takingPhoto = false
    private var timerSeconds = 0
    private var countdown: CountDownTimer? = null
    private var levelEnabled = false
    private var foreground = false
    @Volatile private var framingRevision = 0
    private var appliedFramingRevision = 0 // Accessed by the analysis executor.
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val gravitySensor by lazy {
        sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val levelListener =
        object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                val rotation = preview.display?.rotation ?: return
                guide.levelAngle = CameraLevel.roll(event.values[0], event.values[1], rotation)
            }
        }
    private var streaming = false
    private var lastPhoto: Uri? = null
    private var recommendation: RatioSuggestion? = null
    private lateinit var config: DirectorSettings
    private lateinit var aspectHost: FrameLayout
    private lateinit var viewfinder: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var guide: GuideView
    private lateinit var status: TextView
    private lateinit var shutter: Button
    private lateinit var review: Button
    private lateinit var ratioHint: Button
    private lateinit var flashButton: Button
    private lateinit var switchButton: Button
    private lateinit var gridButton: Button
    private lateinit var timerButton: Button
    private lateinit var levelButton: Button
    private lateinit var nextFrameButton: Button
    private val ratioButtons = mutableMapOf<PhotoRatio, Button>()
    private var ratio = PhotoRatio.CLASSIC
    private val portrait
        get() = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) initCamera() else status.text = "Camera access is needed to use AI Camera."
        }

    private val expireAdvice = Runnable {
        if (guide.advice.focusBounds != null || guide.advice.key == "ready") guide.clear("Frame your subject")
        latestMatte = null
        latestAuto = null
        if (look.light != Lighting.NATURAL || look.blur > 0) applyLook(look, false)
    }

    private data class Binding(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val lens: Int,
        val ratio: PhotoRatio,
    )

    private var bound: Binding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = DirectorSettings.load(this)
        val prefs = getSharedPreferences("ai_camera", MODE_PRIVATE)
        ratio =
            PhotoRatio.entries.firstOrNull { it.name == prefs.getString("ratio", null) }
                ?: PhotoRatio.CLASSIC
        timerSeconds = prefs.getInt("timer", 0).takeIf { it in listOf(0, 3, 10) } ?: 0
        levelEnabled = prefs.getBoolean("level", false) && gravitySensor != null
        lens = savedInstanceState?.getInt("lens", lens) ?: lens
        lastPhoto = savedInstanceState?.getString("photo")?.let(Uri::parse)
        val settings = prefs.readCreativeLook()
        look = settings.look
        lightStrength = settings.lightingStrength
        config=config.copy(grid=prefs.getBoolean("cameraGrid",config.grid),mode=prefs.getString("coachMode",config.mode)?.takeIf { it in DirectorSettings.modes && it!="Manual Composition" } ?: "Auto Director")
        shooting=prefs.getString("shooting","Photo")?.takeIf { it in listOf("Photo","Portrait","Video","Cinematic") } ?: "Photo"
        latestVideo=savedInstanceState?.getString("videoUri")?.let(Uri::parse)
        buildScreen()
        applyLook(look, false)
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
            initCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun buildScreen() {
        val root =
            LinearLayout(this).apply {
                orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                setBackgroundColor(Color.BLACK)
            }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val margin =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            view.setPadding(margin.left, margin.top, margin.right, margin.bottom)
            insets
        }
        val controls =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, dp(8), dp(2))
            }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun top(control: Button) = header.addView(control, LinearLayout.LayoutParams(0,dp(48),1f))
        top(icon("‹","Back"){finish()})
        top(icon("ϟ","Flash"){if(flashButton.isEnabled)flashButton.performClick() else status.text="Flash unavailable"})
        exposure=icon("±","Exposure compensation"){showExposure()};top(exposure)
        toolsButton=icon("⌃","Camera tools"){if(dockPage=="tools")modeStrip() else showTools()};top(toolsButton)
        looksControl=icon("◐","Film and lighting"){if(shooting=="Pano")finishPanorama() else openLooks()};top(looksControl)
        ratioMenu=icon(ratio.label(portrait),"Photo ratio"){
            val choices=PhotoRatio.entries
            pick("Photo ratio",choices.map {it.label(portrait)},choices.indexOf(ratio)){chooseRatio(choices[it])}
        };top(ratioMenu)
        if (portrait) root.addView(header) else controls.addView(header)
        viewfinder = FrameLayout(this)
        preview =
            PreviewView(this).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                contentDescription = "Camera viewfinder. Tap to focus or pinch to zoom."
            }
        guide =
            GuideView(this).apply {
                settings = config
                previewArea = Box(0f, 0f, 1f, 1f)
                showSubjectBadge = true
                clear("Opening camera")
            }
        viewfinder.addView(preview, FrameLayout.LayoutParams(-1, -1))
        portraitLayer=android.widget.ImageView(this).apply {
            scaleType=android.widget.ImageView.ScaleType.FIT_XY
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        viewfinder.addView(portraitLayer,FrameLayout.LayoutParams(-1,-1))
        viewfinder.addView(guide, FrameLayout.LayoutParams(-1, -1))
        zoomControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        zoomRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xb8202124.toInt())
            }
        }
        autoFrame = button("Auto frame", ::applyAutoFrame).apply {
            textSize = 12f
            setTextColor(Color.rgb(247, 207, 65))
            contentDescription = "Auto frame the detected subject"
        }
        zoomControls.addView(zoomRow, LinearLayout.LayoutParams(-2, dp(48)))
        zoomControls.addView(autoFrame, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(6) })
        viewfinder.addView(zoomControls, FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(44)
        })
        aspectHost =
            object : FrameLayout(this) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                        val (w, h) = displayRatio.fit(measuredWidth, measuredHeight, portrait)
                        viewfinder.measure(
                            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
                        )
                    }
                }
                .apply {
                    setBackgroundColor(Color.BLACK)
                    addView(viewfinder, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                }
        root.addView(
            aspectHost,
            if (portrait) LinearLayout.LayoutParams(-1, 0, 1f)
            else LinearLayout.LayoutParams(0, -1, 1f),
        )
        ratioHint=button("") { recommendation?.let { chooseRatio(it.ratio) } }
        flashButton = button("Flash off") { cycleFlash() }
        switchButton = icon("⟳","Switch camera") { flipCamera() }
        gridButton =
            button(if (config.grid) "Grid on" else "Grid off") {
                config = config.copy(grid = !config.grid)
                prefs.edit().putBoolean("cameraGrid",config.grid).apply()
                guide.settings = config
                guide.invalidate()
                gridButton.text = if (config.grid) "Grid on" else "Grid off"
            }
        timerButton =
            button(timerLabel()) {
                timerSeconds =
                    when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                getSharedPreferences("ai_camera", MODE_PRIVATE)
                    .edit()
                    .putInt("timer", timerSeconds)
                    .apply()
                timerButton.text = timerLabel()
            }
        levelButton =
            button(if (levelEnabled) "Level on" else "Level off") {
                levelEnabled = !levelEnabled
                getSharedPreferences("ai_camera", MODE_PRIVATE)
                    .edit()
                    .putBoolean("level", levelEnabled)
                    .apply()
                levelButton.text = if (levelEnabled) "Level on" else "Level off"
                updateLevelSensor()
            }
        nextFrameButton = button("Find another shot") { nextFrame() }
        val shotChoicesButton = button("Choose composition") {
            val choices = engine.shotChoices()
            if (choices.isEmpty()) {
                status.text = "Scan the scene first to find composition options"
            } else {
                val list = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Choose composition")
                    .setView(android.widget.ScrollView(this).apply { addView(list) })
                    .setNegativeButton("Cancel", null)
                    .create()
                choices.forEachIndexed { index, proposal ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(8), dp(12), dp(12))
                        contentDescription = "Composition ${index + 1}: ${proposal.reason}"
                        isClickable = true
                        isFocusable = true
                    }
                    val title = android.widget.TextView(this).apply {
                        text = "${index + 1}. ${proposal.reason.ifBlank { "Suggested framing" }}"
                        textSize = 16f
                        setTextColor(if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
                    }
                    card.addView(title)
                    val previewDiagram = object : View(this) {
                        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        override fun onDraw(canvas: android.graphics.Canvas) {
                            super.onDraw(canvas)
                            val inset = dp(8).toFloat()
                            val w = (width - inset * 2).coerceAtLeast(0f)
                            val h = (height - inset * 2).coerceAtLeast(0f)
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = dp(2).toFloat()
                            paint.color = Color.LTGRAY
                            canvas.drawRect(inset, inset, inset + w, inset + h, paint)
                            paint.color = Color.rgb(255, 193, 7)
                            paint.strokeWidth = dp(3).toFloat()
                            val f = proposal.frame
                            // Crop proposals may extend beyond the current viewfinder.
                            // Clip the diagram so it remains readable in the selection card.
                            canvas.save()
                            canvas.clipRect(inset, inset, inset + w, inset + h)
                            canvas.drawRect(inset + f.l * w, inset + f.t * h,
                                inset + f.r * w, inset + f.b * h, paint)
                            canvas.restore()
                        }
                    }
                    card.addView(previewDiagram, LinearLayout.LayoutParams(-1, dp(82)))
                    card.setOnClickListener {
                        if (streaming && !takingPhoto && countdown == null) {
                            val revision = ++framingRevision
                            guide.clear("Aligning selected composition")
                            analyzer.execute {
                                if (!closed && revision == framingRevision) {
                                    engine.selectShot(index)
                                    appliedFramingRevision = revision
                                }
                            }
                        }
                        dialog.dismiss()
                    }
                    list.addView(card)
                    if (index < choices.lastIndex) list.addView(View(this).apply {
                        setBackgroundColor(Color.DKGRAY)
                    }, LinearLayout.LayoutParams(-1, dp(1)))
                }
                dialog.show()
            }
        }
        val why =
            button("Why this frame?") {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Why this frame?")
                    .setMessage(
                        listOfNotNull(
                                guide.advice.reason.ifBlank { guide.advice.text },
                                lastAnalysisMs?.let { "Last preview analysis: ${it} ms" },
                            )
                            .joinToString("\n\n")
                    )
                    .setPositiveButton("Close", null)
                    .show()
            }
        analyzePhoto =
            button("Analyze photo") {
                lastPhoto?.let {
                    startActivity(Intent(this, PhotoReviewActivity::class.java).setData(it))
                }
            }
        moreActions = listOf(flashButton, gridButton, timerButton, levelButton, shotChoicesButton, nextFrameButton, why, analyzePhoto)
        modeControl=button("More"){chooseMode()}
        dock=FrameLayout(this)
        controls.addView(dock,LinearLayout.LayoutParams(-1,-2))
        modeStrip()
        val actionRow = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        shutter = button("●") { when(shooting){"Pano"->addPanoramaFrame();"Time-lapse"->beginInterval();else->if(videoMode)toggleRecording() else requestCapture()} }.apply {
            contentDescription="Capture photo"; textSize=40f;setTextColor(Color.WHITE)
            background=getDrawable(R.drawable.camera_shutter)
            setTextColor(Color.TRANSPARENT)

        }
        review=icon("▧","Open last capture") { openPhoto() }
        actionRow.addView(review,LinearLayout.LayoutParams(0,dp(48),1f))
        actionRow.addView(shutter,LinearLayout.LayoutParams(dp(64),dp(64)).apply {setMargins(dp(12),dp(4),dp(12),dp(4))})
        actionRow.addView(switchButton,LinearLayout.LayoutParams(0,dp(48),1f))
        controls.addView(actionRow)
        status =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                text = "Tap to focus · pinch to zoom"
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        status.gravity=Gravity.CENTER
        status.setTextColor(Color.LTGRAY);status.textSize=12f
        controls.addView(status, LinearLayout.LayoutParams(-1,dp(24)))
        if (portrait) root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        else
            root.addView(
                ScrollView(this).apply { addView(controls) },
                LinearLayout.LayoutParams(dp(264), -1),
            )
        setContentView(root)
        updateRatioUi()
        updateControls()
        viewfinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            preview.post { bindCamera(); applyLook(look, false) }
        }
        preview.previewStreamState.observe(this) {
            streaming = it == PreviewView.StreamState.STREAMING
            if (!streaming) cancelCountdown()
            updateControls()
        }
        installGestures()
    }

    private fun initCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                if (closed || isFinishing) return@addListener
                try {
                    provider = future.get()
                    val available = provider ?: return@addListener
                    if (
                        !available.hasCamera(selector()) &&
                            available.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    )
                        lens = CameraSelector.LENS_FACING_FRONT
                    val ext=ExtensionsManager.getInstanceAsync(this,available)
                    ext.addListener({
                        if(!closed) {
                            extensions=runCatching {ext.get()}.getOrNull()
                            if(shooting=="Portrait" && !takingPhoto){bound=null;bindCamera()}
                        }
                    },uiExecutor)
                    bindCamera()
                } catch (_: Exception) {
                    status.text = "Unable to open the camera on this device."
                    guide.clear("Camera unavailable")
                }
            },
            uiExecutor,
        )
    }

    private fun selector() = CameraSelector.Builder().requireLensFacing(lens).build()

    private fun bindCamera() {
        val available = provider ?: return
        if (closed || isFinishing || takingPhoto || recording!=null || intervalActive || preview.width <= 0 || preview.height <= 0)
            return
        val rotation = preview.display?.rotation ?: return
        val next = Binding(preview.width, preview.height, rotation, lens, displayRatio)
        if (bound == next) return
        val viewPort = preview.getViewPort(rotation) ?: return
        clearAutoFrame()
        cancelCountdown()
        focusRequest++
        focusPending = false
        focusFailed = false
        lastAnalysisMs = null
        panorama.clear()
        latestAuto=null;latestMatte=null;applyLook(look,false)
        val token = ++session
        val mirror = lens == CameraSelector.LENS_FACING_FRONT
        val settings = when(shooting){
            "Manual"->config.copy(mode="Manual Composition")
            "Portrait"->config.copy(mode="Portrait")
            else->config
        }
        guide.settings=settings
        streaming = false
        capture = null
        video=null
        camera = null
        guide.clear("Frame your subject")
        setRecommendation(null)
        updateControls()
        analysis?.clearAnalyzer()
        available.unbindAll()
        cineEffect?.close();cineEffect=null
        try {
            val previewCase =
                Preview.Builder().setTargetRotation(rotation).build().apply {
                    setSurfaceProvider(preview.surfaceProvider)
                }
            if(shooting in listOf("Time-lapse","Pano")){
                camera=available.bindToLifecycle(this,selector(),previewCase);bound=next;guide.visibility=View.INVISIBLE
                status.text=if(shooting=="Pano")"Capture first frame · then pan right" else "Silent time-lapse · 2 samples/s"
                updateControls();return
            }
            if(videoMode) {
                val info=available.getCameraInfo(selector())
                val caps=if(shooting=="Slo-mo") Recorder.getHighSpeedVideoCapabilities(info) else Recorder.getVideoCapabilities(info)
                val choices=caps?.getSupportedQualities(DynamicRange.SDR).orEmpty()
                val quality=videoQuality.takeIf { it in choices } ?: choices.first()
                val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
                val vc=VideoCapture.Builder(recorder).setTargetRotation(rotation).apply {
                    if(shooting!="Slo-mo"){
                        if(stabilise && caps?.isStabilizationSupported==true)setVideoStabilizationEnabled(true)
                        val rate=if(shooting=="Cinematic" && fps==0)24 else fps
                        val range=android.util.Range(rate,rate)
                        if(rate>0 && range in info.supportedFrameRateRanges)setTargetFrameRate(range)
                    }
                }.build()
                camera=if(shooting=="Slo-mo") available.bindToLifecycle(this,selector(),
                    HighSpeedVideoSessionConfig.Builder(vc).setPreview(previewCase).setSlowMotionEnabled(true).build())
                else if(shooting=="Cinematic"){
                    val effect=androidx.camera.media3.effect.Media3Effect(this,androidx.camera.core.CameraEffect.PREVIEW or androidx.camera.core.CameraEffect.VIDEO_CAPTURE,uiExecutor) {
                        status.text="Cinematic effect unavailable · choose Video";recording?.stop()
                    }
                    cineEffect=effect;updateCinema()
                    available.bindToLifecycle(this,selector(),UseCaseGroup.Builder().addUseCase(previewCase).addUseCase(vc).addEffect(effect).build())
                } else available.bindToLifecycle(this,selector(),previewCase,vc)
                video=vc;bound=next
                status.text="Silent video · "+qualityLabel(quality)
                guide.visibility=View.INVISIBLE
                updateControls()
                return
            }
            guide.visibility=View.VISIBLE
            val photo =
                ImageCapture.Builder().setTargetRotation(rotation).setFlashMode(flash).apply {
                    if(shooting=="Photo" && format=="RAW + JPEG")setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                    if(shooting=="Photo")requestedSize?.let { size->
                        setResolutionSelector(ResolutionSelector.Builder().setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                            .setResolutionStrategy(ResolutionStrategy(size,ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                    }
                }.build()
            val frames =
                ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
            var lastAnalysis = 0L
            val revision = framingRevision
            analyzer.execute {
                engine.reset(clearSelection = true)
                appliedFramingRevision = revision
                vision?.reset()
            }
            var processingInterval = settings.interval
            frames.setAnalyzer(analyzer) { frame ->
                val now = SystemClock.elapsedRealtime()
                if (closed || session != token || autoFrameInFlight || now < frameAfterZoom ||
                    now - lastAnalysis < processingInterval) {
                    frame.close()
                } else {
                    lastAnalysis = now
                    analyze(frame, token, mirror, settings, now)
                    val cost = SystemClock.elapsedRealtime() - now
                    val hot =
                        Build.VERSION.SDK_INT >= 29 &&
                            power.currentThermalStatus >=
                                android.os.PowerManager.THERMAL_STATUS_SEVERE
                    processingInterval =
                        maxOf(
                            settings.interval,
                            (cost * 2).coerceAtMost(1800),
                            if (hot) 1500L else 0L,
                        )
                }
            }
            val extMode=when(shooting) {
                "Night"->ExtensionMode.NIGHT
                "Portrait"->ExtensionMode.BOKEH
                else->ExtensionMode.NONE
            }
            val ext=extensions
            val useExt=extMode!=ExtensionMode.NONE && ext?.isExtensionAvailable(selector(),extMode)==true && (shooting!="Portrait" || ext.isImageAnalysisSupported(selector(),extMode))
            val canAnalyze=!useExt || ext!!.isImageAnalysisSupported(selector(),extMode)
            val group=UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(previewCase).addUseCase(photo)
            if(canAnalyze)group.addUseCase(frames) else guide.clear("Frame manually · camera effect active")
            camera=available.bindToLifecycle(this,if(useExt)ext!!.getExtensionEnabledCameraSelector(selector(),extMode) else selector(),group.build())
            val ev=camera!!.cameraInfo.exposureState
            if(ev.isExposureCompensationSupported && prefs.contains("ev$lens"))
                camera!!.cameraControl.setExposureCompensationIndex(ev.exposureCompensationRange.clamp(prefs.getInt("ev$lens",0)))
            if(shooting=="Photo") applyLensControls()
            analysis = frames
            capture = photo
            bound = next
            status.text = "Tap to focus · pinch to zoom"
        } catch (_: Exception) {
            status.text = "Camera combination unavailable. Choose Photo or reset resolution in Tools."
            guide.clear("Camera unavailable")
            bound = null
        }
        updateControls()
    }

    private fun analyze(
        frame: ImageProxy,
        token: Int,
        mirror: Boolean,
        settings: DirectorSettings,
        now: Long,
    ) {
        var full: Bitmap? = null
        var photoArea: Bitmap? = null
        try {
            val crop = frame.cropRect
            full = frame.toBitmap()
            val turn =
                Matrix().apply {
                    postRotate(frame.imageInfo.rotationDegrees.toFloat())
                    if (mirror) postScale(-1f, 1f)
                }
            photoArea =
                Bitmap.createBitmap(
                    full,
                    crop.left,
                    crop.top,
                    crop.width(),
                    crop.height(),
                    turn,
                    true,
                )
            val revision = appliedFramingRevision
            val detector = vision ?: Vision(applicationContext).also { vision = it }
            val features = detector.analyze(photoArea, settings.mode)
            val matte=PortraitDirector.matte(features)
            val auto=PortraitDirector.auto(features,matte)
            val advice =
                engine.evaluate(
                    features,
                    settings.mode,
                    settings.preference,
                    settings.threshold,
                    now,
                )
            val analysisMs = SystemClock.elapsedRealtime() - now
            main.post {
                if (
                    !closed &&
                        session == token &&
                        revision == framingRevision &&
                        streaming &&
                        !takingPhoto &&
                        !autoFrameInFlight &&
                        now >= frameAfterZoom
                ) {
                    latestMatte=matte;latestAuto=auto;analyzedAt=now
                    if(look.light!=Lighting.NATURAL || look.blur > 0)applyLook(look,false)
                    lastAnalysisMs = analysisMs
                    guide.advice =
                        if (advice.key == "ready" && (focusPending || focusFailed))
                            advice.copy(
                                key = "focus",
                                text =
                                    if (focusPending) "Focusing" else "Tap the subject to refocus",
                                level = 1,
                            )
                        else advice
                    guide.subjects = features.subjects
                    guide.horizon = features.horizon
                    setRecommendation(
                        suggestPhotoRatio(
                            features,
                            ratio,
                            portrait,
                            settings.mode,
                            settings.threshold,
                        )
                    )
                    main.removeCallbacks(expireAdvice)
                    main.postDelayed(expireAdvice, maxOf(2000L, settings.interval * 3))
                }
            }
        } catch (_: Exception) {
            main.post {
                if (!closed && session == token) {
                    setRecommendation(null)
                    latestAuto=null;latestMatte=null;applyLook(look,false)
                    guide.clear("Guidance unavailable · you can still capture")
                }
            }
        } finally {
            if (photoArea !== full) photoArea?.recycle()
            full?.recycle()
            frame.close()
        }
    }

    private fun setRecommendation(value: RatioSuggestion?) {
        if (recommendation == value) return
        recommendation = value
        ratioHint.visibility = if (value == null) View.INVISIBLE else View.VISIBLE
        if (value != null) ratioHint.text = "Try ${value.ratio.label(portrait)} · ${value.reason}"
    }

    private fun chooseRatio(choice: PhotoRatio) {
        if (ratio == choice || takingPhoto || countdown != null) return
        clearAutoFrame()
        session++
        streaming = false
        capture = null
        closeTray()
        ratio = choice
        getSharedPreferences("ai_camera", MODE_PRIVATE)
            .edit()
            .putString("ratio", ratio.name)
            .apply()
        setRecommendation(null)
        guide.clear("Frame your subject")
        updateRatioUi()
        updateControls()
        aspectHost.requestLayout()
    }

    private fun flipCamera() {
        focusRequest++
        focusPending = false
        focusFailed = false
        val target =
            if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        if (
            provider?.hasCamera(CameraSelector.Builder().requireLensFacing(target).build()) != true
        ) {
            status.text = "Another camera is unavailable."
            return
        }
        closeTray()
        focusDistance=null;exposureLocked=false;correction=null;requestedSize=null;format="JPEG"
        if(shooting=="Night" || shooting=="Slo-mo"){shooting="Photo";modeStrip();applyLook(look,false);updateRatioUi();aspectHost.requestLayout()}
        lens = target
        flash = ImageCapture.FLASH_MODE_OFF
        flashButton.text = "Flash off"
        bindCamera()
    }

    private fun cycleFlash() {
        val photo = capture ?: return
        if (camera?.cameraInfo?.hasFlashUnit() != true) return
        flash =
            when (flash) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
        photo.flashMode = flash
        flashButton.text =
            when (flash) {
                ImageCapture.FLASH_MODE_AUTO -> "Flash auto"
                ImageCapture.FLASH_MODE_ON -> "Flash on"
                else -> "Flash off"
            }
    }

    private fun timerLabel() = if (timerSeconds == 0) "Timer off" else "Timer ${timerSeconds}s"

    private fun nextFrame() {
        if (!streaming || takingPhoto || countdown != null) return
        // Queue the reset on the same thread as the framing engine.
        val revision = ++framingRevision
        guide.clear("Scanning for another composition")
        analyzer.execute {
            if (!closed && revision == framingRevision) {
                engine.nextShot()
                appliedFramingRevision = revision
            }
        }
    }

    private fun requestCapture() {
        if (countdown != null) {
            cancelCountdown()
            status.text = "Timer cancelled"
            return
        }
        if (capture == null || !streaming || takingPhoto || autoFrameInFlight) return
        if (timerSeconds == 0) {
            takePhoto()
            return
        }
        shutter.text = "×"
        shutter.contentDescription = "Cancel timer"
        status.text = "Taking photo in ${timerSeconds}s"
        countdown =
            object : CountDownTimer(timerSeconds * 1000L, 200L) {
                    override fun onTick(remaining: Long) {
                        status.text = "Taking photo in ${(remaining + 999) / 1000}s"
                    }

                    override fun onFinish() {
                        countdown = null
                        shutter.text = "●"
        shutter.contentDescription = "Capture photo"
                        updateControls()
                        if (foreground && streaming) takePhoto()
                    }
                }
                .start()
        updateControls()
    }

    private fun cancelCountdown() {
        countdown?.cancel() ?: return
        countdown = null
        shutter.text = "●"
        shutter.contentDescription = "Capture photo"
        updateControls()
    }

    private fun updateLevelSensor() {
        val manager = sensors ?: return
        manager.unregisterListener(levelListener)
        guide.levelAngle = null
        val sensor = gravitySensor ?: return
        if (foreground && levelEnabled)
            manager.registerListener(levelListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun takePhoto() {
        val photo = capture ?: return
        if (takingPhoto || !streaming) return
        val name = "Director_${System.currentTimeMillis()}.jpg"
        val metadata =
            ImageCapture.Metadata().apply {
                isReversedHorizontal = lens == CameraSelector.LENS_FACING_FRONT
            }
        var local: File? = null
        val output =
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_PICTURES}/Photography Director",
                            )
                        }
                    ImageCapture.OutputFileOptions.Builder(
                            contentResolver,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values,
                        )
                        .setMetadata(metadata)
                        .build()
                } else {
                    val directory = File(filesDir, "photos").apply { mkdirs() }
                    val file = File(directory, name)
                    local = file
                    ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
                }
            } catch (_: Exception) {
                status.text = "Unable to prepare photo storage."
                return
            }
        takingPhoto = true
        updateControls()
        status.text = "Saving photo"
        val shotStrength=lightStrength
        val shotLook = if (creativeSupported) look else look.copy(
            blur = 0, adjustments = look.adjustments.filterKeys { !it.advanced },
        )
        val savedRatio = ratio.label(portrait)
        val heif=shooting=="Photo" && format=="HEIF copy" && Build.VERSION.SDK_INT>=28
        val callback=object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    if (closed || result.imageFormat==android.graphics.ImageFormat.RAW_SENSOR) return
                    lastPhoto =
                        local?.let {
                            FileProvider.getUriForFile(
                                this@AiCameraActivity,
                                "${packageName}.photos",
                                it,
                            )
                        } ?: result.savedUri
                    val source=lastPhoto
                    fun finishSave(message: String, uri: Uri?) {
                        if (closed) return
                        lastPhoto=uri
                        takingPhoto=false
                        status.text=message
                        updateControls()
                        bindCamera()
                    }
                    if(source!=null && (shotLook!=CameraLook() || heif)) {
                        exporter.execute {
                            val styled=if(shotLook!=CameraLook())runCatching { exportLook(applicationContext,source,shotLook,shotStrength) }.getOrNull() else source
                            val compressed=if(styled!=null && heif && Build.VERSION.SDK_INT>=28)runCatching {HeifExport.save(applicationContext,styled)}.getOrNull() else null
                            val message=when {styled==null->"Original saved · style export failed";heif && compressed==null->"JPEG saved · HEIF unavailable";heif->"JPEG and HEIF saved";else->"Original and styled photo saved"}
                            main.post { finishSave(message,compressed ?: styled ?: source) }
                        }
                    } else finishSave("Saved · $savedRatio",source)
                }

                override fun onError(exception: ImageCaptureException) {
                    if(photo.outputFormat!=ImageCapture.OUTPUT_FORMAT_RAW_JPEG)local?.delete()
                    if (closed) return
                    takingPhoto = false
                    status.text = "Photo could not be saved. Try again."
                    updateControls()
                    bindCamera()
                }
            }
        try {
            if(photo.outputFormat==ImageCapture.OUTPUT_FORMAT_RAW_JPEG){
                val dng=name.removeSuffix(".jpg")+".dng"
                val raw=if(Build.VERSION.SDK_INT>=29)ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,dng);put(MediaStore.Images.Media.MIME_TYPE,"image/x-adobe-dng")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Photography Director")
                }).build() else ImageCapture.OutputFileOptions.Builder(File(File(filesDir,"photos"),dng)).build()
                photo.takePicture(raw,output,uiExecutor,callback)
            }else photo.takePicture(output,uiExecutor,callback)
        }catch(e:Exception){takingPhoto=false;status.text="Photo capture unavailable";updateControls()}
    }

    private fun openPhoto() {
        val uri = (if(videoMode || shooting=="Time-lapse") latestVideo else lastPhoto) ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, if(videoMode || shooting=="Time-lapse")"video/mp4" else contentResolver.getType(uri) ?: "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            status.text = "No photo viewer found."
        }
    }

    private var moreActions = emptyList<Button>()

    private fun pick(title: String, options: List<String>, selected: Int, select: (Int)->Unit) {
        strip(title,options,selected){ index ->modeStrip();select(index) }

    }

    private fun applyLensControls() {
        val active=camera ?: return
        val requests=CaptureRequestOptions.Builder().apply {
            focusDistance?.let {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,it)
            }
            if(exposureLocked)setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,true)
            if(Build.VERSION.SDK_INT>=28)correction?.let { setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE,it) }
        }.build()
        val result=Camera2CameraControl.from(active.cameraControl).setCaptureRequestOptions(requests)
        result.addListener({
            if(active===camera && !closed && runCatching {result.get()}.isFailure)
                status.text="Lens setting could not be applied"
        },uiExecutor)
    }

    private fun addLensTools(actions:MutableList<Button>) {
        val info=camera?.cameraInfo ?: return
        val formats=mutableListOf("JPEG")
        if(Build.VERSION.SDK_INT>=28 && android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { mime->mime=="image/vnd.android.heic" || mime=="video/hevc" } }) formats.add("HEIF copy")
        if(ImageCapture.OUTPUT_FORMAT_RAW_JPEG in ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats)formats.add("RAW + JPEG")
        if(formats.size>1)actions.add(button("Format · "+format) {
            pick("Photo format",formats,formats.indexOf(format)) {format=formats[it];bound=null;bindCamera()}
        })
        val hw=Camera2CameraInfo.from(info)
        val nearest=hw.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val modes=hw.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if(nearest>0f && modes?.contains(CaptureRequest.CONTROL_AF_MODE_OFF)==true){
            actions.add(button("Manual focus") {
                slider("Focus",0,100,((focusDistance ?: 0f)*100/nearest).roundToInt(),
                    { if(it==0)"Far" else if(it==100)"Nearest" else "$it%" }) {
                    focusDistance=it*nearest/100f;applyLensControls()
                }
            })
            actions.add(button("Near focus") { focusDistance=nearest;applyLensControls();status.text="Nearest focus · tap to refocus" })
        }
        if(hw.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)==true)
            actions.add(button(if(exposureLocked)"Unlock exposure" else "Lock exposure") {
                exposureLocked=!exposureLocked;applyLensControls();status.text=if(exposureLocked)"Exposure locked" else "Auto exposure"
            })
        if(Build.VERSION.SDK_INT>=28){
            val supported=hw.getCameraCharacteristic(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES).orEmptyArray()
            if(supported.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_OFF) && supported.any { it!=0 })
                actions.add(button("Lens correction") {
                    pick("Lens correction",listOf("Camera default","Off","On"),when(correction){null->0;0->1;else->2}) {
                        correction=when(it){0->null;1->0;else->supported.last { v->v!=0 }}
                        applyLensControls()
                    }
                })
        }
        val map=hw.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes=(map?.getOutputSizes(android.graphics.ImageFormat.JPEG).orEmpty().toList()+
            map?.getHighResolutionOutputSizes(android.graphics.ImageFormat.JPEG).orEmpty().toList())
            .filter { it.width>=it.height }.sortedByDescending { it.width.toLong()*it.height }
            .distinctBy { (it.width.toDouble()*it.height/1e6).roundToInt() }.take(12)
        if(sizes.isNotEmpty())actions.add(button("Photo resolution") {
            val current=capture?.resolutionInfo?.resolution
            pick("Target sensor size"+(current?.let { " · active ${it.width}×${it.height}" } ?: ""),
                listOf("Auto")+sizes.map { "%.1f MP · %d×%d".format(it.width.toDouble()*it.height/1e6,it.width,it.height) },
                requestedSize?.let { sizes.indexOf(it)+1 } ?: 0) {
                requestedSize=sizes.getOrNull(it-1);bound=null;bindCamera()
            }
        })
    }
    private fun IntArray?.orEmptyArray()=this ?: intArrayOf()

    override fun onKeyUp(keyCode:Int,event:android.view.KeyEvent):Boolean {
        if(keyCode==android.view.KeyEvent.KEYCODE_CAMERA && shutter.isEnabled){shutter.performClick();return true}
        return super.onKeyUp(keyCode,event)
    }

    private val panorama=ShortPanorama()
    private var intervalActive=false
    private var sampleBusy=false
    private var intervalEncoder:IntervalMovie?=null // Export executor only
    private var intervalFile:File?=null
    private val sampleTask=Runnable { sampleInterval() }

    private fun sampledPreview(limit:Int):Bitmap? {
        val original=preview.bitmap ?: return null
        val scale=minOf(1f,limit.toFloat()/maxOf(original.width,original.height))
        val w=maxOf(2,(original.width*scale).toInt()/2*2)
        val h=maxOf(2,(original.height*scale).toInt()/2*2)
        val small=Bitmap.createScaledBitmap(original,w,h,true)
        if(original!==small)original.recycle()
        return small
    }

    private fun sampleInterval(){
        if(!intervalActive || closed)return
        if(!sampleBusy) {
            val bmp=sampledPreview(640)
            if(bmp!=null){
                sampleBusy=true
                exporter.execute {
                    val result=runCatching {
                        val codec=intervalEncoder ?: IntervalMovie(requireNotNull(intervalFile),bmp.width,bmp.height).also {intervalEncoder=it}
                        codec.append(bmp)
                    }
                    bmp.recycle()
                    main.post {sampleBusy=false;if(intervalActive && result.isFailure)endInterval(false)}
                }
            }
        }
        main.postDelayed(sampleTask,500)
    }

    private fun beginInterval() {
        if(intervalActive){endInterval(true);return}
        intervalFile=runCatching {File.createTempFile("interval-", ".mp4",cacheDir)}.getOrNull()
        if(intervalFile==null){status.text="Video storage unavailable";return}
        intervalActive=true;shutter.text="■";status.text="Time-lapse · 2 samples/s";updateControls();sampleInterval()
    }

    private fun endInterval(save:Boolean){
        if(!intervalActive)return
        intervalActive=false;main.removeCallbacks(sampleTask);takingPhoto=true;updateControls()
        val file=intervalFile ?: return
        exporter.execute {
            val codec=intervalEncoder
            val uri=runCatching {
                check(save);checkNotNull(codec).finish()
                publishCreativeFile(applicationContext,file,"Director_Interval_"+System.currentTimeMillis()+".mp4",true)
            }.getOrNull()
            codec?.close();intervalEncoder=null;file.delete()
            main.post {
                if(!closed){
                    intervalFile=null;takingPhoto=false;shutter.text="●";if(uri!=null)latestVideo=uri
                    status.text=if(uri!=null)"Time-lapse saved" else "Time-lapse unavailable or too short"
                    updateControls();bindCamera()
                }
            }
        }
    }

    private fun addPanoramaFrame() {
        if(takingPhoto || !streaming)return
        val bmp=sampledPreview(800) ?: return
        if(!panorama.add(bmp)){bmp.recycle();status.text="Try again · overlap half the previous frame";return}
        status.text="Frame "+panorama.count+" · pan right with half overlap"
        if(panorama.count>=5)finishPanorama() else updateControls()
    }

    private fun finishPanorama(){
        if(panorama.count<2 || takingPhoto)return
        val merged=panorama.stitch();panorama.clear();takingPhoto=true;updateControls()
        exporter.execute {
            val result=runCatching {
                val file=File.createTempFile("pano-", ".jpg",cacheDir)
                try {
                    file.outputStream().use {check(merged.compress(Bitmap.CompressFormat.JPEG,94,it))}
                    publishCreativeFile(applicationContext,file,"Director_Pano_"+System.currentTimeMillis()+".jpg",false)
                } finally {file.delete()}
            }
            merged.recycle()
            main.post {if(!closed){
                takingPhoto=false
                result.onSuccess {lastPhoto=it;status.text="Panorama saved"}.onFailure {status.text="Panorama save failed"}
                updateControls();bindCamera()
            }}
        }
    }

    private fun icon(glyph:String,description:String,action:()->Unit)=button(glyph,action).apply {
        contentDescription=description
        textSize=19f
        background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x44ffffff),null,null)
        val asset=when(description){
            "Back","Return to camera modes"->R.drawable.cam_back
            "Camera tools"->R.drawable.cam_sliders
            "Film and lighting"->R.drawable.cam_looks
            "Flash"->R.drawable.cam_flash
            "Exposure compensation"->R.drawable.cam_ev
            "Switch camera"->R.drawable.cam_flip
            "Open last capture"->R.drawable.cam_gallery
            else->0
        }
        if(asset!=0){text="";foreground=getDrawable(asset);foregroundGravity=Gravity.CENTER}
        setPadding(dp(4),0,dp(4),0)
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled),intArrayOf()),intArrayOf(Color.WHITE,Color.GRAY)))
    }

    private fun setSelectedPill(b:Button){
        b.setTextColor(Color.rgb(246,211,93))
        b.background=android.graphics.drawable.InsetDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(49,44,29));cornerRadius=dp(18).toFloat()
        },dp(1),dp(8),dp(1),dp(8))
    }

    private fun modeGlyph(mode:String)=when(mode){
        "Video","Cinematic"->"▷";"Portrait"->"◎";"Time-lapse"->"◷";"Pano"->"▱";"Night"->"☾";"Close"->"×";else->"◉"
    }

    private fun modeStrip(){
        val modes=listOf("Video","Photo","Portrait")+(if(shooting !in listOf("Video","Photo","Portrait"))listOf(shooting) else emptyList())+"More"
        strip("modes",modes.map {if(it=="Photo")"AI CAM" else it.uppercase()},modes.indexOf(shooting)){
            if(modeControl.isEnabled){
                if(modes[it]=="More")chooseMode() else if(modes[it]!=shooting)switchMode(modes[it])
            }
        }
    }

    private fun strip(page:String,labels:List<String>,selected:Int,action:(Int)->Unit){
        dockPage=page
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        if(page!="modes"){
            val shell=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(Color.rgb(22,24,27))}
            val choices=LinearLayout(this)
            var first=(selected.coerceAtLeast(0)/2)*2
            val back=icon("‹","Return to camera modes"){closeTray();modeStrip()}
            shell.addView(back,dp(48),dp(52))
            shell.addView(choices,LinearLayout.LayoutParams(0,dp(52),1f))
            lateinit var next:Button
            fun render(){
                choices.removeAllViews()
                for(i in first until minOf(labels.size,first+2)){
                    val b=button(labels[i]){if(dock.isEnabled)action(i)}.apply {
                        contentDescription=labels[i];textSize=12f;maxLines=2;ellipsize=TextUtils.TruncateAt.END
                        setPadding(dp(4),0,dp(4),0)
                        if(i==selected)setTextColor(Color.rgb(247,207,65))
                    }
                    choices.addView(b,LinearLayout.LayoutParams(0,-1,1f).apply {setMargins(dp(2),dp(2),dp(2),dp(2))})
                }
                next.contentDescription="Next controls, page ${first/2+1} of ${(labels.size+1)/2}"
            }
            next=icon("›","Next controls"){first=if(first+2<labels.size)first+2 else 0;render()}
            if(labels.size>2)shell.addView(next,dp(48),dp(52))
            render()
            dock.removeAllViews();dock.addView(shell,FrameLayout.LayoutParams(-1,dp(52)))
            return
        }
        val scroll=scroller(row)
        scroll.isFillViewport=true
        row.gravity=Gravity.CENTER
        var target:View?=null
        labels.forEachIndexed {i,label->
            val b=icon(label,label){if(dock.isEnabled)action(i)}.apply {
                textSize=12f;minWidth=dp(48);minimumWidth=dp(48);setPadding(dp(10),0,dp(10),0)
                isSelected=i==selected
                if(isSelected)setTextColor(Color.rgb(110,222,157))
            }
            if(b.isSelected){
                setSelectedPill(b)
            }
            row.addView(b,LinearLayout.LayoutParams(-2,dp(48)))
            if(i==selected)target=b
        }
        dock.removeAllViews();dock.addView(scroll,FrameLayout.LayoutParams(-1,dp(48)))
        target?.let {v->scroll.post {scroll.scrollTo((v.left-(scroll.width-v.width)/2).coerceAtLeast(0),0)}}
    }

    private var zoomOwner:Camera?=null
    private var shownMedia:Uri?=null
    private fun updateGalleryThumb(){
        val uri=if(videoMode || shooting=="Time-lapse")latestVideo else lastPhoto
        if(shownMedia==uri)return
        shownMedia=uri
        review.text="";review.foreground=getDrawable(R.drawable.cam_gallery)
        if(uri==null || Build.VERSION.SDK_INT<29)return
        exporter.execute {
            val bmp=runCatching {contentResolver.loadThumbnail(uri,android.util.Size(96,96),null)}.getOrNull()
            main.post {
                if(!closed && shownMedia==uri && bmp!=null){
                    val side=minOf(bmp.width,bmp.height)
                    val crop=Bitmap.createBitmap(bmp,(bmp.width-side)/2,(bmp.height-side)/2,side,side)
                    val thumb=Bitmap.createScaledBitmap(crop,dp(40),dp(40),true)
                    thumb.density=resources.displayMetrics.densityDpi
                    val square=android.graphics.drawable.BitmapDrawable(resources,thumb).apply {gravity=Gravity.CENTER}
                    review.text="";review.foreground=square
                }
            }
        }
    }

    private fun updateZoom(enabled:Boolean){
        val owner=camera
        zoomControls.visibility = if (owner != null && enabled && streaming) View.VISIBLE else View.GONE
        autoFrame.visibility = if (stillLighting && shooting != "Manual") View.VISIBLE else View.GONE
        autoFrame.isEnabled = enabled && !autoFrameInFlight && countdown == null
        autoFrame.text = if (autoFrameInFlight) "Framing…" else "Auto frame"
        if(owner===zoomOwner)return
        zoomOwner?.cameraInfo?.zoomState?.removeObservers(this)
        zoomOwner=owner
        zoomRow.removeAllViews()
        owner?.cameraInfo?.zoomState?.observe(this){state->
            if(owner!==camera)return@observe
            val values=listOf(.5f,1f,2f).filter {it in state.minZoomRatio..state.maxZoomRatio}
            if(zoomRow.childCount!=values.size){
                zoomRow.removeAllViews()
                for(v in values)zoomRow.addView(icon("","Zoom to ${v}×"){ clearAutoFrame(); owner.cameraControl.setZoomRatio(v); updateControls() }.apply {textSize=13f},dp(48),dp(48))
            }
            values.forEachIndexed {i,v->
                val b=zoomRow.getChildAt(i) as Button
                b.text=if(v==.5f)".5×" else "${v.toInt()}×"
                b.setTextColor(if(kotlin.math.abs(state.zoomRatio-v)<.06f)Color.rgb(110,222,157) else Color.WHITE)
            }
        }
    }

    private fun clearAutoFrame() {
        zoomGeneration++
        autoFrameInFlight = false
        frameAfterZoom = SystemClock.elapsedRealtime()
    }

    private fun applyAutoFrame() {
        val owner = camera ?: return
        if (!foreground || !streaming || !stillLighting || shooting == "Manual" || takingPhoto ||
            countdown != null || autoFrameInFlight) return
        val advice = guide.advice
        val now = SystemClock.elapsedRealtime()
        if (now - analyzedAt > 2000L || analyzedAt < frameAfterZoom || advice.focusBounds == null) {
            status.text = "Aim at a clear subject first"
            return
        }
        val zoom = owner.cameraInfo.zoomState.value ?: return
        val target = suggestedZoomRatio(advice, zoom.zoomRatio, zoom.minZoomRatio, zoom.maxZoomRatio)
        if (target == null) {
            status.text = when (advice.key) {
                "aim" -> "Center the suggested frame first"
                "ready" -> "Frame ready · press the shutter"
                "zoom_in", "zoom_out" -> "Adjust framing by hand to keep the subject in view"
                else -> advice.text
            }
            return
        }
        val ticket = ++zoomGeneration
        autoFrameInFlight = true
        guide.clear("Framing subject")
        updateControls()
        fun finish(success: Boolean) {
            if (closed || owner !== camera || ticket != zoomGeneration) return
            clearAutoFrame()
            frameAfterZoom += 200L
            status.text = if (success) "Hold steady on this frame" else "Auto frame unavailable · use the zoom controls"
            updateControls()
        }
        val request = owner.cameraControl.setZoomRatio(target)
        request.addListener({ finish(runCatching { request.get() }.isSuccess) }, uiExecutor)
        main.postDelayed({ finish(false) }, 2000L)
    }

    private fun chooseMode() {
        val choices=mutableListOf("Photo","Portrait","Manual","Video","Cinematic","Time-lapse","Pano")
        val info=runCatching { provider?.getCameraInfo(selector()) }.getOrNull()
        if(info!=null && !Recorder.getHighSpeedVideoCapabilities(info)?.getSupportedQualities(DynamicRange.SDR).isNullOrEmpty()) choices.add("Slo-mo")
        if(extensions?.isExtensionAvailable(selector(),ExtensionMode.NIGHT)==true) choices.add("Night")
        dockPage="more"
        val grid=android.widget.GridLayout(this).apply {columnCount=3;setBackgroundColor(Color.rgb(22,23,26))}
        for(choice in choices+listOf("Close")) {
            val b=icon(modeGlyph(choice)+"\n"+choice,choice){
                if(choice=="Close")modeStrip() else switchMode(choice)
            }.apply {textSize=13f}
            grid.addView(b,android.widget.GridLayout.LayoutParams().apply {
                width=0;height=dp(54);columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f)
            })
        }
        dock.removeAllViews();dock.addView(grid)
    }

    private fun switchMode(choice:String){
            if(!modeControl.isEnabled)return
            closeTray();panorama.clear();shooting=choice;modeStrip()
            prefs.edit().putString("shooting",shooting).apply()
            applyLook(look,false)
            bound=null;streaming=false;capture=null;video=null
            guide.visibility=if(videoMode)View.INVISIBLE else View.VISIBLE
            updateControls();updateRatioUi();aspectHost.requestLayout();aspectHost.post { bindCamera() }
    }

    private fun qualityLabel(q:Quality)=when(q){
        Quality.UHD->"4K";Quality.FHD->"1080p";Quality.HD->"720p";Quality.SD->"SD";else->"Auto"
    }

    private fun toggleRecording() {
        recording?.let { it.stop();return }
        val recorder=video?.output ?: return
        val name="Director_"+System.currentTimeMillis()+".mp4"
        var file: File?=null
        try {
            val pending=if(Build.VERSION.SDK_INT>=29){
                val values=ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME,name);put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Photography Director")
                }
                recorder.prepareRecording(this,MediaStoreOutputOptions.Builder(contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values).build())
            } else {
                val local=File(File(filesDir,"photos").apply { mkdirs() },name);file=local
                recorder.prepareRecording(this,FileOutputOptions.Builder(local).build())
            }
            recording=pending.start(uiExecutor) { event->
                if(closed)return@start
                when(event) {
                    is VideoRecordEvent.Start->{status.text="Recording · silent";shutter.text="■"}
                    is VideoRecordEvent.Finalize->{
                        recording=null;shutter.text="●"
                        if(!event.hasError()){
                            latestVideo=file?.let { FileProvider.getUriForFile(this,packageName+".photos",it) } ?: event.outputResults.outputUri
                            status.text="Video saved"
                        } else { file?.delete();status.text="Recording interrupted" }
                        updateControls()
                    }
                }
            }
            updateControls()
        } catch(e:Exception){status.text="Recording unavailable";updateControls()}
    }

    private fun showTools() {
        closeTray()
        val actions=if(videoMode || shooting in listOf("Time-lapse","Pano")) mutableListOf<Button>() else moreActions.filter { it.isEnabled }.toMutableList()
        if(capture!=null) actions.add(0,button("Composition · "+(if(shooting=="Manual")"Manual Composition" else config.mode)) {
            pick("Composition mode",DirectorSettings.modes,DirectorSettings.modes.indexOf(config.mode)) {
                val selected=DirectorSettings.modes[it]
                if(selected=="Manual Composition"){switchMode("Manual");return@pick}
                config=config.copy(mode=selected);guide.settings=config
                if(shooting=="Manual")switchMode("Photo")
                prefs.edit().putString("coachMode",config.mode).apply();bound=null;bindCamera()
            }
        })
        if(shooting=="Photo") {
            if(camera==null)actions.add(button("Reset photo settings"){requestedSize=null;format="JPEG";bound=null;bindCamera()})
            addLensTools(actions)
        }
        if(videoMode && recording==null) {
            val info=camera?.cameraInfo
            val caps=info?.let { if(shooting=="Slo-mo")Recorder.getHighSpeedVideoCapabilities(it) else Recorder.getVideoCapabilities(it) }
            val qs=caps?.getSupportedQualities(DynamicRange.SDR).orEmpty()
            if(qs.isNotEmpty())actions.add(button("Video resolution") { pick("Video resolution",qs.map(::qualityLabel),qs.indexOf(videoQuality)) {
                videoQuality=qs[it];bound=null;bindCamera()
            } })
            if(info!=null && shooting!="Slo-mo") {
                val rates=listOf(0)+info.supportedFrameRateRanges.filter { it.lower==it.upper }.map { it.upper }.distinct().sorted()
                actions.add(button("Frame rate") { pick("Target frame rate",rates.map { if(it==0)"Auto" else "$it fps" },rates.indexOf(fps)){
                    fps=rates[it];bound=null;bindCamera()
                } })
                if(caps?.isStabilizationSupported==true)actions.add(button(if(stabilise)"Stabilization on" else "Stabilization off"){
                    stabilise=!stabilise;bound=null;bindCamera()
                })
            }
        }
        if(recommendation!=null && ratioHint.isEnabled) actions.add(0,ratioHint)
        val zoom=camera?.cameraInfo?.zoomState?.value
        if(zoom!=null) actions.add(0,button("Zoom") {
            val values=listOf(.5f,1f,1.2f,1.5f,2f,4f,8f).filter { it>=zoom.minZoomRatio && it<=zoom.maxZoomRatio }
            if(values.isNotEmpty()) pick("Zoom ratio",values.map { "${it}×" },values.indexOf(zoom.zoomRatio)) {
                clearAutoFrame()
                camera?.cameraControl?.setZoomRatio(values[it])
                updateControls()
            }
        })
        strip("tools",actions.map {it.text.toString()},-1){ i-> modeStrip();actions[i].performClick() }

    }

    private fun closeTray() { tray?.let { viewfinder.removeView(it) };tray=null }

    private fun slider(title: String, low: Int, high: Int, start: Int, label: (Int)->String, set: (Int)->Unit) {
        closeTray()
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(8))
            setBackgroundColor(Color.argb(242,24,26,30));isClickable=true
        }
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val value=TextView(this).apply { setTextColor(Color.WHITE);text="$title  ${label(start)}" }
        row.addView(value,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("Done") {closeTray()})
        box.addView(row)
        box.addView(android.widget.SeekBar(this).apply {
            contentDescription=title;max=high-low;progress=(start-low).coerceIn(0,max)
            setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar:android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar:android.widget.SeekBar) {}
                override fun onProgressChanged(bar:android.widget.SeekBar,p:Int,user:Boolean) {
                    if(user){value.text="$title  ${label(low+p)}";set(low+p)}
                }
            })
        },LinearLayout.LayoutParams(-1,dp(48)))
        tray=box
        viewfinder.addView(box,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
    }

    private fun showExposure() {
        val active=camera ?: return
        val state=active.cameraInfo.exposureState
        val step=state.exposureCompensationStep.toFloat()
        if(!state.isExposureCompensationSupported || step<=0f) return
        val limit=kotlin.math.floor(2.0001f/step).toInt()
        val lo=maxOf(-limit,state.exposureCompensationRange.lower)
        val hi=minOf(limit,state.exposureCompensationRange.upper)
        slider("Exposure",lo,hi,state.exposureCompensationIndex.coerceIn(lo,hi),
            { java.lang.String.format("%+.1f EV",it*step) }) { index ->
            val result=active.cameraControl.setExposureCompensationIndex(index)
            val key="ev$lens"
            result.addListener({if(runCatching { result.get() }.isSuccess) prefs.edit().putInt(key,index).apply()},uiExecutor)
        }
    }

    private fun updateCinema() {
        val m=look.matrix()
        val gl=FloatArray(16)
        for(row in 0..2) {
            for(col in 0..2) gl[col*4+row]=m[row*5+col]
            gl[12+row]=m[row*5+4]/255f
        }
        gl[15]=1f
        cineEffect?.setEffects(listOf(androidx.media3.effect.RgbMatrix { _,_ -> gl }))
    }

    private fun applyLook(next: CameraLook, persist: Boolean = true) {
        look = next
        if (shooting == "Cinematic") updateCinema()
        val enabled = !videoMode && shooting !in listOf("Time-lapse", "Pano") && next != CameraLook()
        val advanced = enabled && stillLighting && (next.adjustments.keys.any { it.advanced } || next.blur > 0)
        val matrix = when {
            !enabled -> null
            stillLighting -> portraitMatrix(next, latestMatte, lightStrength)
            else -> next.matrix()
        }
        val gpuApplied = if (android.os.Build.VERSION.SDK_INT >= 33 && advanced && creativeSupported && matrix != null) {
            runCatching {
                val renderer = creativePreview ?: CreativePreview(preview).also { creativePreview = it }
                preview.setLayerType(View.LAYER_TYPE_NONE, null)
                renderer.apply(next, matrix, latestMatte)
            }.onFailure {
                creativeGpuFailed = true
                status.text = "Advanced controls unavailable on this preview"
            }.isSuccess
        } else false
        if (!gpuApplied) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && creativePreview != null) {
                preview.setRenderEffect(null)
                creativePreview = null
            }
            if (matrix == null) {
                preview.setLayerType(View.LAYER_TYPE_NONE, null)
            } else {
                val paint = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                }
                preview.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                preview.setLayerPaint(paint)
            }
        }
        refreshPortrait()
        if (persist) prefs.edit().putString("creativeLook", SavedLook(next, lightStrength).encode().toString()).apply()
    }

    private fun tonePad(){
        closeTray()
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(4),dp(10),dp(8))
            setBackgroundColor(Color.argb(240,22,24,28));isClickable=true
        }
        val row=LinearLayout(this)
        val caption=TextView(this).apply {setTextColor(Color.WHITE)}
        fun updateCaption(){caption.text="Warmth ${look.warmth}   Contrast ${look.tone}%"}
        updateCaption();row.addView(caption,LinearLayout.LayoutParams(0,-2,1f));row.addView(button("Done"){closeTray()})
        box.addView(row)
        val pad=object:View(this){
            val pen=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c:android.graphics.Canvas){
                pen.shader=android.graphics.LinearGradient(0f,0f,width.toFloat(),0f,0xff405a78.toInt(),0xff936344.toInt(),android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,width.toFloat(),height.toFloat(),pen);pen.shader=null
                pen.color=0x80ffffff.toInt()
                c.drawLine(width*.5f,0f,width*.5f,height.toFloat(),pen);c.drawLine(0f,height*.5f,width.toFloat(),height*.5f,pen)
                pen.color=Color.WHITE;c.drawCircle(width*(look.warmth+30)/60f,height*(150-look.tone)/100f,dp(6).toFloat(),pen)
            }
            override fun performClick():Boolean {super.performClick();return true}
            override fun onTouchEvent(e:MotionEvent):Boolean{
                if(e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP)){
                    val warmth=(e.x/width*60-30).roundToInt().coerceIn(-30,30)
                    val contrast=(150-e.y/height*100).roundToInt().coerceIn(50,150)
                    applyLook(look.copy(warmth=warmth,tone=contrast));updateCaption();invalidate()
                    if(e.actionMasked==MotionEvent.ACTION_UP)performClick()
                };return true
            }
        }
        pad.contentDescription="Tone and color pad. Drag right for warmth and up for contrast. Individual sliders are also available."
        box.addView(pad,LinearLayout.LayoutParams(-1,dp(96)))
        tray=box;viewfinder.addView(box,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
    }

    private fun openLooks() {
        closeTray()
        val sample = if (streaming) runCatching {
            preview.bitmap?.let { source ->
                val scaled = Bitmap.createScaledBitmap(source, 100, 75, true)
                if (source !== scaled) source.recycle()
                scaled
            }
        }.getOrNull() else null
        val panel = CreativeControls(
            activity = this,
            current = { SavedLook(look, lightStrength) },
            change = { selection, save ->
                lightStrength = selection.lightingStrength
                applyLook(selection.look, save)
                if (save && latestMatte == null &&
                    (selection.look.light != Lighting.NATURAL || selection.look.blur > 0)) {
                    status.text = "Aim at one person to preview portrait effects"
                }
            },
            close = ::closeTray,
            recommendation = ::showGradeRecommendation,
            tonePad = ::tonePad,
            thumbnail = sample,
            portraitStatus = { if (latestMatte == null) "Aim at one person to preview lighting" else "Portrait subject detected" },
            stills = stillLighting,
            advanced = stillLighting && creativeSupported,
        )
        val maxHeight = (viewfinder.height - dp(96)).coerceAtLeast(dp(48))
        val scroller = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(panel)
        }
        // Bound the tray so it can scroll independently of guidance and camera controls.
        tray = scroller
        viewfinder.addView(scroller, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    }

    private fun installGestures() {
        var pinched = false
        val scale =
            ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val active = camera ?: return false
                        val zoom = active.cameraInfo.zoomState.value ?: return false
                        if (autoFrameInFlight) {
                            clearAutoFrame()
                            updateControls()
                        }
                        active.cameraControl.setZoomRatio(
                            (zoom.zoomRatio * detector.scaleFactor).coerceIn(
                                zoom.minZoomRatio,
                                zoom.maxZoomRatio,
                            )
                        )
                        pinched = true
                        return true
                    }
                },
            )
        preview.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pinched = false
            if (event.pointerCount > 1) pinched = true
            scale.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && !pinched) {
                view.performClick()
                if(shooting=="Photo" && (focusDistance!=null || exposureLocked)) {
                    focusDistance=null;exposureLocked=false;applyLensControls()
                }
                val point = preview.meteringPointFactory.createPoint(event.x, event.y)
                val active = camera ?: return@setOnTouchListener true
                val action =
                    FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                if (active.cameraInfo.isFocusMeteringSupported(action)) {
                    val ticket = ++focusRequest
                    focusPending = true
                    focusFailed = false
                    guide.clear("Focusing")
                    val result = active.cameraControl.startFocusAndMetering(action)
                    result.addListener(
                        {
                            if (!closed && active === camera && ticket == focusRequest) {
                                focusPending = false
                                focusFailed =
                                    runCatching { !result.get().isFocusSuccessful }
                                        .getOrDefault(false)
                                main.postDelayed(
                                    { if (ticket == focusRequest) focusFailed = false },
                                    3000,
                                )
                            }
                        },
                        uiExecutor,
                    )
                }
            }
            true
        }
    }

    private fun updateRatioUi() {
        ratioMenu.text=displayRatio.label(portrait)
        for ((option, view) in ratioButtons) {
            view.isSelected = option == ratio
            view.setTextColor(if (option == ratio) Color.rgb(247, 207, 65) else Color.WHITE)
            view.contentDescription =
                "Photo ratio ${option.label(portrait)}${if (option == ratio) ", selected" else ""}"
        }
    }

    private fun updateControls() {
        val adjustable = !takingPhoto && countdown == null && recording==null && !intervalActive
        modeControl.isEnabled=adjustable
        looksControl.isEnabled=adjustable && (!videoMode || shooting=="Cinematic")
        looksControl.text=if(shooting=="Pano")"✓" else ""
        looksControl.foreground=if(shooting=="Pano")null else getDrawable(R.drawable.cam_looks)
        looksControl.contentDescription=if(shooting=="Pano")"Finish panorama" else "Film and lighting"
        if(shooting=="Pano")looksControl.isEnabled=adjustable && panorama.count>=2
        if(shooting=="Time-lapse")looksControl.isEnabled=false
        toolsButton.isEnabled=adjustable
        updateGalleryThumb()
        shutter.isEnabled = streaming && capture != null && !takingPhoto && !autoFrameInFlight
        if(videoMode) {
            shutter.isEnabled=(video!=null && streaming) || recording!=null
            shutter.contentDescription=if(recording!=null)"Stop recording" else "Record silent video"
        }
        if(shooting in listOf("Time-lapse","Pano")){
            shutter.isEnabled=(streaming || intervalActive) && !takingPhoto
            shutter.contentDescription=when {shooting=="Pano"->"Add panorama frame";intervalActive->"Finish time-lapse";else->"Start time-lapse"}
        }
        if(!videoMode && shooting !in listOf("Time-lapse","Pano") && countdown==null)shutter.contentDescription="Capture photo"
        switchButton.isEnabled = provider != null && adjustable
        flashButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true && adjustable
        timerButton.isEnabled = adjustable
        levelButton.isEnabled = gravitySensor != null
        nextFrameButton.isEnabled = streaming && adjustable
        analyzePhoto.isEnabled = lastPhoto != null && adjustable
        exposure.isEnabled =
            adjustable && camera?.cameraInfo?.exposureState?.isExposureCompensationSupported == true
        review.isEnabled = (if(videoMode || shooting=="Time-lapse")latestVideo!=null else lastPhoto!=null) && adjustable
        ratioMenu.isEnabled = adjustable && !videoMode
        ratioHint.isEnabled = adjustable
        dock.isEnabled=adjustable
        if(!adjustable && dockPage!="modes")modeStrip()
        shutter.setTextColor(if(shutter.text=="●")Color.TRANSPARENT else Color.BLACK)
        updateZoom(adjustable)
        for (button in ratioButtons.values) button.isEnabled = adjustable
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("videoUri",latestVideo?.toString())
        outState.putInt("lens", lens)
        outState.putString("photo", lastPhoto?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        updateLevelSensor()
    }

    override fun onPause() {
        clearAutoFrame()
        endInterval(true)
        recording?.stop()
        latestMatte=null;latestAuto=null;applyLook(look,false)
        foreground = false
        focusRequest++
        focusPending = false
        focusFailed = false
        cancelCountdown()
        updateLevelSensor()
        super.onPause()
    }

    override fun onStop() {
        main.removeCallbacks(expireAdvice)
        streaming = false
        guide.clear("Frame your subject")
        super.onStop()
    }

    override fun onDestroy() {
        closed = true
        session++
        main.removeCallbacksAndMessages(null)
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        cineEffect?.close();cineEffect=null
        analyzer.execute { vision?.close() }
        analyzer.shutdown()
        panorama.clear()
        exporter.shutdown()
        super.onDestroy()
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            background =
                android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(0x44ffffff),
                    android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(Color.rgb(42, 45, 50))
                    },
                    null,
                )
            setPadding(dp(10), dp(2), dp(10), dp(2))
            isAllCaps = false
            textSize = 13f
            minWidth = 0
            minimumWidth=0
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun scroller(row: View) =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row, ViewGroup.LayoutParams(-2, -2))
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
