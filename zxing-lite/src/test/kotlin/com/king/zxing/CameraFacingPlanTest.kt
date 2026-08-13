package com.king.zxing

import androidx.camera.core.CameraSelector
import com.king.zxing.config.CameraFacingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CameraFacingPlan 降级链决策逻辑的纯 JVM 测试。
 * CameraSelector.LENS_FACING_* 是编译期常量，会被内联，本测试不需要 Android 运行时。
 */
class CameraFacingPlanTest {

    @Test
    fun `opposite maps back to front and front to back`() {
        assertEquals(CameraSelector.LENS_FACING_FRONT, CameraFacingPlan.opposite(CameraSelector.LENS_FACING_BACK))
        assertEquals(CameraSelector.LENS_FACING_BACK, CameraFacingPlan.opposite(CameraSelector.LENS_FACING_FRONT))
    }

    @Test
    fun `back preferred chain tries full then minimal then opposite minimal`() {
        val chain = (0 until CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_BACK)).map { attempt ->
            CameraFacingPlan.step(CameraSelector.LENS_FACING_BACK, attempt)
        }
        assertEquals(
            listOf(
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_BACK, true),
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_BACK, false),
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_FRONT, false)
            ),
            chain
        )
    }

    @Test
    fun `front preferred chain skips adaptive config on the front camera`() {
        val chain = (0 until CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_FRONT)).map { attempt ->
            CameraFacingPlan.step(CameraSelector.LENS_FACING_FRONT, attempt)
        }
        assertEquals(
            listOf(
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_FRONT, false),
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_BACK, true),
                CameraFacingPlan.Step(CameraSelector.LENS_FACING_BACK, false)
            ),
            chain
        )
    }

    @Test
    fun `front never uses full config on the front camera itself`() {
        val steps = (0 until CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_FRONT))
            .map { CameraFacingPlan.step(CameraSelector.LENS_FACING_FRONT, it) }
        assertTrue(steps.none { it.lensFacing == CameraSelector.LENS_FACING_FRONT && it.useFullConfig })
    }

    @Test
    fun `legacy helpers match step for both facings`() {
        for (preferred in intArrayOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT)) {
            for (attempt in 0 until CameraFacingPlan.attemptCount(preferred)) {
                val step = CameraFacingPlan.step(preferred, attempt)
                assertEquals(step.lensFacing, CameraFacingPlan.lensFacingForAttempt(preferred, attempt))
                assertEquals(step.useFullConfig, CameraFacingPlan.useFullConfigForAttempt(preferred, attempt))
            }
        }
    }

    @Test
    fun `attempt count is three for both facings`() {
        assertEquals(3, CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_BACK))
        assertEquals(3, CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_FRONT))
        assertEquals(3, CameraFacingPlan.MAX_ATTEMPTS)
    }

    @Test
    fun `first back attempt still uses the full config`() {
        assertTrue(CameraFacingPlan.useFullConfigForAttempt(CameraSelector.LENS_FACING_BACK, 0))
        assertFalse(CameraFacingPlan.useFullConfigForAttempt(CameraSelector.LENS_FACING_FRONT, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempt is rejected`() {
        CameraFacingPlan.step(CameraSelector.LENS_FACING_BACK, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt beyond the chain is rejected`() {
        CameraFacingPlan.step(
            CameraSelector.LENS_FACING_BACK,
            CameraFacingPlan.attemptCount(CameraSelector.LENS_FACING_BACK)
        )
    }
}
