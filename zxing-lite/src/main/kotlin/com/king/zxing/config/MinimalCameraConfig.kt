package com.king.zxing.config

import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.CameraConfig

/**
 * Minimal camera configuration: only constrains the lens facing and leaves preview /
 * analysis resolution and aspect ratio entirely to CameraX defaults.
 *
 * Used as a fallback when the adaptive-resolution config fails to bind (seen on some
 * pads/tablets whose cameras do not satisfy the adaptive resolution filter chain).
 */
open class MinimalCameraConfig(
    @CameraSelector.LensFacing private val lensFacing: Int
) : CameraConfig() {

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(lensFacing)
        return super.options(builder)
    }
}
