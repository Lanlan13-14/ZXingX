package com.king.zxing.config

import androidx.camera.core.CameraSelector

/**
 * Pure decision logic for camera facing selection and bind-failure fallback.
 *
 * Background: camera-scan's BaseCameraScan.startCamera() swallows every bind exception
 * (it only logs), so a device whose camera fails to bind (seen on some tablets/pads)
 * just shows a black preview forever. BarcodeCameraScanActivity runs a watchdog: if the
 * camera is still null a few seconds after startCamera(), it escalates one attempt:
 *
 *   attempt 0: preferred facing, full config (adaptive resolution + logical multi-camera preference)
 *   attempt 1: preferred facing, minimal config (plain selector, default resolutions)
 *   attempt 2: opposite facing, full config
 *   attempt 3: opposite facing, minimal config
 *
 * after the last attempt fails, the caller shows an error instead of a silent black screen.
 *
 * This class contains no Android framework state so the whole matrix is JVM-unit-testable.
 */
object CameraFacingPlan {

    const val MAX_ATTEMPTS = 4

    fun opposite(@CameraSelector.LensFacing lensFacing: Int): Int =
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

    /**
     * Lens facing to use for [attempt], given the user's [preferred] facing.
     * Attempts 0-1 try the preferred facing, attempts 2-3 fall back to the opposite one.
     */
    fun lensFacingForAttempt(@CameraSelector.LensFacing preferred: Int, attempt: Int): Int {
        require(attempt in 0 until MAX_ATTEMPTS) { "attempt out of range: $attempt" }
        return if (attempt < 2) preferred else opposite(preferred)
    }

    /**
     * Whether [attempt] uses the full-featured config (adaptive resolution, logical
     * multi-camera preference). Even attempts are full, odd attempts are minimal —
     * a minimal config removes resolution/surface-combination constraints, which is
     * one of the ways camera binding fails on large-screen devices such as pads.
     */
    fun useFullConfigForAttempt(attempt: Int): Boolean {
        require(attempt in 0 until MAX_ATTEMPTS) { "attempt out of range: $attempt" }
        return attempt % 2 == 0
    }
}
