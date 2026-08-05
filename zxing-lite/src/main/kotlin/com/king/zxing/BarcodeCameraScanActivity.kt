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
import com.king.zxing.gesture.EdgeSwipeBackController

/**
 * 基于 ZXing 实现的扫码识别 - 相机扫描基类。
 *
 * Uses the standard CameraX logical rear camera and CameraScan's native pinch zoom.
 * CameraControl.setZoomRatio() is delegated to the device camera HAL; this library does
 * not enumerate, guess, or explicitly bind physical camera IDs.
 */
abstract class BarcodeCameraScanActivity : BaseCameraScanActivity<Result>() {

    protected var viewfinderView: ViewfinderView? = null
    private var swipeBackController: EdgeSwipeBackController? = null

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = findViewById(viewfinderViewId)
        }
        super.initUI()
        swipeBackController = EdgeSwipeBackController.install(this)
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // CameraScan's default pinch recognizer calls CameraControl.setZoomRatio().
        // CameraX/HAL owns any physical-lens transition behind that continuous zoom.
        cameraScan.setNeedTouchZoom(true)
        cameraScan.setCameraConfig(LogicalMultiCameraConfig(this))
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
                                                                             