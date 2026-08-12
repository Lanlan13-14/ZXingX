package com.king.zxing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.Result
import com.king.camera.scan.BaseCameraScanActivity
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.view.viewfinderview.ViewfinderView
import com.king.zxing.analyze.MultiFormatAnalyzer
import com.king.zxing.config.CameraFacingPlan
import com.king.zxing.config.LogicalMultiCameraConfig
import com.king.zxing.config.MinimalCameraConfig
import com.king.zxing.gesture.EdgeSwipeBackController

/**
 * 基于 ZXing 实现的扫码识别 - 相机扫描基类。
 *
 * Uses the standard CameraX logical camera and CameraScan's native pinch zoom.
 * CameraControl.setZoomRatio() is delegated to the device camera HAL; this library does
 * not enumerate, guess, or explicitly bind physical camera IDs.
 *
 * Two resilience features live here:
 *
 * 1. Front/back switching: layouts may contain an `ivSwitchCamera` view (bottom center);
 *    tapping it rebinds the camera with the opposite lens facing. The button is only
 *    shown when the device actually reports both a front and a back camera. The default
 *    facing is back; on devices without a back camera (e.g. some pads/kiosks) the
 *    initial facing automatically falls back to front.
 *
 * 2. Bind-failure fallback: camera-scan's startCamera() swallows bind exceptions, so a
 *    failed bind (observed on some tablets/pads) otherwise leaves a silent black screen.
 *    A watchdog checks whether a camera got bound; if not, it escalates through
 *    [CameraFacingPlan] (preferred-full → preferred-minimal → opposite-full →
 *    opposite-minimal) and finally surfaces a Toast instead of failing silently.
 */
abstract class BarcodeCameraScanActivity : BaseCameraScanActivity<Result>() {

    protected var viewfinderView: ViewfinderView? = null
    private var swipeBackController: EdgeSwipeBackController? = null

    private var ivSwitchCamera: View? = null

    /** Lens facing the user prefers (default back); toggled by the switch button. */
    @CameraSelector.LensFacing
    private var preferredLensFacing = CameraSelector.LENS_FACING_BACK

    /** Lens facing used by the most recently applied camera config. */
    @CameraSelector.LensFacing
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK

    /** Current position in the bind-failure fallback chain, see [CameraFacingPlan]. */
    private var cameraStartAttempt = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val bindWatchdog = Runnable { onBindWatchdog() }

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = findViewById(viewfinderViewId)
        }
        super.initUI()
        val switchCameraId = getSwitchCameraViewId()
        if (switchCameraId != View.NO_ID && switchCameraId != 0) {
            ivSwitchCamera = findViewById(switchCameraId)
            ivSwitchCamera?.setOnClickListener { switchCameraFacing() }
        }
        swipeBackController = EdgeSwipeBackController.install(this)
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // CameraScan's default pinch recognizer calls CameraControl.setZoomRatio().
        // CameraX/HAL owns any physical-lens transition behind that continuous zoom.
        cameraScan.setNeedTouchZoom(true)
        applyCameraConfigForAttempt()
        probeCameraAvailability()
    }

    /**
     * startCamera() is also called again after the camera permission is granted, so the
     * watchdog is (re)armed here and in [requestCameraPermissionResult].
     */
    override fun startCamera() {
        super.startCamera()
        scheduleBindWatchdog()
    }

    override fun requestCameraPermissionResult(granted: Boolean) {
        super.requestCameraPermissionResult(granted)
        if (granted) {
            scheduleBindWatchdog()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(bindWatchdog)
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

    /**
     * 切换前置/后置按钮的 ID；布局中不包含该按钮时可返回 [View.NO_ID] 关闭此功能。
     */
    @IdRes
    open fun getSwitchCameraViewId(): Int {
        return R.id.ivSwitchCamera
    }

    /**
     * 切换前置/后置摄像头；切换后从降级链的起点重新绑定。
     */
    protected fun switchCameraFacing() {
        preferredLensFacing = CameraFacingPlan.opposite(currentLensFacing)
        cameraStartAttempt = 0
        // 换镜头后原闪光灯状态不再有效，复位手电筒图标。
        ivFlashlight?.isSelected = false
        applyCameraConfigForAttempt()
        cameraScan?.startCamera()
        scheduleBindWatchdog()
    }

    private fun applyCameraConfigForAttempt() {
        val facing = CameraFacingPlan.lensFacingForAttempt(preferredLensFacing, cameraStartAttempt)
        currentLensFacing = facing
        val config = if (CameraFacingPlan.useFullConfigForAttempt(cameraStartAttempt)) {
            LogicalMultiCameraConfig(this, facing, true)
        } else {
            MinimalCameraConfig(facing)
        }
        cameraScan?.setCameraConfig(config)
    }

    private fun scheduleBindWatchdog() {
        mainHandler.removeCallbacks(bindWatchdog)
        mainHandler.postDelayed(bindWatchdog, BIND_WATCHDOG_MS)
    }

    private fun onBindWatchdog() {
        if (isFinishing || isDestroyed) return
        // 权限未授予时不走降级：此时可能在等权限弹窗，摄像头为空是正常状态。
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (cameraScan?.camera != null) return // 绑定成功
        if (cameraStartAttempt + 1 >= CameraFacingPlan.MAX_ATTEMPTS) {
            Toast.makeText(this, R.string.zxl_camera_open_failed, Toast.LENGTH_LONG).show()
            return
        }
        cameraStartAttempt++
        applyCameraConfigForAttempt()
        cameraScan?.startCamera()
        scheduleBindWatchdog()
    }

    /**
     * Probes which cameras the device actually has:
     * - no back camera but a front one (some pads/kiosks) → default to front;
     * - both present → show the switch button; otherwise keep it hidden.
     * If the probe itself fails, assume both exist (previous behavior) and show the button.
     */
    private fun probeCameraAvailability() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            var hasBack = true
            var hasFront = true
            try {
                val provider = future.get()
                hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            } catch (e: Exception) {
                // 探测失败时保持默认假设，交给绑定降级链处理。
            }
            ivSwitchCamera?.visibility = if (hasBack && hasFront) View.VISIBLE else View.GONE
            if (!hasBack && hasFront &&
                preferredLensFacing == CameraSelector.LENS_FACING_BACK &&
                cameraScan?.camera == null
            ) {
                // 无后置设备：直接把初始朝向改为前置，避免空等降级链。
                preferredLensFacing = CameraSelector.LENS_FACING_FRONT
                cameraStartAttempt = 0
                applyCameraConfigForAttempt()
                cameraScan?.startCamera()
                scheduleBindWatchdog()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private companion object {
        /** 相机绑定看门狗超时；绑定通常 1 秒内完成，3 秒容忍慢设备。 */
        const val BIND_WATCHDOG_MS = 3000L
    }
}
