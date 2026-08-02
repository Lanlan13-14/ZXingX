package com.king.zxing.config

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AdaptiveCameraConfig
import com.king.logx.LogX

/**
 * Prefer the device's **logical multi-camera** on the back side.
 *
 * On modern OEM stacks (Pixel / Samsung / Xiaomi / OPPO / …) the system exposes one
 * logical rear camera that maps [CameraControl.setZoomRatio] across ultra-wide /
 * main / tele physical sensors. CameraX already does that mapping — we only need to:
 * 1. Select the logical multi-camera when present (not a single physical sensor).
 * 2. Keep pinch-to-zoom enabled (CameraScan default) so the user drives zoomRatio.
 *
 * No extra UI. If the device has no logical multi-camera capability, fall back to the
 * back camera with the widest zoom range.
 */
@OptIn(ExperimentalCamera2Interop::class)
open class LogicalMultiCameraConfig(context: Context) : AdaptiveCameraConfig(context) {

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        builder.addCameraFilter { cameraInfos ->
            preferLogicalMultiCamera(cameraInfos)
        }
        return super.options(builder)
    }

    private fun preferLogicalMultiCamera(cameraInfos: List<CameraInfo>): List<CameraInfo> {
        if (cameraInfos.isEmpty()) {
            return cameraInfos
        }

        val logical = cameraInfos.filter { isLogicalMultiCamera(it) }
        if (logical.isNotEmpty()) {
            LogX.d(
                "Logical multi-camera available: %d / %d candidates",
                logical.size,
                cameraInfos.size
            )
            // Among logical cameras, pick the one with the widest zoom span first.
            return logical.sortedByDescending { zoomSpan(it) }
        }

        LogX.d(
            "No REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA; " +
                "using widest zoom-range back camera (%d candidates)",
            cameraInfos.size
        )
        return cameraInfos.sortedByDescending { zoomSpan(it) }
    }

    private fun isLogicalMultiCamera(info: CameraInfo): Boolean {
        return try {
            val camera2 = Camera2CameraInfo.from(info)
            val caps = camera2.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )
            caps?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            ) == true
        } catch (e: Exception) {
            LogX.w(e)
            false
        }
    }

    private fun zoomSpan(info: CameraInfo): Float {
        val zoom = info.zoomState.value ?: return 1f
        val min = zoom.minZoomRatio.coerceAtLeast(0.01f)
        return zoom.maxZoomRatio / min
    }
}
