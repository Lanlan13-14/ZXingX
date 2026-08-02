package com.king.zxing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.view.TextureView
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.FrameMetadata
import com.king.view.viewfinderview.ViewfinderView
import com.king.zxing.analyze.ImageAnalyzer
import com.king.zxing.camera2.Camera2ScanController
import com.king.zxing.gesture.EdgeSwipeBackController

/** Camera2-direct scan base; CameraX does not choose the lens. */
abstract class Camera2BarcodeScanActivity : AppCompatActivity() {
    protected var viewfinderView: ViewfinderView? = null
    private lateinit var cameraController: Camera2ScanController
    protected lateinit var swipeBackController: EdgeSwipeBackController
    private lateinit var previewView: TextureView
    private var beep: android.media.ToneGenerator? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())
        previewView = findViewById(R.id.previewView)
        viewfinderView = findViewById(getViewfinderViewId())
        swipeBackController = EdgeSwipeBackController.install(this)
        findViewById<View>(R.id.ivFlashlight)?.setOnClickListener { toggleTorch(it) }
        initUI()
        requestOrStartCamera()
    }

    protected open fun initUI() = Unit
    protected abstract fun createAnalyzer(): ImageAnalyzer
    protected abstract fun onScanResult(result: AnalyzeResult<Result>)
    protected open fun getLayoutId(): Int = R.layout.zxl_camera2_scan

    @IdRes
    protected open fun getViewfinderViewId(): Int = R.id.viewfinderView

    protected fun setAnalyzeImage(enabled: Boolean) {
        if (::cameraController.isInitialized) cameraController.setAnalyzeImage(enabled)
    }

    private fun requestOrStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (::cameraController.isInitialized) return
        cameraController = Camera2ScanController(this, previewView, createAnalyzer()) { result, width, height ->
            beep?.release()
            beep = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 70).also {
                it.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 90)
            }
            val nv21Placeholder = ByteArray(width * height * 3 / 2)
            onScanResult(
                AnalyzeResult(
                    nv21Placeholder,
                    ImageFormat.NV21,
                    FrameMetadata(width, height, 0),
                    result
                )
            )
        }
        cameraController.start()
    }

    private fun toggleTorch(view: View) {
        if (!::cameraController.isInitialized || !cameraController.hasFlashUnit()) return
        val enabled = !cameraController.isTorchEnabled()
        cameraController.enableTorch(enabled)
        view.isSelected = enabled
    }

    override fun onResume() {
        super.onResume()
        if (::cameraController.isInitialized) cameraController.start()
    }

    override fun onPause() {
        if (::cameraController.isInitialized) cameraController.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::cameraController.isInitialized) cameraController.release()
        beep?.release()
        beep = null
        super.onDestroy()
    }
}
