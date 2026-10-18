package com.example.screencamerarecorder

import android.app.*
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class ScreenRecordService : Service() {
    companion object {
        const val ACTION_START = "com.example.screencamerarecorder.START"
        const val ACTION_STOP = "com.example.screencamerarecorder.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_CAMERA_ENABLED = "cameraEnabled"
        const val EXTRA_LENS = "lens"
        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 401
    }

    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null
    private var recording = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> {
                if (recording) return START_NOT_STICKY
                startForegroundCompat()
                startRecording(intent)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 402, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Screen recording is active")
            .setContentText("Tap Stop recording to finish and save the video.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop recording", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(intent: Intent) {
        stopping = false
        try {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val resultData = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_RESULT_DATA)

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                stopSelf()
                return
            }

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                }
            }, android.os.Handler(mainLooper))

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = (metrics.widthPixels / 2) * 2
            val height = (metrics.heightPixels / 2) * 2
            val density = metrics.densityDpi

            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "recordings")
            if (!dir.exists()) dir.mkdirs()
            outputFile = File(dir, "screen_${System.currentTimeMillis()}.mp4")

            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = mr
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setOutputFile(outputFile!!.absolutePath)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setVideoSize(width, height)
            mr.setVideoFrameRate(30)
            mr.setVideoEncodingBitRate(6_000_000)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.prepare()

            virtualDisplay = projection?.createVirtualDisplay(
                "ScreenCameraRecorder",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mr.surface, null, null
            )
            mr.start()
            recording = true

            if (intent.getBooleanExtra(EXTRA_CAMERA_ENABLED, true)) {
                val overlay = Intent(this, CameraOverlayService::class.java).apply {
                    putExtra(EXTRA_LENS, intent.getIntExtra(EXTRA_LENS, 0))
                }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay) else startService(overlay)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenRecordService", "Could not start recording", e)
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (stopping) return
        stopping = true
        if (!recording && recorder == null) {
            stopService(Intent(this, CameraOverlayService::class.java))
            stopSelf()
            return
        }
        recording = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null

        try {
            recorder?.stop()
        } catch (e: Exception) {
            android.util.Log.w("ScreenRecordService", "Recorder stopped before a valid file was made", e)
        }
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
        recorder = null
        stopService(Intent(this, CameraOverlayService::class.java))

        outputFile?.let { file ->
            if (file.exists() && file.length() > 1024) {
                val saved = copyToGallery(file)
                if (saved != null) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(
                        NOTIFICATION_ID + 1,
                        NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("Recording saved")
                            .setContentText("Saved to Movies/ScreenCameraRecorder")
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun copyToGallery(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenCameraRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            contentResolver.openOutputStream(uri).use { output ->
                if (output == null) throw IllegalStateException("Could not open gallery output")
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
            file.delete()
            return uri
        } catch (e: Exception) {
            android.util.Log.e("ScreenRecordService", "Could not save video to gallery", e)
            contentResolver.delete(uri, null, null)
            return null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (recording || recorder != null) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
