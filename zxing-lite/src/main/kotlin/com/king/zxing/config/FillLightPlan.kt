package com.king.zxing.config

import androidx.camera.core.CameraSelector

/**
 * Pure decision logic for the flashlight button.
 *
 * Back cameras usually have a flash unit → hardware torch. Front cameras never
 * do → the button toggles "screen flash" instead: two white bars (top/bottom of
 * the scan screen) at full brightness act as fill light, the same trick WeChat
 * and the iOS Camera app use for front-facing illumination.
 *
 * [hasFlashUnit] is null while the camera is still binding; in that case the
 * lens facing decides (front cameras have no torch by hardware definition).
 */
object FillLightPlan {

    fun useScreenFlash(
        @CameraSelector.LensFacing lensFacing: Int,
        hasFlashUnit: Boolean?
    ): Boolean {
        val hasFlash = hasFlashUnit ?: (lensFacing != CameraSelector.LENS_FACING_FRONT)
        return !hasFlash
    }
}
