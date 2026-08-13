package com.king.zxing

import com.king.zxing.config.CameraSwitchTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻面第二半程的揭示策略：必须等第一半程结束，并且要么新预览已经出帧，
 * 要么超过超时。纯逻辑，无 Android 运行时。
 */
class CameraSwitchTimingTest {

    @Test
    fun `does not reveal before the first half finishes even if preview is ready`() {
        assertFalse(CameraSwitchTiming.shouldReveal(false, true, 2_000L))
        assertFalse(CameraSwitchTiming.shouldReveal(false, true, CameraSwitchTiming.REVEAL_TIMEOUT_MS))
    }

    @Test
    fun `reveals when first half is done and preview is ready`() {
        assertTrue(CameraSwitchTiming.shouldReveal(true, true, CameraSwitchTiming.FLIP_HALF_DURATION_MS))
    }

    @Test
    fun `reveals on timeout after first half even without a frame`() {
        assertTrue(
            CameraSwitchTiming.shouldReveal(
                true,
                false,
                CameraSwitchTiming.REVEAL_TIMEOUT_MS
            )
        )
    }

    @Test
    fun `does not reveal just before timeout without a frame`() {
        assertFalse(
            CameraSwitchTiming.shouldReveal(
                true,
                false,
                CameraSwitchTiming.REVEAL_TIMEOUT_MS - 1
            )
        )
    }

    @Test
    fun `timeout is longer than one flip half so the hold is visible`() {
        assertTrue(CameraSwitchTiming.REVEAL_TIMEOUT_MS > CameraSwitchTiming.FLIP_HALF_DURATION_MS)
    }

    @Test
    fun `new preview is not ready until camera is bound`() {
        assertFalse(CameraSwitchTiming.isNewPreviewReady(false, true, true))
    }

    @Test
    fun `new preview is not ready on leftover STREAMING without a gap`() {
        assertFalse(CameraSwitchTiming.isNewPreviewReady(true, false, true))
    }

    @Test
    fun `new preview is not ready during the IDLE gap`() {
        assertFalse(CameraSwitchTiming.isNewPreviewReady(true, true, false))
    }

    @Test
    fun `new preview is ready after a gap then STREAMING with a bound camera`() {
        assertTrue(CameraSwitchTiming.isNewPreviewReady(true, true, true))
    }
}
