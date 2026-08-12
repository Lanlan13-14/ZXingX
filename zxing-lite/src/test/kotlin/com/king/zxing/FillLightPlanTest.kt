package com.king.zxing

import androidx.camera.core.CameraSelector
import com.king.zxing.config.FillLightPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FillLightPlan 补光决策的纯 JVM 测试。
 * CameraSelector.LENS_FACING_* 为编译期内联常量，不需要 Android 运行时。
 */
class FillLightPlanTest {

    @Test
    fun `camera with flash unit uses torch`() {
        assertFalse(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_BACK, true))
        assertFalse(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_FRONT, true))
    }

    @Test
    fun `camera without flash unit uses screen flash`() {
        assertTrue(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_FRONT, false))
        assertTrue(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_BACK, false))
    }

    @Test
    fun `unknown flash falls back to lens facing heuristic`() {
        assertTrue(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_FRONT, null))
        assertFalse(FillLightPlan.useScreenFlash(CameraSelector.LENS_FACING_BACK, null))
    }
}
