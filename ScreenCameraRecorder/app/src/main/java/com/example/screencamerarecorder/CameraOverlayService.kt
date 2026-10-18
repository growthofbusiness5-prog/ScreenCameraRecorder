package com.example.screencamerarecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CameraOverlayService : LifecycleService() {
    private var windowManager: WindowManager? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createChannel()
        val notification = NotificationCompat.Builder(this, "camera_overlay")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Camera bubble is active")
            .setContentText("Camera preview is shown over other apps.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                402,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else startForeground(402, notification)

        val lens = intent?.getIntExtra(ScreenRecordService.EXTRA_LENS, 0) ?: 0
        showOverlay(lens)
        return START_NOT_STICKY
    }

    private fun showOverlay(lens: Int) {
        if (previewView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(0xFF202020.toInt())
        }
        previewView = preview

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(150), dp(200), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(120)
        }

        try {
            windowManager?.addView(preview, params)
        } catch (e: Exception) {
            android.util.Log.e("CameraOverlayService", "Overlay could not be displayed", e)
            stopSelf()
            return
        }

        val providerFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(if (lens == 1) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
                    .build()
                val useCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, selector, useCase)
            } catch (e: Exception) {
                android.util.Log.e("CameraOverlayService", "Camera preview failed", e)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "camera_overlay", "Camera bubble", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        try { previewView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        previewView = null
        cameraExecutor.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
}
