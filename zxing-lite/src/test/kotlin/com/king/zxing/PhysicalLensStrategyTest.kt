package com.king.zxing

import com.king.zxing.config.CameraBinding
import com.king.zxing.config.LensDescriptor
import com.king.zxing.config.PhysicalLensStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalLensStrategyTest {

    private val logical = CameraBinding.Logical("logical-main")
    private val ultraWide = CameraBinding.Physical("logical-main", "physical-wide")
    private val telephoto = CameraBinding.Physical("logical-main", "physical-tele")

    private val lenses = PhysicalLensStrategy.normalized(
        raw = listOf(
            LensDescriptor("wide", 1f, 8f, logical),
            LensDescriptor("ultra-wide", 0.5f, 4f, ultraWide),
            LensDescriptor("telephoto", 3f, 2f, telephoto),
        ),
        baseScore = 1f,
    )

    @Test
    fun selectsPhysicalLensAtDeclaredOpticalRange() {
        assertEquals("ultra-wide", PhysicalLensStrategy.select(lenses, 0.6f, logical)?.stableId)
        assertEquals("wide", PhysicalLensStrategy.select(lenses, 1f, logical)?.stableId)
        assertEquals("telephoto", PhysicalLensStrategy.select(lenses, 3f, logical)?.stableId)
    }

    @Test
    fun keepsParentLogicalBindingOnPhysicalCandidate() {
        val tele = lenses.first { it.stableId == "telephoto" }
        assertTrue(tele.bindings.contains(CameraBinding.Physical("logical-main", "physical-tele")))
        assertEquals(2f, PhysicalLensStrategy.localZoom(tele, 6f), 0.0001f)
    }

    @Test
    fun unknownMetadataProducesNoExplicitCandidates() {
        assertTrue(
            PhysicalLensStrategy.normalized(
                raw = emptyList(),
                baseScore = null,
            ).isEmpty(),
        )
    }
}
