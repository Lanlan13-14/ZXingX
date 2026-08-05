package com.king.zxing.config

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AdaptiveCameraConfig

/**
 * Standard CameraX configuration for continuous rear-camera zoom.
 *
 * CameraX chooses the device's logical rear multi-camera when it exposes one. Pinch zoom is
 * handled by CameraScan and calls CameraControl.setZoomRatio(); the device camera HAL decides
 * whether and when the underlying physical lens changes. No physical camera IDs are guessed or
 * rebound by this library.
 */
open class LogicalMultiCameraConfig(context: Context) : AdaptiveCameraConfig(context) {

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        builder.addCameraFilter(::preferLogicalMultiCamera)
        return super.options(builder)
    }

    private fun preferLogicalMultiCamera(infos: List<CameraInfo>): List<CameraInfo> {
        if (infos.isEmpty()) return infos
        val logical = infos.filter { it.isLogicalMultiCameraSupported }
        return if (logical.isNotEmpty()) logical else infos
    }
}
