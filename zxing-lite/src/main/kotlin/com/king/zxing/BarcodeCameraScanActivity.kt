package com.king.zxing

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
import com.king.zxing.config.FillLightPlan
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
 * Features layered on top of CameraScan:
 *
 * 1. Front/back switching: layouts may contain an `ivSwitchCamera` view (bottom center);
 *    tapping it plays a rotateY card-flip on the preview and rebinds the opposite lens
 *    facing at the edge-on midpoint. The button is only shown when the device reports
 *    both cameras. Default facing is back; devices without a back camera (some
 *    pads/kiosks) start on the front camera automatically.
 *
 * 2. Bind-failure fallback: camera-scan's startCamera() swallows bind exceptions, so a
 *    failed bind (observed on some tablets/pads) otherwise leaves a silent black screen.
 *    A watchdog checks whether a camera got bound; if not, it escalates through
 *    [CameraFacingPlan] (preferred-full → preferred-minimal → opposite-full →
 *    opposite-minimal) and finally surfaces a Toast instead of failing silently.
 *
 * 3. Screen flash ([FillLightPlan]): on cameras without a flash unit (front) the
 *    flashlight button lights two white bars (top/bottom) at full window brightness
 *    instead of the torch. The flashlight icon is self-managed (always visible)
 *    because camera-scan's ambient-light auto-hide would hide it exactly when the
 *    screen flash lights the room up.
 *
 * 4. Tablets (sw600dp) are unlocked to FULL_SENSOR orientation; phones keep the
 *    manifest's portrait lock.
 */
abstract class BarcodeCameraScanActivity : BaseCameraScanActivity<Result>() {

    protected var viewfinderView: ViewfinderView? = null
    private var swipeBackController: EdgeSwipeBackController? = null

    private var ivSwitchCamera: View? = null

    /** 前置补光区域（上下两条白色）；布局中可缺省。 */
    private var screenFlashTop: View? = null
    private var screenFlashBottom: View? = null

    /** 屏幕补光是否点亮（无闪光灯摄像头时手电筒按钮的替代行为）。 */
    private var isScreenFlashOn = false

    /** 点亮补光前的窗口亮度，用于恢复；-1 表示系统跟随（BRIGHTNESS_OVERRIDE_NONE）。 */
    private var savedScreenBrightness: Float? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 平板（sw600dp）放开方向锁定；手机维持清单里的竖屏声明。
        if (resources.getBoolean(R.bool.zxl_is_tablet)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

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
        screenFlashTop = findViewById(R.id.screenFlashTop)
        screenFlashBottom = findViewById(R.id.screenFlashBottom)
        sizeScreenFlashViews()
        swipeBackController = EdgeSwipeBackController.install(this)
    }

    /** 补光区域高度：上 20% / 下 25% 屏幕高，参考微信前置补光的占比。 */
    private fun sizeScreenFlashViews() {
        val height = resources.displayMetrics.heightPixels
        screenFlashTop?.layoutParams?.height = (height * SCREEN_FLASH_TOP_RATIO).toInt()
        screenFlashBottom?.layoutParams?.height = (height * SCREEN_FLASH_BOTTOM_RATIO).toInt()
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // CameraScan's default pinch recognizer calls CameraControl.setZoomRatio().
        // CameraX/HAL owns any physical-lens transition behind that continuous zoom.
        cameraScan.setNeedTouchZoom(true)
        // 手电筒图标改为常驻、由本类自管：继续挂在光照传感器上时，屏幕补光会把
        // 环境照亮，管理器随即把图标隐藏——用户将无法再关掉补光。
        cameraScan.bindFlashlightView(null)
        ivFlashlight?.visibility = View.VISIBLE
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
        if (isScreenFlashOn) {
            setScreenFlash(false) // 恢复窗口亮度，避免带出到其它页面
        }
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
     * 手电筒按钮：有闪光单元的摄像头走硬件 torch；没有（前置等）改为屏幕补光——
     * 点亮上下两条白色区域并把窗口亮度拉满（微信/iOS 相机同款前置补光）。
     */
    override fun onClickFlashlight() {
        val hasFlashUnit = cameraScan?.camera?.cameraInfo?.hasFlashUnit()
        if (FillLightPlan.useScreenFlash(currentLensFacing, hasFlashUnit)) {
            setScreenFlash(!isScreenFlashOn)
        } else {
            super.onClickFlashlight()
        }
    }

    private fun setScreenFlash(on: Boolean) {
        if (on == isScreenFlashOn) return
        isScreenFlashOn = on
        screenFlashTop?.visibility = if (on) View.VISIBLE else View.GONE
        screenFlashBottom?.visibility = if (on) View.VISIBLE else View.GONE
        ivFlashlight?.isSelected = on
        val attrs = window.attributes
        if (on) {
            if (savedScreenBrightness == null) savedScreenBrightness = attrs.screenBrightness
            attrs.screenBrightness = 1.0f // BRIGHTNESS_OVERRIDE_FULL
        } else {
            attrs.screenBrightness =
                savedScreenBrightness ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            savedScreenBrightness = null
        }
        window.attributes = attrs
    }

    /**
     * 切换前置/后置摄像头；预览做绕 Y 轴的卡片翻转动画（0→90° 期间完成换绑，
     * -90°→0° 展示新画面），切换后从降级链的起点重新绑定。
     */
    protected fun switchCameraFacing() {
        // 换镜头后原补光/闪光灯状态不再有效。
        setScreenFlash(false)
        ivFlashlight?.isSelected = false

        val preview = previewView
        if (preview == null || preview.width == 0) {
            performCameraSwitch()
            return
        }
        preview.animate().cancel()
        // 透视强度对齐参考（perspective:1200px / 320px 卡片宽 ≈ 3.75）。
        preview.cameraDistance = preview.width * FLIP_PERSPECTIVE_RATIO
        preview.animate()
            .rotationY(90f)
            .setDuration(FLIP_HALF_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(FLIP_INTERPOLATOR_FACTOR))
            .withEndAction {
                performCameraSwitch()
                preview.rotationY = -90f
                preview.animate()
                    .rotationY(0f)
                    .setDuration(FLIP_HALF_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator(FLIP_INTERPOLATOR_FACTOR))
                    .start()
            }
            .start()
    }

    private fun performCameraSwitch() {
        preferredLensFacing = CameraFacingPlan.opposite(currentLensFacing)
        cameraStartAttempt = 0
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

        /** 翻转动画半程时长；参考实现全程 0.8s，相机切换收紧到 0.6s 保持跟手。 */
        const val FLIP_HALF_DURATION_MS = 300L

        /** 参考卡片的透视比：perspective 1200px / 卡片宽 320px。 */
        const val FLIP_PERSPECTIVE_RATIO = 3.75f

        const val FLIP_INTERPOLATOR_FACTOR = 0.6f

        const val SCREEN_FLASH_TOP_RATIO = 0.20f
        const val SCREEN_FLASH_BOTTOM_RATIO = 0.25f
    }
}
