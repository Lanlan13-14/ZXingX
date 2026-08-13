package com.king.zxing.config

import androidx.camera.core.CameraSelector

/**
 * Pure decision logic for camera facing selection and bind-failure fallback.
 *
 * Background: camera-scan's BaseCameraScan.startCamera() swallows every bind exception
 * (it only logs), so a device whose camera fails to bind (seen on some tablets/pads)
 * just shows a black preview forever. BarcodeCameraScanActivity runs a watchdog: if the
 * camera is still null a few seconds after startCamera(), it escalates one attempt.
 *
 * The chain is facing-aware. AdaptiveCameraConfig (full) asks CameraX for a 720p–1080p
 * preview plus a filtered analysis resolution; that combination is what the rear
 * logical multi-camera is built for, but front sensors on phones and pads often cannot
 * satisfy it. Binding then fails silently and the 3s watchdog is what the user feels
 * as "the flip finished long ago, the camera is still dead."
 *
 *   preferred = BACK (default cold start):
 *     0: back,  full   (adaptive + logical multi-camera)
 *     1: back,  min    (plain selector, CameraX default resolutions)
 *     2: front, min    (never spend another Adaptive attempt on the opposite lens)
 *
 *   preferred = FRONT (user flipped, or a front-only pad):
 *     0: front, min    (skip Adaptive entirely — this is the slow path we are killing)
 *     1: back,  full
 *     2: back,  min
 *
 * After the last attempt fails, the caller shows an error instead of a silent black screen.
 *
 * This class contains no Android framework state so the whole matrix is JVM-unit-testable.
 */
object CameraFacingPlan {

    data class Step(
        @CameraSelector.LensFacing val lensFacing: Int,
        val useFullConfig: Boolean
    )

    /** Longest chain length; both facings currently use 3 steps. */
    const val MAX_ATTEMPTS = 3

    fun opposite(@CameraSelector.LensFacing lensFacing: Int): Int =
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

    /**
     * Bind-failure fallback chain for the user's [preferred] facing.
     * Front never includes a full/Adaptive step on the front camera itself.
     */
    fun steps(@CameraSelector.LensFacing preferred: Int): List<Step> {
        val other = opposite(preferred)
        return if (preferred == CameraSelector.LENS_FACING_FRONT) {
            listOf(
                Step(preferred, useFullConfig = false),
                Step(other, useFullConfig = true),
                Step(other, useFullConfig = false)
            )
        } else {
            listOf(
                Step(preferred, useFullConfig = true),
                Step(preferred, useFullConfig = false),
                Step(other, useFullConfig = false)
            )
        }
    }

    fun attemptCount(@CameraSelector.LensFacing preferred: Int): Int = steps(preferred).size

    fun step(@CameraSelector.LensFacing preferred: Int, attempt: Int): Step {
        val chain = steps(preferred)
        require(attempt in chain.indices) { "attempt out of range: $attempt" }
        return chain[attempt]
    }

    /**
     * Lens facing to use for [attempt], given the user's [preferred] facing.
     */
    fun lensFacingForAttempt(@CameraSelector.LensFacing preferred: Int, attempt: Int): Int =
        step(preferred, attempt).lensFacing

    /**
     * Whether [attempt] uses the full-featured config (adaptive resolution, logical
     * multi-camera preference). Front-as-preferred never returns true for the front
     * camera itself — see [steps].
     */
    fun useFullConfigForAttempt(@CameraSelector.LensFacing preferred: Int, attempt: Int): Boolean =
        step(preferred, attempt).useFullConfig
}
