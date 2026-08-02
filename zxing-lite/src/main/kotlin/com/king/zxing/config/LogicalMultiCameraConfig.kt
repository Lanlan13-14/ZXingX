package com.king.zxing.config

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AdaptiveCameraConfig
import com.king.logx.LogX

/**
 * Select the OEM logical rear camera, optionally binding one of its physical lenses.
 *
 * [physicalCameraId] is passed to CameraX's standard
 * [CameraSelector.Builder.setPhysicalCameraId] API. `null` keeps the logical camera.
 */
open class LogicalMultiCameraConfig(
    context: Context,
    private val physicalCameraId: String? = null
) : AdaptiveCameraConfig(context) {

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        builder.addCameraFilter(::preferLogicalMultiCamera)
        physicalCameraId?.let(builder::setPhysicalCameraId)
        return super.options(builder)
    }

    private fun preferLogicalMultiCamera(cameraInfos: List<CameraInfo>): List<CameraInfo> {
        if (cameraInfos.isEmpty()) return cameraInfos

        val logical = cameraInfos.filter { it.isLogicalMultiCameraSupported }
        if (logical.isNotEmpty()) {
            LogX.d(
                "Logical multi-camera selected: %d / %d candidates; physical=%s",
                logical.size,
                cameraInfos.size,
                physicalCameraId ?: "logical"
            )
            return logical
        }

        LogX.d(
            "Logical multi-camera unavailable; using rear camera fallback; physical=%s",
            physicalCameraId ?: "logical"
        )
        return cameraInfos
    }
}

data class PhysicalLens(
    val cameraId: String,
    /** Focal length / sensor width. Larger means a narrower field of view. */
    val opticalScore: Float,
    /** Normalized to the main lens: ultra-wide < 1, main = 1, tele > 1. */
    val opticalRatio: Float
)

/** Pure selection math used by the gesture controller. */
object PhysicalLensStrategy {

    fun normalized(
        lenses: List<Pair<String, Float>>,
        logicalMainScore: Float?
    ): List<PhysicalLens> {
        val valid = lenses
            .filter { it.first.isNotBlank() && it.second > 0f && it.second.isFinite() }
            .distinctBy { it.first }
            .sortedBy { it.second }
        if (valid.isEmpty()) return emptyList()

        val mainScore = logicalMainScore
            ?.takeIf { it > 0f && it.isFinite() }
            ?.let { score -> valid.minByOrNull { kotlin.math.abs(kotlin.math.ln(it.second / score)) }?.second }
            ?: valid[valid.size / 2].second

        return valid.map { (id, score) ->
            PhysicalLens(id, score, score / mainScore)
        }
    }

    fun select(lenses: List<PhysicalLens>, virtualZoom: Float): PhysicalLens? {
        if (lenses.isEmpty()) return null
        val zoom = virtualZoom.coerceAtLeast(lenses.first().opticalRatio)
        return lenses.lastOrNull { it.opticalRatio <= zoom + 0.015f } ?: lenses.first()
    }

    fun localZoom(lens: PhysicalLens, virtualZoom: Float): Float {
        return (virtualZoom / lens.opticalRatio).coerceAtLeast(1f)
    }
}
                                                                             