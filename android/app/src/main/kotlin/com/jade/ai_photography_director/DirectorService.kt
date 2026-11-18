package com.jade.ai_photography_director

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class DirectorService : Service() {
    companion object {
        @Volatile private var reference = WeakReference<DirectorService>(null)
        var active: DirectorService?
            get() = reference.get()
            private set(value) {
                reference = WeakReference(value)
            }

        @Volatile var lastStatus = "Stopped"
    }

    @Volatile
    var status = "Starting"
        private set(value) {
            field = value
            lastStatus = value
        }

    @Volatile
    var latency = 0L
        private set

    @Volatile
    var frames = 0L
        private set

    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("director-capture")
    private lateinit var worker: Handler
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private lateinit var wm: WindowManager
    private lateinit var guides: GuideView
    private var gradeTip=""
    private lateinit var panel: LinearLayout
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var guideParams: WindowManager.LayoutParams
    @Volatile private var settings = DirectorSettings()
    @Volatile private var vision: Vision? = null
    private val engine = CompositionEngine()
    // Accessed only by the single analysis executor.
    private val calibration = PreviewCalibrationState()
    private var lastCalibratedSize: Pair<Int, Int>? = null
    private var analyzedArea: Box? = null
    @Volatile private var paused = false
    @Volatile private var closing = false
    @Volatile private var generation = 0
    @Volatile private var captureVisible = true
    private var manual = false
    private var expanded = false
    private var compact = false
    private var choosingSubject = false
    @Volatile private var pickingTarget = false
    private val cancelTargetPick = Runnable { finishTargetPick(null) }
    private var subjectChoice: SubjectChoice = SubjectChoice.Automatic
    private var latestSubjects = emptyList<Subject>()
    private var latestSubjectsAt = 0L
    @Volatile private var lastFrame = 0L
    @Volatile private var lastAttempt = 0L
    private var blackSince = 0L
    private var screenW = 0
    private var screenH = 0
    private var density = 0
    private var captureW = 0
    private var captureH = 0
    @Volatile private var thermal = 0
    private val thermalListener =
        if (Build.VERSION.SDK_INT >= 29)
            PowerManager.OnThermalStatusChangedListener { thermal = it }
        else null
    private val lockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) stopSelf()
            }
        }
    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                status = "Capture ended • authorize a new session"
                stopSelf()
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width <= 0 || height <= 0 || closing) return
                updateMetrics()
                if (abs(width.toFloat() / height - screenW.toFloat() / screenH) > .05f) {
                    paused = true
                    finishTargetPick(null)
                    generation++
                    guides.clear("Capture area differs • stop and share entire screen")
                    return
                }
                resizeReader(width, height)
            }

            override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                captureVisible = isVisible
                if (!isVisible && !closing) finishTargetPick(null)
                generation++
                if (!closing)
                    guides.clear(
                        if (isVisible) "Shared display visible • updating guidance"
                        else "Shared content hidden • return to camera"
                    )
            }
        }

    override fun onCreate() {
        super.onCreate()
        workerThread.start()
        worker = Handler(workerThread.looper)
        wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(
                lockReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                RECEIVER_NOT_EXPORTED,
            )
        else registerReceiver(lockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        if (Build.VERSION.SDK_INT >= 29 && thermalListener != null)
            getSystemService(PowerManager::class.java).addThermalStatusListener(thermalListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (active != null || intent == null) {
            if (active == null) stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        manual = intent.action == "manual"
        settings = DirectorSettings.load(this)
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "assistant",
                    "Photography assistant",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
            val stop =
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, DirectorService::class.java).setAction("stop"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val open =
                PendingIntent.getActivity(
                    this,
                    2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                Notification.Builder(this, "assistant")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("AI Photography Director")
                    .setContentText(
                        if (manual) "Manual framing guides active"
                        else "Screen analysis active • frames stay on device"
                    )
                    .setContentIntent(open)
                    .setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                    .build()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(
                    37,
                    notification,
                    if (manual) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            else startForeground(37, notification)
            updateMetrics()
            createOverlay()
            active = this
            if (manual) {
                status = "Manual guides • no analysis"
                guides.manual = true
                guides.clear(status)
            } else {
                @Suppress("DEPRECATION")
                val data =
                    if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra("data", Intent::class.java)
                    else intent.getParcelableExtra<Intent>("data")
                require(data != null) { "Missing fresh capture authorization" }
                projection =
                    getSystemService(MediaProjectionManager::class.java)
                        .getMediaProjection(
                            intent.getIntExtra("code", Activity.RESULT_CANCELED),
                            data,
                        )
                projection!!.registerCallback(projectionCallback, main)
                setInitialReader(screenW, screenH)
                lastFrame = SystemClock.elapsedRealtime()
                display =
                    projection!!.createVirtualDisplay(
                        "Director transient frames",
                        captureW,
                        captureH,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader!!.surface,
                        null,
                        worker,
                    )
                status = "Open stock camera • analyzing preview area"
                executor.execute {
                    try {
                        vision = Vision(this)
                    } catch (_: Throwable) {
                        main.post {
                            if (!closing) {
                                paused = true
                                status = "Vision unavailable • restart or use manual guides"
                                guides.clear(status)
                            }
                        }
                    }
                }
            }
            main.post(watchdog)
        } catch (e: Exception) {
            status = "Assistant failed: ${e.javaClass.simpleName}"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun updateMetrics() {
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        density = metrics.densityDpi
    }

    private fun dimensions(w: Int, h: Int): Pair<Int, Int> {
        val scale = min(1f, 960f / max(w, h))
        return Pair(max(2, (w * scale).toInt()), max(2, (h * scale).toInt()))
    }

    private fun setInitialReader(w: Int, h: Int) {
        val dims = dimensions(w, h)
        captureW = dims.first
        captureH = dims.second
        reader =
            ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2).also {
                it.setOnImageAvailableListener({ r -> consume(r) }, worker)
            }
    }

    private fun resizeReader(w: Int, h: Int) {
        worker.post {
            if (closing || display == null) return@post
            val dims = dimensions(w, h)
            if (dims.first == captureW && dims.second == captureH) return@post
            generation++
            val old = reader
            setInitialReader(w, h)
            display?.resize(captureW, captureH, density)
            display?.surface = reader!!.surface
            old?.close()
            main.post { clearSubjectSelection() }
            executor.execute {
                vision?.reset()
                engine.reset(clearSelection = true)
            }
        }
    }

    private fun consume(source: ImageReader) {
        val image =
            try {
                source.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return
        val now = SystemClock.elapsedRealtime()
        lastFrame = now
        val config = settings
        val interval =
            max(
                config.interval,
                if (Build.VERSION.SDK_INT >= 29 && thermal >= PowerManager.THERMAL_STATUS_SEVERE)
                    1600L
                else 0L,
            )
        if (
            closing ||
                paused ||
                pickingTarget ||
                !captureVisible ||
                vision == null ||
                now - lastAttempt < interval ||
                !busy.compareAndSet(false, true)
        ) {
            image.close()
            return
        }
        lastAttempt = now
        val version = generation
        var allocated: Bitmap? = null
        val frameWidth = image.width
        val frameHeight = image.height
        try {
            val plane = image.planes[0]
            val paddedWidth =
                frameWidth + (plane.rowStride - plane.pixelStride * frameWidth) / plane.pixelStride
            allocated = Bitmap.createBitmap(paddedWidth, frameHeight, Bitmap.Config.ARGB_8888)
            allocated.copyPixelsFromBuffer(plane.buffer)
        } catch (_: Exception) {
            allocated?.recycle()
            allocated = null
            busy.set(false)
            main.post {
                if (!closing && version == generation) {
                    generation++
                    status = "Screen capture unavailable"
                    guides.clear(status)
                }
            }
        } finally {
            image.close()
        }
        val frame = allocated ?: return
        try {
            executor.execute {
                var cropped: Bitmap? = null
                try {
                    if (
                        closing ||
                            paused ||
                            pickingTarget ||
                            !captureVisible ||
                            generation != version
                    )
                        return@execute
                    val start = SystemClock.elapsedRealtime()
                    val roi =
                        if (config.autoPreview) detectPreview(frame, frameWidth, frameHeight)
                        else config.roi
                    if (roi == null) {
                        engine.reset()
                        main.post {
                            if (!closing && !paused && version == generation) {
                                guides.previewArea = null
                                latestSubjects = emptyList()
                                latestSubjectsAt = 0
                                status = "Open the stock camera or aim at a brighter scene"
                                guides.clear(status)
                            }
                        }
                        return@execute
                    }
                    val areaChanged = roi != analyzedArea
                    if (areaChanged) {
                        analyzedArea = roi
                        blackSince = 0
                        vision?.reset()
                        engine.reset(clearSelection = true)
                    }
                    val left = (roi.l * frameWidth).toInt()
                    val top = (roi.t * frameHeight).toInt()
                    val right = (roi.r * frameWidth).toInt()
                    val bottom = (roi.b * frameHeight).toInt()
                    cropped =
                        Bitmap.createBitmap(
                            frame,
                            left,
                            top,
                            max(1, right - left),
                            max(1, bottom - top),
                        )
                    val features = vision?.analyze(cropped, config.mode) ?: return@execute
                    latency = SystemClock.elapsedRealtime() - start
                    frames++
                    if (features.brightness < .025f) {
                        if (blackSince == 0L) blackSince = now
                    } else blackSince = 0
                    val grade=PortraitDirector.auto(features,PortraitDirector.matte(features))
                    val tip=grade?.let {
                        when {
                            it.exposureDelta<0 -> "Try lowering exposure in your camera."
                            it.exposureDelta>0 -> "Try raising exposure in your camera."
                            it.strength>0 -> "Try soft portrait light if your camera offers it."
                            it.look.warmth<0 -> "Try a cooler white balance."
                            it.look.warmth>0 -> "Try a warmer white balance."
                            else -> "Keep the current exposure and color."
                        }
                    }.orEmpty()
                    val advice =
                        if (blackSince > 0 && now - blackSince > 2000)
                            Advice(
                                "black",
                                "Preview unavailable • stop AI and use manual guides",
                                2,
                            )
                        else
                            engine.evaluate(
                                features,
                                config.mode,
                                config.preference,
                                config.threshold,
                                now,
                            )
                    main.post {
                        if (!closing && !paused && captureVisible && version == generation) {
                            if (areaChanged && subjectChoice != SubjectChoice.Automatic) {
                                clearSubjectSelection()
                            }
                            guides.previewArea = roi
                            latestSubjects =
                                features.subjects.filter { it.confidence >= config.threshold }
                            gradeTip=if(advice.key=="black") "" else tip
                            latestSubjectsAt = now
                            guides.subjects = latestSubjects
                            guides.advice = advice
                            guides.horizon = features.horizon
                            guides.invalidate()
                            status = advice.text
                        }
                    }
                } catch (_: Exception) {
                    main.post {
                        if (!closing) {
                            generation++
                            guides.clear("Analysis interrupted • pause and retry")
                            status = "Analysis error • pause and retry"
                        }
                    }
                } finally {
                    if (cropped !== frame) cropped?.recycle()
                    if (!frame.isRecycled) frame.recycle()
                    busy.set(false)
                }
            }
        } catch (_: Exception) {
            if (!frame.isRecycled) frame.recycle()
            busy.set(false)
        }
    }

    private fun detectPreview(frame: Bitmap, width: Int, height: Int): Box? {
        val size = width to height
        if (lastCalibratedSize != size) {
            calibration.reset()
            lastCalibratedSize = size
        }
        val sampleWidth = if (width < height) 64 else 112
        val sampleHeight = if (width < height) 112 else 64
        val matrix =
            Matrix().apply {
                setScale(sampleWidth.toFloat() / width, sampleHeight.toFloat() / height)
            }
        val sample = Bitmap.createBitmap(frame, 0, 0, width, height, matrix, true)
        try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            return calibration.update(
                PreviewCalibration.detect(
                    pixels,
                    sample.width,
                    sample.height,
                    width.toFloat() / height,
                )
            )
        } finally {
            if (sample !== frame) sample.recycle()
        }
    }

    private val watchdog =
        object : Runnable {
            override fun run() {
                if (closing) return
                if (!Settings.canDrawOverlays(this@DirectorService)) {
                    stopSelf()
                    return
                }
                if (
                    !manual &&
                        !paused &&
                        lastFrame > 0 &&
                        SystemClock.elapsedRealtime() - lastFrame > 3000
                ) {
                    generation++
                    guides.clear("No fresh frames • stop and authorize again")
                    status = "Capture interrupted"
                }
                if (
                    guides.advice.key == "ready" &&
                        SystemClock.elapsedRealtime() - latestSubjectsAt > 2500
                ) {
                    guides.clear("Hold steady")
                    status = "Hold steady"
                }
                if (
                    !manual &&
                        !paused &&
                        busy.get() &&
                        SystemClock.elapsedRealtime() - lastAttempt > 5000
                ) {
                    generation++
                    guides.clear("Analysis is slow • wait or stop assistant")
                    status = "Analysis delayed"
                }
                main.postDelayed(this, 1000)
            }
        }

    fun reload() {
        main.post {
            if (closing) return@post
            val updated = DirectorSettings.load(this)
            val autoChanged = settings.autoPreview != updated.autoPreview
            settings = updated
            generation++
            if (::guides.isInitialized) {
                guides.settings = settings
                if (autoChanged) guides.previewArea = null
                guideParams.alpha = settings.opacity
                wm.updateViewLayout(guides, guideParams)
                guides.clear(if (manual) "Manual framing guides" else "Updating guidance")
            }
            clearSubjectSelection()
            executor.execute {
                if (autoChanged) calibration.reset()
                engine.reset(clearSelection = true)
                vision?.reset()
            }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    // Window coordinates are physical screen coordinates, independent of text direction.
    @SuppressLint("RtlHardcoded")
    private fun windowParams(w: Int, h: Int, touch: Boolean) =
        WindowManager.LayoutParams(
                w,
                h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = Gravity.TOP or Gravity.LEFT
                if (Build.VERSION.SDK_INT >= 28)
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

    private fun createOverlay() {
        guides = GuideView(this)
        guides.settings = settings
        guideParams = windowParams(-1, -1, false)
        guideParams.alpha = settings.opacity
        wm.addView(guides, guideParams)
        panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(26, 28, 31))
                        cornerRadius = dp(18).toFloat()
                    }
            }
        panelParams = windowParams(dp(64), -2, true)
        panelParams.x = dp(12)
        panelParams.y = dp(36)
        wm.addView(panel, panelParams)
        renderPanel()
    }

    private fun beginTargetPick() {
        if (paused || !captureVisible || pickingTarget) return
        val roi = guides.previewArea ?: return
        generation++
        pickingTarget = true
        choosingSubject = false
        expanded = false
        guides.picking = true
        guides.clear("Tap or drag around your subject")
        guideParams.flags =
            guideParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        wm.updateViewLayout(guides, guideParams)
        var anchor: Pair<Float, Float>? = null
        fun point(event: MotionEvent): Pair<Float, Float>? {
            if (guides.width <= 0 || guides.height <= 0) return null
            return ((event.x / guides.width - roi.l) / roi.w) to
                ((event.y / guides.height - roi.t) / roi.h)
        }
        guides.setOnTouchListener { _, event ->
            val point = point(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    anchor = point?.takeIf { it.first in 0f..1f && it.second in 0f..1f }
                MotionEvent.ACTION_MOVE ->
                    guides.targetSelection = anchor?.let { a -> point?.let { targetBounds(a, it) } }
                MotionEvent.ACTION_UP -> {
                    val chosen = anchor?.let { a -> point?.let { targetBounds(a, it) } }
                    finishTargetPick(chosen)
                }
                MotionEvent.ACTION_CANCEL -> finishTargetPick(null)
            }
            guides.invalidate()
            true
        }
        renderPanel()
        main.postDelayed(cancelTargetPick, 12000)
    }

    private fun finishTargetPick(bounds: Box?) {
        if (!pickingTarget) return
        main.removeCallbacks(cancelTargetPick)
        pickingTarget = false
        guides.picking = false
        guides.targetSelection = null
        guides.setOnTouchListener(null)
        guideParams.flags = guideParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(guides, guideParams)
        if (bounds == null) {
            guides.clear("Target selection cancelled")
            renderPanel()
        } else selectSubject(SubjectChoice.Region(bounds))
    }

    private fun clearSubjectSelection() {
        finishTargetPick(null)
        subjectChoice = SubjectChoice.Automatic
        latestSubjects = emptyList()
        latestSubjectsAt = 0L
        choosingSubject = false
        if (::panel.isInitialized) renderPanel()
    }

    private fun selectSubject(choice: SubjectChoice) {
        if (
            choice is SubjectChoice.Detected &&
                (paused ||
                    SystemClock.elapsedRealtime() - latestSubjectsAt > 2500 ||
                    !SubjectMenu.isAvailable(choice, latestSubjects, settings.threshold))
        ) {
            guides.clear("Subject changed • reopen selection")
            status = "Subject changed • reopen selection"
            choosingSubject = false
            renderPanel()
            return
        }
        subjectChoice = choice
        choosingSubject = false
        generation++
        guides.clear(
            if (choice == SubjectChoice.WholeScene) "Keeping the wider scene"
            else "Finding a frame for your subject"
        )
        executor.execute { engine.selectSubject(choice) }
        renderPanel()
    }

    private fun constrainPanel(x: Int, y: Int, width: Int, height: Int) {
        val cutout =
            if (Build.VERSION.SDK_INT >= 28) guides.rootWindowInsets?.displayCutout else null
        val left = if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetLeft ?: 0 else 0
        val top = if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetTop ?: 0 else 0
        val right = if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetRight ?: 0 else 0
        val bottom = if (Build.VERSION.SDK_INT >= 28) cutout?.safeInsetBottom ?: 0 else 0
        panelParams.x = x.coerceIn(left, max(left, screenW - right - width))
        panelParams.y = y.coerceIn(top, max(top, screenH - bottom - height))
    }

    private fun renderPanel() {
        panel.removeAllViews()
        panelParams.width = dp(if (expanded) 218 else 64)
        val handle =
            FloatingHandle(this).apply {
                text =
                    if (pickingTarget) "Cancel" else if (expanded) "◉ DIRECTOR   ·   drag" else "◉"
                textSize = if (expanded) 13f else 25f
                setTextColor(Color.rgb(247, 207, 65))
                gravity = Gravity.CENTER
                minHeight = dp(48)
                isFocusable = true
                contentDescription = "Photography director. Drag to move; tap to expand controls."
                setOnClickListener {
                    if (pickingTarget) finishTargetPick(null)
                    else {
                        expanded = !expanded
                        renderPanel()
                    }
                }
            }
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = event.rawX
                    sy = event.rawY
                    ox = panelParams.x
                    oy = panelParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - sx
                    val dy = event.rawY - sy
                    if (abs(dx) + abs(dy) > dp(8)) moved = true
                    constrainPanel(ox + dx.toInt(), oy + dy.toInt(), panel.width, panel.height)
                    wm.updateViewLayout(panel, panelParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) handle.performClick()
                    true
                }
                else -> false
            }
        }
        panel.addView(handle)
        if (expanded) {
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val scroll =
                ScrollView(this).apply {
                    isFillViewport = false
                    addView(body)
                }
            panel.addView(scroll)
            fun control(label: String, action: () -> Unit) {
                body.addView(
                    Button(this).apply {
                        text = label
                        textSize = 12f
                        isAllCaps = false
                        setOnClickListener { action() }
                    }
                )
            }
            control(if (paused) "Resume guidance" else "Pause guidance") {
                paused = !paused
                generation++
                guides.clear(
                    if (paused) "Paused • no screen analysis"
                    else if (manual) "Manual guides" else "Resuming analysis"
                )
                clearSubjectSelection()
                executor.execute {
                    engine.reset(clearSelection = true)
                    vision?.reset()
                }
                renderPanel()
            }
            if(!manual) control("AI Auto suggestion") {
                val fresh=!paused && captureVisible && SystemClock.elapsedRealtime()-latestSubjectsAt<=3000 && gradeTip.isNotBlank()
                android.widget.Toast.makeText(this,if(fresh)gradeTip else "A current camera preview is needed.",android.widget.Toast.LENGTH_LONG).show()
            }
            if (!manual && settings.mode != "Manual Composition") {
                val label =
                    when (val choice = subjectChoice) {
                        SubjectChoice.Automatic -> "Automatic"
                        SubjectChoice.WholeScene -> "Whole scene"
                        is SubjectChoice.Region -> "Locked area"
                        is SubjectChoice.Detected -> choice.label
                    }
                control("Subject: $label ▾") {
                    choosingSubject = !choosingSubject
                    renderPanel()
                }
                if (choosingSubject) {
                    if (!paused && captureVisible && guides.previewArea != null) {
                        control("Select on preview") { beginTargetPick() }
                    }
                    control("Automatic selection") { selectSubject(SubjectChoice.Automatic) }
                    control("Whole scene · stay wide") { selectSubject(SubjectChoice.WholeScene) }
                    val options =
                        if (!paused && SystemClock.elapsedRealtime() - latestSubjectsAt <= 2500)
                            SubjectMenu.candidates(latestSubjects, settings.threshold)
                        else emptyList()
                    if (options.isEmpty())
                        body.addView(
                            TextView(this).apply {
                                text =
                                    "No subjects detected yet. Resume guidance or point at a clearer subject."
                                setTextColor(Color.LTGRAY)
                                textSize = 12f
                                setPadding(dp(8), dp(8), dp(8), dp(8))
                            }
                        )
                    for (option in options) control(option.title) {
                        selectSubject(
                            SubjectChoice.Detected(option.subject.id, option.subject.label)
                        )
                    }
                }
            }
            if (subjectChoice != SubjectChoice.WholeScene)
                control("Find another shot") {
                    generation++
                    guides.clear("Looking for another frame…")
                    executor.execute { engine.nextShot() }
                }
            control(settings.mode) {
                settings =
                    settings.copy(
                        mode =
                            DirectorSettings.modes[
                                    (DirectorSettings.modes.indexOf(settings.mode) + 1) %
                                        DirectorSettings.modes.size]
                    )
                settings.save(this)
                reload()
                renderPanel()
            }
            control(if (compact) "Show visual guidance" else "Hide visual guidance") {
                compact = !compact
                guides.compact = compact
                guides.invalidate()
                renderPanel()
            }
            control(if (settings.grid) "Hide framing grid" else "Show framing grid") {
                settings = settings.copy(grid = !settings.grid)
                settings.save(this)
                reload()
                renderPanel()
            }
            body.addView(
                SeekBar(this).apply {
                    max = 45
                    progress = ((settings.opacity - .2f) * 100).toInt()
                    contentDescription = "Overlay transparency"
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                bar: SeekBar?,
                                value: Int,
                                user: Boolean,
                            ) {
                                if (user) {
                                    settings = settings.copy(opacity = .2f + value / 100f)
                                    settings.save(this@DirectorService)
                                    guideParams.alpha = settings.opacity
                                    wm.updateViewLayout(guides, guideParams)
                                }
                            }

                            override fun onStartTrackingTouch(bar: SeekBar?) {}

                            override fun onStopTrackingTouch(bar: SeekBar?) {}
                        }
                    )
                }
            )
            control("Stop assistant") { stopSelf() }
            body.measure(
                View.MeasureSpec.makeMeasureSpec(dp(210), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            scroll.layoutParams =
                LinearLayout.LayoutParams(
                    -1,
                    min(body.measuredHeight, (screenH - dp(100)).coerceAtLeast(dp(100))),
                )
        }
        panelParams.x = panelParams.x.coerceIn(0, max(0, screenW - panelParams.width))
        wm.updateViewLayout(panel, panelParams)
        panel.post {
            if (!closing) {
                constrainPanel(panelParams.x, panelParams.y, panel.width, panel.height)
                wm.updateViewLayout(panel, panelParams)
            }
        }
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        finishTargetPick(null)
        generation++
        updateMetrics()
        guides.previewArea = null
        guides.clear("Open the stock camera or aim at a brighter scene")
        resizeReader(screenW, screenH)
        renderPanel()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        closing = true
        active = null
        generation++
        main.removeCallbacksAndMessages(null)
        if (!status.startsWith("Assistant failed") && !status.startsWith("Capture ended"))
            lastStatus = "Stopped"
        try {
            unregisterReceiver(lockReceiver)
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 29 && thermalListener != null)
            getSystemService(PowerManager::class.java).removeThermalStatusListener(thermalListener)
        projection?.unregisterCallback(projectionCallback)
        display?.release()
        display = null
        projection?.stop()
        projection = null
        worker.post {
            reader?.close()
            reader = null
            workerThread.quitSafely()
        }
        executor.execute {
            vision?.close()
            vision = null
        }
        executor.shutdown()
        if (::guides.isInitialized)
            try {
                wm.removeView(guides)
            } catch (_: Exception) {}
        if (::panel.isInitialized)
            try {
                wm.removeView(panel)
            } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
