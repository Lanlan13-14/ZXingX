package com.king.zxing.config

import android.hardware.camera2.CameraCharacteristics
import android.util.SizeF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.king.camera.scan.CameraScan
import com.king.logx.LogX
import kotlin.math.max

/**
 * Cross-vendor physical lens controller using only CameraX / Camera2 standard APIs.
 *
 * Discovery layers:
 * 1. Physical children of every logical rear camera (`physicalCameraInfos`).
 * 2. Independently exposed rear CameraX IDs (`availableCameraInfos`).
 * 3. Logical camera zoom-ratio fallback when neither can be explicitly rebound.
 */
@OptIn(ExperimentalCamera2Interop::class)
class PhysicalLensController<T>(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val cameraScan: CameraScan<T>,
    private val configFactory: (CameraBinding?) -> LogicalMultiCameraConfig
) {
    private var lenses: List<LensCandidate> = emptyList()
    private var virtualZoom = 1f
    private var currentBinding: CameraBinding? = null
    private var pendingCandidate: LensCandidate? = null
    private var switchGeneration = 0
    private var fallbackZoomOnly = false
    private var initialized = false

    private val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val maxVirtual = maxVirtualZoom()
                virtualZoom = (virtualZoom * detector.scaleFactor)
                    .coerceIn(minVirtualZoom(), maxVirtual)
                applyVirtualZoom()
                return true
            }
        }
    )

    fun install() {
        // Disable CameraScan's duplicate recognizer. This detector handles physical switches
        // and still falls back to CameraControl.zoomTo when explicit binding is unavailable.
        cameraScan.setNeedTouchZoom(false)
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            handleTapToFocus(event)
            true
        }
        awaitInitialCamera(0)
    }

    private fun awaitInitialCamera(attempt: Int) {
        val camera = cameraScan.getCamera()
        if (camera == null) {
            if (attempt < 80) previewView.postDelayed({ awaitInitialCamera(attempt + 1) }, 50L)
            return
        }
        if (!initialized) {
            initialized = true
            discoverAllRearLenses(camera.cameraInfo)
        }
    }

    private fun discoverAllRearLenses(activeLogicalInfo: CameraInfo) {
        val raw = mutableListOf<Triple<String, Float, List<CameraBinding>>>()
        val provider = runCatching {
            androidx.camera.lifecycle.ProcessCameraProvider.getInstance(previewView.context).get()
        }.getOrNull()
        val allRear = provider?.availableCameraInfos
            ?.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .orEmpty()

        val logicalInfos = (allRear + activeLogicalInfo)
            .distinctBy { cameraId(it) ?: it.toString() }
            .filter { it.isLogicalMultiCameraSupported }

        // Physical children. Lock each physical ID to its owning logical camera.
        logicalInfos.forEach { logical ->
            val logicalId = cameraId(logical) ?: return@forEach
            logical.physicalCameraInfos.forEach { physical ->
                val physicalId = cameraId(physical) ?: return@forEach
                val score = opticalScore(physical) ?: return@forEach
                raw += Triple(
                    "lens:$physicalId",
                    score,
                    listOf(CameraBinding.Physical(logicalId, physicalId))
                )
            }
        }

        // Independently exposed rear IDs. Some OEMs expose auxiliary lenses this way only.
        allRear.filterNot { it.isLogicalMultiCameraSupported }.forEach { info ->
            val id = cameraId(info) ?: return@forEach
            val score = opticalScore(info) ?: return@forEach
            val stable = "lens:$id"
            val existing = raw.indexOfFirst { it.first == stable }
            val exact = CameraBinding.Exact(id)
            if (existing >= 0) {
                val old = raw[existing]
                raw[existing] = Triple(old.first, old.second, old.third + exact)
            } else {
                raw += Triple(stable, score, listOf(exact))
            }
        }

        val mainScore = opticalScore(activeLogicalInfo)
        lenses = PhysicalLensStrategy.normalized(raw, mainScore)
        fallbackZoomOnly = lenses.size < 2
        virtualZoom = 1f.coerceIn(minVirtualZoom(), maxVirtualZoom())
        currentBinding = lensNearOne()?.bindings?.firstOrNull()

        LogX.d(
            "CameraX lens discovery: rear=%d logical=%d candidates=%d fallbackZoom=%s",
            allRear.size,
            logicalInfos.size,
            lenses.size,
            fallbackZoomOnly
        )
        lenses.forEach { lens ->
            LogX.d(
                "Lens %s score=%f ratio=%f bindings=%s",
                lens.stableId,
                lens.opticalScore,
                lens.opticalRatio,
                lens.bindings
            )
        }
    }

    private fun applyVirtualZoom() {
        if (fallbackZoomOnly || lenses.isEmpty()) {
            cameraScan.zoomTo(virtualZoom)
            return
        }
        val target = PhysicalLensStrategy.select(lenses, virtualZoom) ?: run {
            cameraScan.zoomTo(virtualZoom)
            return
        }
        val localZoom = PhysicalLensStrategy.localZoom(target, virtualZoom)
        if (target.bindings.contains(currentBinding)) {
            cameraScan.zoomTo(localZoom)
            return
        }
        if (pendingCandidate?.stableId == target.stableId) return
        pendingCandidate = target
        tryBinding(target, 0, localZoom)
    }

    private fun tryBinding(target: LensCandidate, index: Int, localZoom: Float) {
        if (index >= target.bindings.size) {
            LogX.w("All CameraX bindings failed for %s; logical zoom fallback", target.stableId)
            pendingCandidate = null
            fallbackToLogical(localZoom)
            return
        }
        val binding = target.bindings[index]
        switchGeneration += 1
        val generation = switchGeneration
        val previousCamera = cameraScan.getCamera()
        val restoreTorch = cameraScan.isTorchEnabled()
        cameraScan.setCameraConfig(configFactory(binding))
        cameraScan.startCamera()
        awaitRebind(
            generation,
            previousCamera,
            target,
            index,
            binding,
            localZoom,
            restoreTorch,
            0
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
        attempt: Int
    ) {
        if (generation != switchGeneration) return
        val camera = cameraScan.getCamera()
        if (camera == null || camera === previousCamera) {
            if (attempt < 60) {
                previewView.postDelayed({
                    awaitRebind(
                        generation,
                        previousCamera,
                        target,
                        bindingIndex,
                        binding,
                        localZoom,
                        restoreTorch,
                        attempt + 1
                    )
                }, 50L)
            } else {
                LogX.w("CameraX bind timed out: %s", binding)
                tryBinding(target, bindingIndex + 1, localZoom)
            }
            return
        }

        currentBinding = binding
        pendingCandidate = null
        cameraScan.zoomTo(localZoom)
        if (restoreTorch && cameraScan.hasFlashUnit()) cameraScan.enableTorch(true)
        LogX.d(
            "CameraX lens bound: %s virtual=%f local=%f",
            binding,
            virtualZoom,
            localZoom
        )
    }

    private fun fallbackToLogical(localZoom: Float) {
        currentBinding = null
        pendingCandidate = null
        cameraScan.setCameraConfig(configFactory(null))
        cameraScan.startCamera()
        previewView.postDelayed({ cameraScan.zoomTo(max(1f, localZoom)) }, 300L)
    }

    private fun handleTapToFocus(event: MotionEvent) {
        if (event.pointerCount != 1 || event.actionMasked != MotionEvent.ACTION_UP) return
        val camera = cameraScan.getCamera() ?: return
        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
        val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    private fun lensNearOne(): LensCandidate? {
        return lenses.minByOrNull { kotlin.math.abs(it.opticalRatio - 1f) }
    }

    private fun minVirtualZoom(): Float {
        return lenses.firstOrNull()?.opticalRatio?.coerceAtMost(1f)
            ?: cameraScan.getCamera()?.cameraInfo?.zoomState?.value?.minZoomRatio
            ?: 1f
    }

    private fun maxVirtualZoom(): Float {
        val digitalMax = cameraScan.getCamera()?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 8f
        val opticalMax = lenses.lastOrNull()?.opticalRatio ?: 1f
        return max(1f, max(digitalMax, opticalMax * 4f))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun cameraId(info: CameraInfo): String? = runCatching {
        Camera2CameraInfo.from(info).cameraId
    }.getOrNull()

    @OptIn(ExperimentalCamera2Interop::class)
    private fun opticalScore(info: CameraInfo): Float? = runCatching {
        val camera2 = Camera2CameraInfo.from(info)
        val focal = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.filter { it > 0f }
            ?.minOrNull()
            ?: return@runCatching null
        val sensor = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        focal / horizontalSensorWidth(sensor)
    }.getOrNull()

    private fun horizontalSensorWidth(size: SizeF?): Float {
        val width = size?.width ?: 0f
        return if (width > 0f && width.isFinite()) width else 1f
    }
}
