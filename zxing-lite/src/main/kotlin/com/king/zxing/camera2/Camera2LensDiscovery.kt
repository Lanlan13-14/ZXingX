package com.king.zxing.camera2

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.king.logx.LogX
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Camera2 lens discovery based on PhotonCamera's architecture:
 * public IDs + bounded generic ID probing + logical physical bindings.
 * No vendor/model branches.
 */
internal class Camera2LensDiscovery(
    private val cameraManager: CameraManager
) {

    data class Binding(
        val openCameraId: String,
        val physicalCameraId: String? = null
    )

    data class Lens(
        val id: String,
        val ratio: Float,
        val focal35mm: Float,
        val bindings: List<Binding>,
        val characteristics: CameraCharacteristics
    )

    fun discoverBackLenses(): List<Lens> {
        val publicIds = runCatching { cameraManager.cameraIdList.toList() }
            .getOrDefault(emptyList())
        val publicSet = publicIds.toSet()
        val logicalBindings = discoverLogicalBindings(publicIds, publicSet)
        val allIds = (publicIds + logicalBindings.keys + probeHiddenIds(publicSet)).distinct()
        val publicRear = publicIds.mapNotNull { id ->
            characteristics(id)?.takeIf(::isBack)?.let { id to it }
        }
        // PhotonCamera uses a reference main camera (typically camera ID 0) for
        // intrinsic zoom. Prefer ID 0, then a logical rear camera, then the
        // conventional ~4.5 mm phone main focal. Never trust list ordering.
        val reference = publicRear.firstOrNull { it.first == "0" }
            ?: publicRear.firstOrNull { (_, chars) -> isLogical(chars) }
            ?: publicRear.minByOrNull { (_, chars) ->
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.filter { it > 0f }
                    ?.minOrNull()
                    ?: Float.MAX_VALUE
                kotlin.math.abs(focal - 4.5f)
            }
        val default35mm = reference?.second?.let(::focal35mm)?.takeIf { it > 0f }
            ?: publicRear.map { focal35mm(it.second) }.filter { it > 0f }.sorted().let { values ->
                values.getOrNull(values.size / 2) ?: 24f
            }

        val raw = mutableListOf<Lens>()
        allIds.forEach { id ->
            val chars = characteristics(id) ?: return@forEach
            val parent = logicalBindings[id]
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
                ?: parent?.let { characteristics(it)?.get(CameraCharacteristics.LENS_FACING) }
            if (facing != CameraCharacteristics.LENS_FACING_BACK) return@forEach
            if (!supportsPreviewAndAnalysis(chars)) return@forEach
            val focal = focal35mm(chars).takeIf { it > 0f } ?: return@forEach
            val bindings = buildList {
                parent?.let { add(Binding(it, id)) }
                if (id in publicSet || canOpenDirectly(id)) add(Binding(id, null))
            }.distinct()
            if (bindings.isEmpty()) return@forEach
            raw += Lens(id, focal / default35mm, focal, bindings, chars)
        }

        return mergeEquivalent(raw)
    }

    private fun discoverLogicalBindings(
        publicIds: List<String>,
        publicSet: Set<String>
    ): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyMap()
        val candidates = mutableMapOf<String, Pair<String, Int>>()
        publicIds.forEach { logicalId ->
            val chars = characteristics(logicalId) ?: return@forEach
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: intArrayOf()
            if (!capabilities.contains(
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                )
            ) return@forEach
            val physicalIds = chars.physicalCameraIds
            physicalIds.forEach { physicalId ->
                // Direct public IDs are still kept; physical output becomes first fallback.
                val score = physicalIds.size + if (physicalId !in publicSet) 100 else 0
                val current = candidates[physicalId]
                if (current == null || score > current.second) {
                    candidates[physicalId] = logicalId to score
                }
            }
        }
        return candidates.mapValues { it.value.first }
    }

    private fun probeHiddenIds(publicIds: Set<String>): List<String> {
        // Vendor-neutral bounded ranges seen across Android camera providers.
        // This is intentionally not 0..999 brute force: excessive probing can stall HALs.
        val ranges = listOf(0..9, 20..29, 40..49, 80..89, 100..119)
        val found = mutableListOf<String>()
        for (range in ranges) {
            var sawCandidate = false
            var consecutiveMisses = 0
            for (number in range) {
                val id = number.toString()
                if (id in publicIds) {
                    sawCandidate = true
                    consecutiveMisses = 0
                    continue
                }
                val chars = characteristics(id)
                val usable = chars != null && isBack(chars) &&
                    (chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        ?: intArrayOf()).contains(
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                    ) && supportsPreviewAndAnalysis(chars) && canOpenDirectly(id)
                if (usable) {
                    found += id
                    sawCandidate = true
                    consecutiveMisses = 0
                } else {
                    consecutiveMisses++
                    if (sawCandidate && consecutiveMisses >= 6) break
                }
            }
        }
        return found
    }

    private fun supportsPreviewAndAnalysis(chars: CameraCharacteristics): Boolean {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val preview = map.getOutputSizes(SurfaceTexture::class.java)
        val analysis = map.getOutputSizes(ImageFormat.YUV_420_888)
        return !preview.isNullOrEmpty() && !analysis.isNullOrEmpty()
    }

    private fun canOpenDirectly(cameraId: String): Boolean {
        // Characteristics access is the only safe pre-open capability query. Actual open
        // failures are handled by Camera2ScanController and fall through to next binding.
        return characteristics(cameraId) != null
    }

    private fun isBack(chars: CameraCharacteristics): Boolean =
        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK

    private fun isLogical(chars: CameraCharacteristics): Boolean {
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        return capabilities.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
        )
    }

    private fun characteristics(id: String): CameraCharacteristics? = runCatching {
        cameraManager.getCameraCharacteristics(id)
    }.onFailure {
        LogX.v("Camera2 probe %s unavailable: %s", id, it.message)
    }.getOrNull()

    private fun focal35mm(chars: CameraCharacteristics): Float {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull { it > 0f } ?: return 0f
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0f
        val diagonal = sqrt(
            (sensor.width * sensor.width + sensor.height * sensor.height).toDouble()
        ).toFloat()
        return if (diagonal > 0f) focal * 43.2666f / diagonal else 0f
    }

    private fun mergeEquivalent(raw: List<Lens>): List<Lens> {
        val sorted = raw.sortedBy { it.ratio }
        val merged = mutableListOf<Lens>()
        sorted.forEach { lens ->
            val index = merged.indexOfFirst { abs(it.ratio - lens.ratio) <= 0.04f }
            if (index < 0) {
                merged += lens
            } else {
                val old = merged[index]
                merged[index] = old.copy(bindings = (old.bindings + lens.bindings).distinct())
            }
        }
        return merged
    }
}
