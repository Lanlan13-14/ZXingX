package com.king.zxing.config

import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.king.camera.scan.CameraScan
import com.king.logx.LogX
import kotlin.math.max

/**
 * No-button physical lens switching for a logical multi-camera.
 *
 * A continuous "virtual zoom" is driven by pinch gestures. CameraX binds the physical
 * camera whose native field of view best covers that zoom, then applies any remaining
 * zoom with CameraControl. If the HAL exposes no physical cameras, this falls back to
 * regular CameraX zoom.
 */
@OptIn(ExperimentalCamera2Interop::class)
class PhysicalLensController<T : Any>(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val cameraScan: CameraScan<T>,
    private val configFactory: (String?) -> LogicalMultiCameraConfig
) {

    private var lenses: List<PhysicalLens> = emptyList()
    private var mainCameraId: String? = null
    private var currentPhysicalId: String? = null
    private var pendingPhysicalId: String? = null
    private var virtualZoom = 1f
    private var minVirtualZoom = 1f
    private var maxVirtualZoom = 8f
    private var discoveryAttempts = 0
    private var switchGeneration = 0
    private var lastSwitchAt = 0L

    private val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor.takeIf { it.isFinite() && it > 0f } ?: return false
                virtualZoom = (virtualZoom * factor).coerceIn(minVirtualZoom, maxVirtualZoom)
                applyVirtualZoom()
                return true
            }
        }
    )

    private val tapDetector = GestureDetector(
        previewView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (scaleDetector.isInProgress) return false
                val camera = cameraScan.getCamera() ?: return false
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                val action = FocusMeteringAction.Builder(point).build()
                if (camera.cameraInfo.isFocusMeteringSupported(action)) {
                    camera.cameraControl.startFocusAndMetering(action)
                    return true
                }
                return false
            }
        }
    )

    fun install() {
        // Replace CameraScan's default touch listener: retain tap-to-focus and zoom,
        // while adding actual physical-camera rebinding.
        cameraScan.setNeedTouchZoom(false)
        previewView.setOnTouchListener { _, event ->
            val scaleHandled = scaleDetector.onTouchEvent(event)
            val tapHandled = tapDetector.onTouchEvent(event)
            scaleHandled || tapHandled || event.pointerCount > 1
        }
        discoverWhenReady()
    }

    private fun discoverWhenReady() {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            previewView.postDelayed(::discoverWhenReady, 100L)
            return
        }
        val camera = cameraScan.getCamera()
        if (camera == null) {
            if (discoveryAttempts++ < 40) previewView.postDelayed(::discoverWhenReady, 100L)
            return
        }

        val logicalInfo = camera.cameraInfo
        val zoomState = logicalInfo.zoomState.value
        minVirtualZoom = zoomState?.minZoomRatio ?: 1f
        maxVirtualZoom = zoomState?.maxZoomRatio ?: 8f
        virtualZoom = (zoomState?.zoomRatio ?: 1f).coerceIn(minVirtualZoom, maxVirtualZoom)

        if (!logicalInfo.isLogicalMultiCameraSupported) {
            LogX.d("Physical lens switching unavailable: logical multi-camera not exposed")
            return
        }

        val scores = logicalInfo.physicalCameraInfos.mapNotNull { info ->
            val camera2 = Camera2CameraInfo.from(info)
            val id = camera2.cameraId
            opticalScore(camera2)?.let { id to it }
        }
        val logicalMain = opticalScore(Camera2CameraInfo.from(logicalInfo))
        lenses = PhysicalLensStrategy.normalized(scores, logicalMain)
        if (lenses.size < 2) {
            LogX.d("Physical lens switching unavailable: only %d physical lens(es)", lenses.size)
            lenses = emptyList()
            return
        }

        mainCameraId = lenses.minByOrNull { kotlin.math.abs(it.opticalRatio - 1f) }?.cameraId
        minVirtualZoom = minOf(minVirtualZoom, lenses.first().opticalRatio)
        maxVirtualZoom = max(maxVirtualZoom, lenses.last().opticalRatio * 4f)
        virtualZoom = virtualZoom.coerceIn(minVirtualZoom, maxVirtualZoom)

        LogX.d(
            "Physical lenses ready: %s; virtualZoom=[%f,%f]",
            lenses.joinToString { "${it.cameraId}:${"%.2f".format(it.opticalRatio)}x" },
            minVirtualZoom,
            maxVirtualZoom
        )
    }

    private fun opticalScore(camera2: Camera2CameraInfo): Float? {
        return try {
            val focal = camera2.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )?.filter { it > 0f }?.minOrNull() ?: return null
            val sensor = camera2.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            ) ?: return focal
            if (sensor.width > 0f) focal / sensor.width else focal
        } catch (e: Exception) {
            LogX.w(e)
            null
        }
    }

    private fun applyVirtualZoom() {
        if (lenses.isEmpty()) {
            cameraScan.zoomTo(virtualZoom)
            return
        }

        val lens = PhysicalLensStrategy.select(lenses, virtualZoom) ?: return
        // Keep logical camera for main (lets OEM seamless switching work too); bind explicit
        // physical IDs only for ultra-wide / tele.
        val desiredPhysical = if (lens.cameraId == mainCameraId) null else lens.cameraId
        val localZoom = PhysicalLensStrategy.localZoom(lens, virtualZoom)

        if (desiredPhysical == currentPhysicalId && pendingPhysicalId == null) {
            cameraScan.zoomTo(localZoom)
            return
        }

        // Avoid restart storms near a focal boundary.
        val now = SystemClock.uptimeMillis()
        if (pendingPhysicalId != null || now - lastSwitchAt < 180L) return
        lastSwitchAt = now
        pendingPhysicalId = desiredPhysical
        val generation = ++switchGeneration
        val restoreTorch = cameraScan.isTorchEnabled()
        val previousCamera = cameraScan.getCamera()

        cameraScan.setCameraConfig(configFactory(desiredPhysical))
        cameraScan.startCamera()
        awaitRebind(generation, previousCamera, desiredPhysical, localZoom, restoreTorch, 0)
    }

    private fun awaitRebind(
        generation: Int,
        previousCamera: androidx.camera.core.Camera?,
        desiredPhysical: String?,
        localZoom: Float,
        restoreTorch: Boolean,
        attempt: Int
    ) {
        if (generation != switchGeneration) return
        val camera = cameraScan.getCamera()
        // startCamera() is async; wait for the old Camera object to be replaced.
        if (camera == null || camera === previousCamera) {
            if (attempt < 60) {
                previewView.postDelayed(
                    {
                        awaitRebind(
                            generation,
                            previousCamera,
                            desiredPhysical,
                            localZoom,
                            restoreTorch,
                            attempt + 1
                        )
                    },
                    50L
                )
            } else {
                pendingPhysicalId = null
                LogX.w("Timed out rebinding physical camera: %s", desiredPhysical ?: "logical")
                fallbackToLogical(localZoom)
            }
            return
        }

        currentPhysicalId = desiredPhysical
        pendingPhysicalId = null
        cameraScan.zoomTo(localZoom)
        if (restoreTorch && cameraScan.hasFlashUnit()) cameraScan.enableTorch(true)
        LogX.d(
            "Physical camera rebound: %s, virtual=%f, local=%f",
            desiredPhysical ?: "logical-main",
            virtualZoom,
            localZoom
        )
    }

    private fun fallbackToLogical(localZoom: Float) {
        // A physical ID can be advertised but rejected for this Preview+ImageAnalysis session.
        // Return to the logical camera rather than leaving a black preview.
        currentPhysicalId = null
        pendingPhysicalId = null
        cameraScan.setCameraConfig(configFactory(null))
        cameraScan.startCamera()
        previewView.postDelayed({ cameraScan.zoomTo(max(1f, localZoom)) }, 250L)
    }
}
                                                                             