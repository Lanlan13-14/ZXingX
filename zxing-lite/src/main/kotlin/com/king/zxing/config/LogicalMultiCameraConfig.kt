package com.king.zxing.config

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AdaptiveCameraConfig

/**
 * Standard CameraX configuration for continuous zoom on the chosen lens facing.
 *
 * CameraX chooses the device's logical multi-camera when it exposes one. Pinch zoom is
 * handled by CameraScan and calls CameraControl.setZoomRatio(); the device camera HAL decides
 * whether and when the underlying physical lens changes. No physical camera IDs are guessed or
 * rebound by this library.
 *
 * @param lensFacing [CameraSelector.LENS_FACING_BACK] (default) or [CameraSelector.LENS_FACING_FRONT]
 * @param preferLogicalMultiCamera when true, rear/front logical multi-cameras are preferred
 * when the device exposes them; set false for the minimal fallback config used when binding
 * the full config fails (e.g. on some pads/tablets).
 */
open class LogicalMultiCameraConfig(
    context: Context,
    @CameraSelector.LensFacing private val lensFacing: Int,
    private val preferLogicalMultiCamera: Boolean
) : AdaptiveCameraConfig(context) {

    constructor(context: Context) : this(context, CameraSelector.LENS_FACING_BACK, true)

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(lensFacing)
        if (preferLogicalMultiCamera) {
            builder.addCameraFilter(::preferLogicalMultiCamera)
        }
        return super.options(builder)
    }

    private fun preferLogicalMultiCamera(infos: List<CameraInfo>): List<CameraInfo> {
        if (infos.isEmpty()) return infos
        val logical = infos.filter { it.isLogicalMultiCameraSupported }
        return if (logical.isNotEmpty()) logical else infos
    }
}
