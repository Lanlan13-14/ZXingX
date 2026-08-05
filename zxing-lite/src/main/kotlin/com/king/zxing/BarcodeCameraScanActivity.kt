package com.king.zxing

import android.view.View
import androidx.annotation.IdRes
import com.google.zxing.Result
import com.king.camera.scan.BaseCameraScanActivity
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.view.viewfinderview.ViewfinderView
import com.king.zxing.analyze.MultiFormatAnalyzer
import com.king.zxing.config.LogicalMultiCameraConfig
import com.king.zxing.config.PhysicalLensController
import com.king.zxing.gesture.EdgeSwipeBackController

/**
 * 基于 ZXing 实现的扫码识别 - 相机扫描基类。
 *
 * Pinch zoom uses only public CameraX/Camera2 camera metadata and binding APIs. When the device
 * exposes logical physical cameras, the controller rebinding is driven by the pinch gesture;
 * otherwise it falls back to CameraControl.setZoomRatio(). No lens button or vendor branch exists.
 */
abstract class BarcodeCameraScanActivity : BaseCameraScanActivity<Result>() {

    protected var viewfinderView: ViewfinderView? = null
    private var physicalLensController: PhysicalLensController<Result>? = null
    private var swipeBackController: EdgeSwipeBackController? = null

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = findViewById(viewfinderViewId)
        }
        super.initUI()
        swipeBackController = EdgeSwipeBackController.install(this)
        physicalLensController = PhysicalLensController(
            previewView = previewView,
            cameraScan = cameraScan,
            configFactory = { binding -> LogicalMultiCameraConfig(this, binding) },
        ).also { it.install() }
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // Start with the public logical rear-camera selector. The controller owns pinch events
        // after initialization and falls back to CameraControl.setZoomRatio() when needed.
        cameraScan.setCameraConfig(LogicalMultiCameraConfig(this))
    }

    override fun onDestroy() {
        physicalLensController?.release()
        super.onDestroy()
    }

    override fun createAnalyzer(): Analyzer<Result>? {
        return MultiFormatAnalyzer()
    }

    override fun getLayoutId(): Int {
        return R.layout.zxl_camera_scan
    }

    @IdRes
    open fun getViewfinderViewId(): Int {
        return R.id.viewfinderView
    }
}
                                                                             