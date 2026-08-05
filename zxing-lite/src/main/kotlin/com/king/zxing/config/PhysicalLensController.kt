package com.king.zxing.config

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.king.camera.scan.CameraScan
import com.king.logx.LogX
import kotlin.math.max

/**
 * Pinch-driven multi-camera controller built only on public Android/CameraX APIs.
 *
 * The controller discovers the cameras that CameraX exposes, keeps the parent logical camera ID
 * for every physical child, and rebinds Preview/ImageAnalysis through [LogicalMultiCameraConfig]
 * when the virtual zoom crosses a declared optical ratio. If discovery or binding is unsupported,
 * it falls back to CameraControl.setZoomRatio() on the logical camera.
 *
 * There is intentionally no lens button or vendor/model branch. Camera IDs are opaque values
 * returned by Android and are never interpreted by their spelling or numeric value.
 */
@OptIn(ExperimentalCamera2Interop::class)
class PhysicalLensController<T : Any>(
    private val previewView: PreviewView,
    private val cameraScan: CameraScan<T>,
    private val configFactory: (CameraBinding?) -> LogicalMultiCameraConfig,
) {

    private var candidates: List<LensCandidate> = emptyList()
    private var defaultBinding: CameraBinding? = null
    private var currentBinding: CameraBinding? = null
    private var currentLogicalCameraId: String? = null
    private var pendingCandidate: LensCandidate? = null
    @Volatile
    private var physicalOutputObserved = false
    private var virtualZoom = 1f
    private var minVirtualZoom = 1f
    private var maxVirtualZoom = 1f
    private var switchGeneration = 0
    private var initialized = false
    private var released = false
    private val failedBindings = mutableSetOf<CameraBinding>()

    private var tapEligible = false
    private var tapDownX = 0f
    private var tapDownY = 0f

    private val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                    .takeIf { it.isFinite() && it > 0f }
                    ?: return false
                virtualZoom = (virtualZoom * factor)
                    .coerceIn(minVirtualZoom, maxVirtualZoom)
                applyVirtualZoom()
                return true
            }
        },
    )

    fun install() {
        cameraScan.setNeedTouchZoom(false)
        previewView.setOnTouchListener { _, event ->
            handleTapState(event)
            scaleDetector.onTouchEvent(event)
            true
        }
        awaitInitialCamera(0)
    }

    fun release() {
        if (released) return
        released = true
        switchGeneration++
        previewView.setOnTouchListener(null)
    }

    private fun awaitInitialCamera(attempt: Int) {
        if (released || initialized) return
        val camera = cameraScan.getCamera()
        if (camera == null) {
            if (attempt < INITIAL_CAMERA_ATTEMPTS) {
                previewView.postDelayed({ awaitInitialCamera(attempt + 1) }, REBIND_POLL_MS)
            }
            return
        }

        initialized = true
        val initialCameraId = cameraId(camera.cameraInfo)
        val initialIsLogical = runCatching {
            camera.cameraInfo.isLogicalMultiCameraSupported
        }.getOrDefault(false)
        currentLogicalCameraId = initialCameraId?.takeIf { initialIsLogical }
        defaultBinding = initialCameraId?.let { id ->
            if (initialIsLogical) CameraBinding.Logical(id) else CameraBinding.Exact(id)
        }
        currentBinding = defaultBinding
        discoverLenses(camera.cameraInfo)
    }

    private fun discoverLenses(activeInfo: CameraInfo) {
        val provider = runCatching {
            ProcessCameraProvider.getInstance(previewView.context).get()
        }.getOrNull()

        val rearInfos = (provider?.availableCameraInfos.orEmpty() + activeInfo)
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .distinctBy { cameraId(it) ?: it.toString() }

        val activeScore = opticalScore(activeInfo)
        if (activeScore == null) {
            LogX.w("CameraX multi-camera discovery unavailable: logical optical metadata missing")
            candidates = emptyList()
            useLogicalZoomRange(activeInfo)
            return
        }

        val raw = mutableListOf<LensDescriptor>()
        rearInfos.forEach { info ->
            val id = cameraId(info) ?: return@forEach
            val score = opticalScore(info) ?: return@forEach
            val logical = runCatching { info.isLogicalMultiCameraSupported }
                .getOrDefault(false)

            if (logical) {
                raw += LensDescriptor(
                    stableId = "logical:$id",
                    opticalScore = score,
                    maxLocalZoom = maxZoom(info),
                    binding = CameraBinding.Logical(id),
                )

                val physicalInfos = runCatching { info.physicalCameraInfos }
                    .getOrDefault(emptySet())
                physicalInfos.forEach { physical ->
                    val physicalId = cameraId(physical) ?: return@forEach
                    val physicalScore = opticalScore(physical) ?: return@forEach
                    raw += LensDescriptor(
                        stableId = "physical:$id/$physicalId",
                        opticalScore = physicalScore,
                        maxLocalZoom = maxZoom(physical),
                        binding = CameraBinding.Physical(id, physicalId),
                    )
                }
            } else {
                raw += LensDescriptor(
                    stableId = "exact:$id",
                    opticalScore = score,
                    maxLocalZoom = maxZoom(info),
                    binding = CameraBinding.Exact(id),
                )
            }
        }

        candidates = PhysicalLensStrategy.normalized(raw, activeScore)
        minVirtualZoom = candidates.minOfOrNull { it.opticalRatio }
            ?.coerceAtMost(1f)
            ?: 1f
        maxVirtualZoom = max(
            1f,
            candidates.maxOfOrNull {
                it.opticalRatio * it.maxLocalZoom.coerceAtLeast(1f)
            } ?: logicalMaxZoom(activeInfo),
        )
        virtualZoom = virtualZoom.coerceIn(minVirtualZoom, maxVirtualZoom)

        val explicitCount = candidates.count { candidate ->
            candidate.bindings.any { it !is CameraBinding.Logical }
        }
        LogX.d(
            "CameraX multi-camera discovery: rear=%d candidates=%d explicit=%d min=%f max=%f",
            rearInfos.size,
            candidates.size,
            explicitCount,
            minVirtualZoom,
            maxVirtualZoom,
        )
        candidates.forEach { candidate ->
            LogX.d(
                "CameraX lens candidate: %s ratio=%f maxLocal=%f bindings=%s",
                candidate.stableId,
                candidate.opticalRatio,
                candidate.maxLocalZoom,
                candidate.bindings,
            )
        }
    }

    private fun useLogicalZoomRange(info: CameraInfo) {
        minVirtualZoom = 1f
        maxVirtualZoom = logicalMaxZoom(info).coerceAtLeast(1f)
        virtualZoom = virtualZoom.coerceIn(minVirtualZoom, maxVirtualZoom)
    }

    private fun applyVirtualZoom() {
        if (released) return
        if (candidates.isEmpty()) {
            cameraScan.zoomTo(virtualZoom)
            return
        }

        val activeBinding = currentBinding?.takeUnless { it in failedBindings }
        val available = candidates.filter { candidate ->
            candidate.bindings.any { it !in failedBindings }
        }
        val target = PhysicalLensStrategy.select(
            lenses = available,
            virtualZoom = virtualZoom,
            currentBinding = activeBinding,
        ) ?: run {
            cameraScan.zoomTo(virtualZoom)
            return
        }
        val localZoom = PhysicalLensStrategy.localZoom(target, virtualZoom)

        if (target.bindings.contains(currentBinding)) {
            pendingCandidate = null
            cameraScan.zoomTo(localZoom)
            return
        }

        if (pendingCandidate?.stableId == target.stableId) return
        pendingCandidate = target
        tryBinding(target, 0)
    }

    private fun tryBinding(target: LensCandidate, index: Int) {
        if (released || target.bindings.isEmpty()) return
        val binding = target.bindings
            .drop(index)
            .firstOrNull { it !in failedBindings }
            ?: run {
                pendingCandidate = null
                fallbackToLogical(target)
                return
            }
        val bindingIndex = target.bindings.indexOf(binding)
        val generation = ++switchGeneration
        val previousCamera = cameraScan.getCamera()
        val localZoom = PhysicalLensStrategy.localZoom(target, virtualZoom)
        val restoreTorch = cameraScan.isTorchEnabled()
        physicalOutputObserved = binding !is CameraBinding.Physical

        val config = configFactory(binding)
        config.setPhysicalCaptureCallback { observedId ->
            if (generation == switchGeneration &&
                binding is CameraBinding.Physical &&
                observedId == binding.physicalCameraId
            ) {
                physicalOutputObserved = true
            }
        }
        cameraScan.setCameraConfig(config)
        cameraScan.startCamera()
        awaitRebind(
            generation = generation,
            previousCamera = previousCamera,
            target = target,
            bindingIndex = bindingIndex,
            binding = binding,
            localZoom = localZoom,
            restoreTorch = restoreTorch,
            attempt = 0,
        )
    }

    private fun awaitRebind(
        generation: Int,
        previousCamera: androidx.camera.core.Camera?,
        target: LensCandidate,
        bindingIndex: Int,
        binding: CameraBinding,
        localZoom: Float,
        restoreTorch: Boolean,
        attempt: Int,
    ) {
        if (released || generation != switchGeneration) return
        val camera = cameraScan.getCamera()
        if (camera == null || camera === previousCamera) {
            if (attempt < REBIND_ATTEMPTS) {
                previewView.postDelayed({
                    awaitRebind(
                        generation,
                        previousCamera,
                        target,
                        bindingIndex,
                        binding,
                        localZoom,
                        restoreTorch,
                        attempt + 1,
                    )
                }, REBIND_POLL_MS)
            } else {
                failedBindings += binding
                LogX.w("CameraX multi-camera binding failed: %s", binding)
                tryBinding(target, bindingIndex + 1)
            }
            return
        }

        if (binding is CameraBinding.Physical && !physicalOutputObserved) {
            if (attempt < REBIND_ATTEMPTS) {
                previewView.postDelayed({
                    awaitRebind(
                        generation,
                        previousCamera,
                        target,
                        bindingIndex,
                        binding,
                        localZoom,
                        restoreTorch,
                        attempt + 1,
                    )
                }, REBIND_POLL_MS)
            } else {
                failedBindings += binding
                LogX.w("CameraX physical output was not observed: %s", binding)
                tryBinding(target, bindingIndex + 1)
            }
            return
        }

        currentBinding = binding
        currentLogicalCameraId = logicalCameraId(binding) ?: currentLogicalCameraId
        pendingCandidate = null
        cameraScan.zoomTo(localZoom)
        if (restoreTorch && cameraScan.hasFlashUnit()) {
            cameraScan.enableTorch(true)
        }
        LogX.d(
            "CameraX multi-camera lens bound: %s virtual=%f local=%f",
            binding,
            virtualZoom,
            localZoom,
        )
    }

    private fun fallbackToLogical(target: LensCandidate) {
        pendingCandidate = null
        val logicalId = logicalCameraId(target.bindings.firstOrNull())
            ?: currentLogicalCameraId
        val logicalBinding = logicalId?.let(CameraBinding::Logical)
            ?: defaultBinding
        if (logicalBinding !is CameraBinding.Logical) {
            cameraScan.zoomTo(virtualZoom)
            return
        }

        val generation = ++switchGeneration
        val previousCamera = cameraScan.getCamera()
        val restoreTorch = cameraScan.isTorchEnabled()
        cameraScan.setCameraConfig(configFactory(logicalBinding))
        cameraScan.startCamera()
        awaitLogicalFallback(
            generation = generation,
            previousCamera = previousCamera,
            logicalBinding = logicalBinding,
            restoreTorch = restoreTorch,
            attempt = 0,
        )
    }

    private fun awaitLogicalFallback(
        generation: Int,
        previousCamera: androidx.camera.core.Camera?,
        logicalBinding: CameraBinding.Logical,
        restoreTorch: Boolean,
        attempt: Int,
    ) {
        if (released || generation != switchGeneration) return
        val camera = cameraScan.getCamera()
        if (camera == null || camera === previousCamera) {
            if (attempt < REBIND_ATTEMPTS) {
                previewView.postDelayed({
                    awaitLogicalFallback(
                        generation,
                        previousCamera,
                        logicalBinding,
                        restoreTorch,
                        attempt + 1,
                    )
                }, REBIND_POLL_MS)
            }
            return
        }

        currentBinding = logicalBinding
        currentLogicalCameraId = logicalBinding.cameraId
        cameraScan.zoomTo(virtualZoom)
        if (restoreTorch && cameraScan.hasFlashUnit()) {
            cameraScan.enableTorch(true)
        }
    }

    private fun handleTapState(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapEligible = true
                tapDownX = event.x
                tapDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (distance(tapDownX, tapDownY, event.x, event.y) >=
                    ViewConfiguration.get(previewView.context).scaledTouchSlop
                ) {
                    tapEligible = false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tapEligible && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    focus(event.x, event.y)
                }
                tapEligible = false
            }
            MotionEvent.ACTION_CANCEL -> tapEligible = false
        }
    }

    private fun focus(x: Float, y: Float) {
        val camera = cameraScan.getCamera() ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        if (camera.cameraInfo.isFocusMeteringSupported(action)) {
            camera.cameraControl.startFocusAndMetering(action)
        }
    }

    private fun logicalCameraId(binding: CameraBinding?): String? = when (binding) {
        is CameraBinding.Logical -> binding.cameraId
        is CameraBinding.Physical -> binding.logicalCameraId
        is CameraBinding.Exact, null -> null
    }

    private fun cameraId(info: CameraInfo): String? = runCatching {
        Camera2CameraInfo.from(info).cameraId
    }.getOrNull()

    private fun opticalScore(info: CameraInfo): Float? = runCatching {
        val camera2 = Camera2CameraInfo.from(info)
        val focal = camera2.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
        )?.firstOrNull { it > 0f } ?: return@runCatching null
        val sensor = camera2.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
        ) ?: return@runCatching null
        val active = camera2.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,
        ) ?: return@runCatching null
        val pixel = camera2.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
        ) ?: return@runCatching null
        val orientation = camera2.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_ORIENTATION,
        ) ?: return@runCatching null

        val horizontalSensor = horizontalSensorLength(sensor, active, pixel, orientation)
        val viewAngle = 2.0 * kotlin.math.atan(horizontalSensor / (2.0 * focal))
        val score = 1.0 / viewAngle
        score.toFloat().takeIf { it.isFinite() && it > 0f }
    }.getOrNull()

    private fun horizontalSensorLength(
        sensor: SizeF,
        active: Rect,
        pixel: Size,
        orientation: Int,
    ): Double {
        val rotated = orientation == 90 || orientation == 270
        val sensorWidth = if (rotated) sensor.height.toDouble() else sensor.width.toDouble()
        val activeWidth = if (rotated) active.height().toDouble() else active.width().toDouble()
        val pixelWidth = if (rotated) pixel.height.toDouble() else pixel.width.toDouble()
        require(sensorWidth > 0.0 && activeWidth > 0.0 && pixelWidth > 0.0)
        return sensorWidth * activeWidth / pixelWidth
    }

    private fun maxZoom(info: CameraInfo): Float {
        val stateMax = runCatching { info.zoomState.value?.maxZoomRatio }.getOrNull()
        if (stateMax != null && stateMax.isFinite() && stateMax >= 1f) return stateMax
        return logicalMaxZoom(info)
    }

    private fun logicalMaxZoom(info: CameraInfo): Float = runCatching {
        val camera2 = Camera2CameraInfo.from(info)
        val max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            camera2.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE,
            )?.upper
        } else {
            camera2.getCameraCharacteristic(
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
            )
        }
        max?.takeIf { it.isFinite() && it >= 1f } ?: 1f
    }.getOrDefault(1f)

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // Delayed callbacks are invalidated by released/switchGeneration checks.

    // Lens descriptors are defined in LogicalMultiCameraConfig.

    private companion object {
        const val REBIND_POLL_MS = 50L
        const val INITIAL_CAMERA_ATTEMPTS = 100
        const val REBIND_ATTEMPTS = 100
    }
}
