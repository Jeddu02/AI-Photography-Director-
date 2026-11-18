package com.jade.ai_photography_director

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private var pending: MethodChannel.Result? = null
    private var notificationPending: MethodChannel.Result? = null

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        MethodChannel(engine.dartExecutor.binaryMessenger, "photography/director")
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "status" ->
                            result.success(
                                mapOf(
                                    "overlay" to Settings.canDrawOverlays(this),
                                    "status" to
                                        (DirectorService.active?.status
                                            ?: DirectorService.lastStatus),
                                    "running" to (DirectorService.active != null),
                                    "latencyMs" to (DirectorService.active?.latency ?: 0L),
                                    "frames" to (DirectorService.active?.frames ?: 0L),
                                )
                            )
                        "onboarded" ->
                            result.success(
                                getSharedPreferences("director", 0).getBoolean("onboarded", false)
                            )
                        "completeOnboarding" -> {
                            getSharedPreferences("director", 0)
                                .edit()
                                .putBoolean("onboarded", true)
                                .apply()
                            result.success(null)
                        }
                        "settings" -> result.success(DirectorSettings.load(this).map())
                        "saveSettings" -> {
                            DirectorSettings.from(JSONObject(call.arguments as Map<*, *>))
                                .save(this)
                            DirectorService.active?.reload()
                            result.success(null)
                        }
                        "overlayPermission" -> {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                )
                            )
                            result.success(null)
                        }
                        "notifications" -> {
                            if (
                                Build.VERSION.SDK_INT >= 33 &&
                                    checkSelfPermission(
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                if (notificationPending != null)
                                    result.error(
                                        "busy",
                                        "Notification permission is already open",
                                        null,
                                    )
                                else {
                                    notificationPending = result
                                    requestPermissions(
                                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                        701,
                                    )
                                }
                            } else result.success(true)
                        }
                        "start" -> {
                            if (!Settings.canDrawOverlays(this))
                                result.error("overlay", "Allow display over other apps first", null)
                            else if (pending != null)
                                result.error("busy", "Consent already open", null)
                            else if (DirectorService.active != null)
                                result.error(
                                    "running",
                                    "Stop the current assistant before starting a new session",
                                    null,
                                )
                            else {
                                pending = result
                                val manager = getSystemService(MediaProjectionManager::class.java)
                                val intent =
                                    if (Build.VERSION.SDK_INT >= 34)
                                        manager.createScreenCaptureIntent(
                                            MediaProjectionConfig.createConfigForDefaultDisplay()
                                        )
                                    else manager.createScreenCaptureIntent()
                                startActivityForResult(intent, 700)
                            }
                        }
                        "manual" -> {
                            if (!Settings.canDrawOverlays(this))
                                result.error("overlay", "Allow display over other apps first", null)
                            else if (DirectorService.active != null)
                                result.error("running", "Stop the current session first", null)
                            else {
                                startForegroundService(
                                    Intent(this, DirectorService::class.java).setAction("manual")
                                )
                                result.success(null)
                            }
                        }
                        "stop" -> {
                            stopService(Intent(this, DirectorService::class.java))
                            result.success(null)
                        }
                        "aiCamera" -> {
                            if (DirectorService.active != null)
                                stopService(Intent(this, DirectorService::class.java))
                            startActivity(Intent(this, AiCameraActivity::class.java))
                            result.success(null)
                        }
                        "camera" ->
                            try {
                                openStockCamera()
                                result.success(null)
                            } catch (_: Exception) {
                                result.error(
                                    "camera",
                                    "Open your camera from its launcher icon",
                                    null,
                                )
                            }
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    result.error("native", e.message, null)
                }
            }
    }

    private fun openStockCamera() {
        startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    @Deprecated("Activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 700) {
            val result = pending
            pending = null
            if (resultCode == Activity.RESULT_OK && data != null)
                try {
                    startForegroundService(
                        Intent(this, DirectorService::class.java)
                            .setAction("capture")
                            .putExtra("code", resultCode)
                            .putExtra("data", data)
                    )
                    result?.success(null)
                    // Some devices require opening their camera from its launcher icon.
                    try {
                        openStockCamera()
                    } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
                } catch (e: Exception) {
                    result?.error("start", e.message, null)
                }
            else
                result?.error(
                    "denied",
                    "Screen capture was declined. Manual guides remain available.",
                    null,
                )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701) {
            val result = notificationPending
            notificationPending = null
            result?.success(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun onDestroy() {
        pending?.error("interrupted", "Activity closed during authorization", null)
        pending = null
        notificationPending?.error(
            "interrupted",
            "Activity closed during notification permission",
            null,
        )
        notificationPending = null
        super.onDestroy()
    }
}
