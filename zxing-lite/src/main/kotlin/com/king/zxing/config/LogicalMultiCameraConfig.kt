package com.king.zxing.config

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AdaptiveCameraConfig
import com.king.logx.LogX
import kotlin.math.abs
import kotlin.math.ln

/** A CameraX-standard camera binding target. No vendor or model-specific IDs. */
sealed interface CameraBinding {
    /** Bind an independently exposed CameraX camera ID. */
    data class Exact(val cameraId: String) : CameraBinding

    /** Bind a physical child under its owning logical camera ID. */
    data class Physical(val logicalCameraId: String, val physicalCameraId: String) : CameraBinding
}

/**
 * Back-camera config that can bind a logical camera, an exact camera ID, or one of
 * a logical camera's physical children using CameraX 1.5 standard APIs.
 */
@OptIn(ExperimentalCamera2Interop::class)
open class LogicalMultiCameraConfig(
    context: Context,
    private val binding: CameraBinding? = null
) : AdaptiveCameraConfig(context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        when (val target = binding) {
            null -> builder.addCameraFilter(::preferLogicalMultiCamera)
            is CameraBinding.Exact -> builder.addCameraFilter { infos ->
                infos.filter { cameraId(it) == target.cameraId }
            }
            is CameraBinding.Physical -> {
                builder.addCameraFilter { infos ->
                    infos.filter { cameraId(it) == target.logicalCameraId }
                }
                builder.setPhysicalCameraId(target.physicalCameraId)
            }
        }
        return super.options(builder)
    }

    private fun preferLogicalMultiCamera(infos: List<CameraInfo>): List<CameraInfo> {
        if (infos.isEmpty()) return infos
        val logical = infos.filter { it.isLogicalMultiCameraSupported }
        val best = if (logical.isNotEmpty()) {
            logical.maxByOrNull(::logicalCoverageScore)
        } else {
            infos.maxByOrNull(::zoomRangeScore)
        }
        return best?.let(::listOf) ?: infos
    }

    /** Rank by physical-child count first, then optical and zoom span. */
    private fun logicalCoverageScore(info: CameraInfo): Float {
        val id = cameraId(info) ?: return zoomRangeScore(info)
        val chars = runCatching { cameraManager?.getCameraCharacteristics(id) }.getOrNull()
            ?: return zoomRangeScore(info)
        val ids = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            chars.physicalCameraIds
        } else {
            emptySet()
        }
        val optical = ids.mapNotNull { physicalId ->
            runCatching { cameraManager?.getCameraCharacteristics(physicalId) }
                .getOrNull()
                ?.let(::equivalentFocalLength)
        }
        val opticalSpan = if (optical.size >= 2) {
            optical.maxOrNull()!! / optical.minOrNull()!!.coerceAtLeast(0.01f)
        } else {
            1f
        }
        return ids.size * 1000f + opticalSpan * 100f + zoomRangeScore(info)
    }

    private fun zoomRangeScore(info: CameraInfo): Float {
        val zoom = info.zoomState.value ?: return 1f
        return zoom.maxZoomRatio / zoom.minZoomRatio.coerceAtLeast(0.01f)
    }

    private fun equivalentFocalLength(chars: CameraCharacteristics): Float? {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.filter { it > 0f }
            ?.minOrNull()
            ?: return null
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val diagonal = kotlin.math.sqrt(
            (sensor.width * sensor.width + sensor.height * sensor.height).toDouble()
        ).toFloat()
        return if (diagonal > 0f) focal * 43.2666f / diagonal else null
    }

    private fun cameraId(info: CameraInfo): String? = runCatching {
        Camera2CameraInfo.from(info).cameraId
    }.onFailure { LogX.w(it) }.getOrNull()
}

data class LensCandidate(
    val stableId: String,
    val opticalScore: Float,
    val opticalRatio: Float,
    /** Ordered attempts. Physical child first, exact independent camera second. */
    val bindings: List<CameraBinding>
)

/** Pure cross-vendor focal-range selection math. */
object PhysicalLensStrategy {

    fun normalized(
        raw: List<Triple<String, Float, List<CameraBinding>>>,
        logicalMainScore: Float?
    ): List<LensCandidate> {
        val valid = raw
            .filter { it.first.isNotBlank() && it.second > 0f && it.second.isFinite() && it.third.isNotEmpty() }
            .groupBy { it.first }
            .map { (id, group) ->
                val score = group.map { it.second }.average().toFloat()
                val bindings = group.flatMap { it.third }.distinct()
                Triple(id, score, bindings)
            }
            .sortedBy { it.second }
        if (valid.isEmpty()) return emptyList()

        val mainScore = logicalMainScore
            ?.takeIf { it > 0f && it.isFinite() }
            ?.let { score -> valid.minByOrNull { abs(ln(it.second / score)) }?.second }
            ?: valid[valid.size / 2].second

        return valid.map { (id, score, bindings) ->
            LensCandidate(id, score, score / mainScore, bindings)
        }
    }

    fun select(lenses: List<LensCandidate>, virtualZoom: Float): LensCandidate? {
        if (lenses.isEmpty()) return null
        val zoom = virtualZoom.coerceAtLeast(lenses.first().opticalRatio)
        return lenses.lastOrNull { it.opticalRatio <= zoom + 0.015f } ?: lenses.first()
    }

    fun localZoom(lens: LensCandidate, virtualZoom: Float): Float {
        return (virtualZoom / lens.opticalRatio).coerceAtLeast(1f)
    }
}
