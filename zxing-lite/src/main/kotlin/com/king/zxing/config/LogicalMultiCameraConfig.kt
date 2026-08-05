package com.king.zxing.config

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import com.king.camera.scan.config.AdaptiveCameraConfig

/**
 * A camera binding target discovered from public CameraX/Camera2 metadata.
 *
 * Camera IDs are opaque values returned by Android. ZXingX never assigns meaning to their
 * spelling or number.
 */
sealed interface CameraBinding {
    /** Keep a particular top-level logical camera selected. */
    data class Logical(val cameraId: String) : CameraBinding

    /** Select an independently exposed top-level camera. */
    data class Exact(val cameraId: String) : CameraBinding

    /** Select a physical child under its owning logical camera. */
    data class Physical(
        val logicalCameraId: String,
        val physicalCameraId: String,
    ) : CameraBinding
}

/**
 * CameraX configuration for the default rear logical camera or a discovered camera binding.
 *
 * For a physical child, the parent logical camera is filtered explicitly and the physical ID is
 * applied to the selector and to every CameraX use case created by CameraScan. Both calls are
 * public CameraX/Camera2 interop APIs; no vendor extension is used.
 */
@OptIn(ExperimentalCamera2Interop::class)
open class LogicalMultiCameraConfig(
    context: Context,
    private val binding: CameraBinding? = null,
) : AdaptiveCameraConfig(context) {

    private var physicalCaptureCallback: ((String) -> Unit)? = null

    fun setPhysicalCaptureCallback(callback: ((String) -> Unit)?) {
        physicalCaptureCallback = callback
    }

    override fun options(builder: CameraSelector.Builder): CameraSelector {
        builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        when (val target = binding) {
            null -> builder.addCameraFilter(::preferLogicalMultiCamera)
            is CameraBinding.Logical -> builder.addCameraFilter { infos ->
                infos.filter { cameraId(it) == target.cameraId }
            }
            is CameraBinding.Exact -> builder.addCameraFilter { infos ->
                infos.filter { cameraId(it) == target.cameraId }
            }
            is CameraBinding.Physical -> {
                builder.addCameraFilter { infos ->
                    infos.filter { cameraId(it) == target.logicalCameraId }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setPhysicalCameraId(target.physicalCameraId)
                }
            }
        }
        return super.options(builder)
    }

    override fun options(builder: Preview.Builder): Preview {
        physicalCameraId()?.let { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Camera2Interop.Extender(builder).setPhysicalCameraId(id)
                physicalCaptureCallback?.let { callback ->
                    attachPhysicalCaptureCallback(builder, callback)
                }
            }
        }
        return super.options(builder)
    }

    override fun options(builder: ImageAnalysis.Builder): ImageAnalysis {
        physicalCameraId()?.let { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Camera2Interop.Extender(builder).setPhysicalCameraId(id)
            }
        }
        return super.options(builder)
    }

    private fun physicalCameraId(): String? = (binding as? CameraBinding.Physical)?.physicalCameraId

    fun attachPhysicalCaptureCallback(
        builder: Preview.Builder,
        onPhysicalResult: (String) -> Unit,
    ) {
        val expected = physicalCameraId() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        Camera2Interop.Extender(builder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (result.physicalCameraTotalResults.containsKey(expected)) {
                            onPhysicalResult(expected)
                        }
                    } else if (result.physicalCameraResults.containsKey(expected)) {
                        onPhysicalResult(expected)
                    }
                }
            },
        )
    }

    private fun preferLogicalMultiCamera(infos: List<CameraInfo>): List<CameraInfo> {
        if (infos.isEmpty()) return infos
        val logical = infos.filter { runCatching { it.isLogicalMultiCameraSupported }.getOrDefault(false) }
        return if (logical.isNotEmpty()) logical else infos
    }

    private fun cameraId(info: CameraInfo): String? = runCatching {
        Camera2CameraInfo.from(info).cameraId
    }.getOrNull()
}

data class LensDescriptor(
    val stableId: String,
    val opticalScore: Float,
    val maxLocalZoom: Float,
    val binding: CameraBinding,
)

data class LensCandidate(
    val stableId: String,
    val opticalScore: Float,
    val opticalRatio: Float,
    val maxLocalZoom: Float,
    val bindings: List<CameraBinding>,
)

/**
 * Pure selection rules for a continuous virtual zoom.
 *
 * The list is sorted by optical ratio. At a requested virtual zoom, the last lens whose declared
 * optical ratio is not greater than that zoom is selected; the remaining factor is digital zoom
 * on that lens. No fixed vendor threshold is involved.
 */
object PhysicalLensStrategy {
    private const val ZOOM_EPSILON = 0.0001f

    fun normalized(
        raw: List<LensDescriptor>,
        baseScore: Float?,
    ): List<LensCandidate> {
        val reference = baseScore?.takeIf { it > 0f && it.isFinite() } ?: return emptyList()
        return raw
            .filter {
                it.stableId.isNotBlank() &&
                    it.opticalScore > 0f &&
                    it.opticalScore.isFinite() &&
                    it.maxLocalZoom.isFinite() &&
                    it.maxLocalZoom >= 1f
            }
            .groupBy { it.stableId }
            .mapNotNull { (stableId, group) ->
                val score = group.map { it.opticalScore }.average().toFloat()
                val ratio = score / reference
                if (!ratio.isFinite() || ratio <= 0f) return@mapNotNull null
                LensCandidate(
                    stableId = stableId,
                    opticalScore = score,
                    opticalRatio = ratio,
                    maxLocalZoom = group.maxOf { it.maxLocalZoom },
                    bindings = group.map { it.binding }.distinct(),
                )
            }
            .sortedBy { it.opticalRatio }
    }

    fun select(
        lenses: List<LensCandidate>,
        virtualZoom: Float,
        currentBinding: CameraBinding?,
    ): LensCandidate? {
        if (lenses.isEmpty()) return null
        val zoom = virtualZoom.coerceAtLeast(lenses.first().opticalRatio)
        val eligible = lenses.filter { it.opticalRatio <= zoom }
        if (eligible.isEmpty()) return lenses.first()

        val covering = eligible.filter {
            virtualZoom <= it.opticalRatio * it.maxLocalZoom + ZOOM_EPSILON
        }
        val selectable = if (covering.isNotEmpty()) covering else eligible
        val highestRatio = selectable.maxOf { it.opticalRatio }
        return selectable.firstOrNull {
            it.opticalRatio == highestRatio &&
                currentBinding != null &&
                it.bindings.contains(currentBinding)
        } ?: selectable.firstOrNull {
            it.opticalRatio == highestRatio &&
                it.bindings.any { binding -> binding is CameraBinding.Logical }
        } ?: selectable.lastOrNull { it.opticalRatio == highestRatio }
            ?: selectable.last()
    }

    fun localZoom(lens: LensCandidate, virtualZoom: Float): Float {
        return (virtualZoom / lens.opticalRatio)
            .coerceIn(1f, lens.maxLocalZoom)
    }
}
