package com.example.screencamerarecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        private const val FRONT_LENS_ID = 1001
        private const val BACK_LENS_ID = 1002
    }
    private lateinit var cameraSwitch: Switch
    private lateinit var lensGroup: RadioGroup
    private lateinit var status: TextView
    private var pendingStart = false

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenRecordService.EXTRA_CAMERA_ENABLED, cameraSwitch.isChecked)
                    putExtra(
                        ScreenRecordService.EXTRA_LENS,
                        if (lensGroup.checkedRadioButtonId == BACK_LENS_ID) 1 else 0
                    )
                }
                ContextCompat.startForegroundService(this, intent)
                status.text = "Recording started. Use the persistent notification to stop."
                Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show()
            } else {
                status.text = "Screen recording permission was cancelled."
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            beginStartFlow()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFF101426.toInt())
        }
        val title = TextView(this).apply {
            text = "Screen + Camera Recorder"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Record your phone screen, microphone and an optional floating camera preview."
            textSize = 15f
            setTextColor(0xFFB9C2DE.toInt())
            setPadding(0, dp(8), 0, dp(18))
        })

        cameraSwitch = Switch(this).apply {
            text = "Show camera bubble while recording"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = true
        }
        root.addView(cameraSwitch)

        root.addView(TextView(this).apply {
            text = "Choose camera"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(18), 0, dp(6))
        })
        lensGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        lensGroup.addView(RadioButton(this).apply {
            id = FRONT_LENS_ID
            text = "Front"
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = true
        })
        lensGroup.addView(RadioButton(this).apply {
            id = BACK_LENS_ID
            text = "Back"
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(18), 0, 0, 0)
        })
        root.addView(lensGroup)

        val start = Button(this).apply {
            text = "▶  Start recording"
            textSize = 17f
            setOnClickListener { requestPermissionsThenStart() }
        }
        root.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(22) })

        val stop = Button(this).apply {
            text = "■  Stop recording"
            setOnClickListener {
                startService(Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                })
                status.text = "Stop requested. The video will be saved if recording started successfully."
            }
        }
        root.addView(stop, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { topMargin = dp(8) })

        status = TextView(this).apply {
            text = "Ready. Press Start and approve Android's screen-capture prompt."
            textSize = 14f
            setTextColor(0xFFFFD6A5.toInt())
            setPadding(0, dp(16), 0, dp(10))
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Important: enable “Display over other apps” when asked for the floating camera. Android shows a system prompt before each screen-capture session."
            textSize = 13f
            setTextColor(0xFFB9C2DE.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun requestPermissionsThenStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (cameraSwitch.isChecked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
            return
        }
        beginStartFlow()
    }

    private fun beginStartFlow() {
        if (cameraSwitch.isChecked && !Settings.canDrawOverlays(this)) {
            status.text = "Allow “Display over other apps”, then return and press Start again."
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            @Suppress("DEPRECATION")
            startActivity(intent)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
