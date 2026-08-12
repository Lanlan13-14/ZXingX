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
    fun `attempts zero and one keep the preferred facing`() {
        for (attempt in 0..1) {
            assertEquals(
                CameraSelector.LENS_FACING_BACK,
                CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_BACK, attempt)
            )
            assertEquals(
                CameraSelector.LENS_FACING_FRONT,
                CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_FRONT, attempt)
            )
        }
    }

    @Test
    fun `attempts two and three fall back to the opposite facing`() {
        for (attempt in 2..3) {
            assertEquals(
                CameraSelector.LENS_FACING_FRONT,
                CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_BACK, attempt)
            )
            assertEquals(
                CameraSelector.LENS_FACING_BACK,
                CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_FRONT, attempt)
            )
        }
    }

    @Test
    fun `full and minimal configs alternate across attempts`() {
        assertTrue(CameraFacingPlan.useFullConfigForAttempt(0))
        assertFalse(CameraFacingPlan.useFullConfigForAttempt(1))
        assertTrue(CameraFacingPlan.useFullConfigForAttempt(2))
        assertFalse(CameraFacingPlan.useFullConfigForAttempt(3))
    }

    @Test
    fun `attempts cover the whole chain exactly once`() {
        val chain = (0 until CameraFacingPlan.MAX_ATTEMPTS).map { attempt ->
            CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_BACK, attempt) to
                CameraFacingPlan.useFullConfigForAttempt(attempt)
        }
        assertEquals(
            listOf(
                CameraSelector.LENS_FACING_BACK to true,
                CameraSelector.LENS_FACING_BACK to false,
                CameraSelector.LENS_FACING_FRONT to true,
                CameraSelector.LENS_FACING_FRONT to false
            ),
            chain
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempt is rejected`() {
        CameraFacingPlan.lensFacingForAttempt(CameraSelector.LENS_FACING_BACK, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `attempt beyond the chain is rejected`() {
        CameraFacingPlan.useFullConfigForAttempt(CameraFacingPlan.MAX_ATTEMPTS)
    }
}
