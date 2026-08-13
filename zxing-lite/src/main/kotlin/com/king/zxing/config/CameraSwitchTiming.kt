package com.king.zxing.config

/**
 * Timing for the front/back card-flip. Pure logic so the reveal policy is
 * JVM-testable: the second half of the flip must not start until the new
 * preview is actually producing frames (or a timeout fires). Starting the
 * second half on a timer is what made the animation finish long before the
 * front camera came up.
 */
object CameraSwitchTiming {

    /** One half of the rotateY flip (0→90 or −90→0). */
    const val FLIP_HALF_DURATION_MS = 300L

    /**
     * Worst case we will hold the preview edge-on waiting for the first new
     * frame. Long enough for a cold front HAL on a mid-range phone, short
     * enough that a wedged bind does not look like a freeze.
     */
    const val REVEAL_TIMEOUT_MS = 1200L

    /** How often the activity re-checks reveal conditions while flipping. */
    const val POLL_MS = 50L

    /**
     * Reference card perspective: 1200px perspective / 320px card width.
     * Applied as PreviewView.cameraDistance = width * this ratio.
     */
    const val FLIP_PERSPECTIVE_RATIO = 3.75f

    const val FLIP_INTERPOLATOR_FACTOR = 0.6f

    /**
     * @param firstHalfDone the 0→90° half has finished (preview is edge-on)
     * @param previewReady the new camera has produced at least one frame
     * @param elapsedMs milliseconds since the user tapped switch
     */
    fun shouldReveal(firstHalfDone: Boolean, previewReady: Boolean, elapsedMs: Long): Boolean {
        if (!firstHalfDone) return false
        return previewReady || elapsedMs >= REVEAL_TIMEOUT_MS
    }

    /**
     * Whether the preview now belongs to the camera we just bound.
     *
     * [cameraBound] CameraX `camera != null` after bindToLifecycle.
     * [sawStreamGap] we have observed a non-STREAMING state since the flip
     *     started (unbindAll typically produces IDLE). Without this, a leftover
     *     STREAMING from the previous camera would look like the new one.
     * [streaming] PreviewView is currently STREAMING.
     */
    fun isNewPreviewReady(cameraBound: Boolean, sawStreamGap: Boolean, streaming: Boolean): Boolean {
        return cameraBound && sawStreamGap && streaming
    }
}
