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

/**
 * 基于 ZXing 实现的扫码识别 - 相机扫描基类。
 *
 * Uses CameraX logical/physical camera APIs. Pinch gestures switch physical lenses
 * when the OEM exposes them; no extra lens buttons are added.
 */
abstract class BarcodeCameraScanActivity : BaseCameraScanActivity<Result>() {

    protected var viewfinderView: ViewfinderView? = null
    private var physicalLensController: PhysicalLensController<Result>? = null

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = findViewById(viewfinderViewId)
        }
        super.initUI()
        physicalLensController = PhysicalLensController(
            lifecycleOwner = this,
            previewView = previewView,
            cameraScan = cameraScan,
            configFactory = { physicalId -> LogicalMultiCameraConfig(this, physicalId) }
        ).also { it.install() }
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // Start with the logical multi-camera. The controller may later bind an explicit
        // physical camera id while retaining the same PreviewView and analyzer.
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
                                                                             