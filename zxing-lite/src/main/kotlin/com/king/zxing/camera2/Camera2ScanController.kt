package com.king.zxing.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import android.widget.ImageView
import com.google.zxing.Result
import com.king.logx.LogX
import com.king.zxing.analyze.ImageAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Camera2-direct preview + ImageReader scanner. */
internal class Camera2ScanController(
    private val context: Context,
    private val textureView: TextureView,
    private val analyzer: ImageAnalyzer,
    private val onResult: (Result, Int, Int) -> Unit
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("ZXingX-Camera2").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val decodeThread = HandlerThread("ZXingX-Decode").apply { start() }
    private val decodeHandler = Handler(decodeThread.looper)
    private val sessionExecutor = Executors.newSingleThreadExecutor()
    private val decoding = AtomicBoolean(false)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var lenses: List<Camera2LensDiscovery.Lens> = emptyList()
    private var lensIndex = -1
    private var bindingIndex = 0
    private var virtualZoom = 1f
    private var currentBinding: Camera2LensDiscovery.Binding? = null
    private var attemptLensIndex = -1
    private var attemptBindingIndex = -1
    private var lastWorkingLensIndex = -1
    private var lastWorkingBindingIndex = -1
    private val failedBindingKeys = mutableSetOf<String>()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var requestBuilder: CaptureRequest.Builder? = null
    private var torch = false
    private var analyze = true
    private var released = false
    private var started = false
    private var discovering = false
    private var generation = 0
    private var frozenPreview: ImageView? = null
    private var previewSize = Size(1280, 720)
    private var sensorOrientation = 90
    private var waitingForFirstFrame = false
    private var firstFrameCount = 0
    private var lastAnalyzeNs = 0L

    fun start() {
        if (released || started) return
        started = true
        textureView.surfaceTextureListener = surfaceListener
        textureView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
        if (lenses.isNotEmpty()) {
            if (textureView.isAvailable) openSelectedLens()
            return
        }
        if (discovering) return
        discovering = true
        // Camera ID probing can touch dozens of IDs. Never perform it on the UI thread.
        cameraHandler.post {
            val discovered = Camera2LensDiscovery(manager).discoverBackLenses()
            textureView.post {
                discovering = false
                if (released || !started) return@post
                lenses = discovered
                LogX.i(
                    "Camera2 lenses: %s",
                    lenses.joinToString { lens ->
                        "${lens.stableId}@${"%.2f".format(java.util.Locale.US, lens.ratio)}x=" +
                            lens.bindings.joinToString(prefix = "[", postfix = "]") { binding ->
                                "open:${binding.openCameraId}/physical:${binding.physicalCameraId ?: "-"}"
                            }
                    }
                )
                lensIndex = lenses.indices.minByOrNull { abs(lenses[it].ratio - 1f) } ?: -1
                if (lensIndex < 0) {
                    LogX.e("Camera2: no usable back lens")
                } else if (textureView.isAvailable) {
                    openSelectedLens()
                }
            }
        }
    }

    fun setAnalyzeImage(enabled: Boolean) { analyze = enabled }
    fun isTorchEnabled(): Boolean = torch
    fun hasFlashUnit(): Boolean = currentCharacteristics()
        ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    fun enableTorch(enabled: Boolean) {
        torch = enabled && hasFlashUnit()
        updateRepeatingRequest()
    }

    fun stop() {
        if (!started) return
        started = false
        discovering = false
        generation++
        closeSessionAndReader()
        camera?.close()
        camera = null
        waitingForFirstFrame = false
        frozenPreview?.let { (it.parent as? ViewGroup)?.removeView(it) }
        frozenPreview = null
    }

    fun release() {
        released = true
        stop()
        closeSessionAndReader()
        camera?.close()
        camera = null
        previewSurface?.release()
        previewSurface = null
        cameraThread.quitSafely()
        decodeThread.quitSafely()
        sessionExecutor.shutdown()
    }

    @SuppressLint("MissingPermission")
    private fun openSelectedLens() {
        if (released || !started || lensIndex !in lenses.indices || !textureView.isAvailable) return
        val lens = lenses[lensIndex]
        if (bindingIndex !in lens.bindings.indices) bindingIndex = 0
        val binding = lens.bindings[bindingIndex]
        attemptLensIndex = lensIndex
        attemptBindingIndex = bindingIndex
        val token = ++generation
        LogX.i(
            "Camera2 open attempt: lens=%s ratio=%f open=%s physical=%s",
            lens.stableId,
            lens.ratio,
            binding.openCameraId,
            binding.physicalCameraId ?: "-"
        )
        freezeFrame()
        closeSessionAndReader()
        val existing = camera
        if (existing != null && existing.id == binding.openCameraId) {
            currentBinding = binding
            createSession(existing, binding, token)
            return
        }
        existing?.close()
        camera = null
        manager.openCamera(binding.openCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (released || token != generation) { device.close(); return }
                camera = device
                currentBinding = binding
                createSession(device, binding, token)
            }
            override fun onDisconnected(device: CameraDevice) {
                device.close()
                if (camera === device) camera = null
                tryNextBinding(token, "disconnected")
            }
            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                if (camera === device) camera = null
                tryNextBinding(token, "open error=$error")
            }
        }, cameraHandler)
    }

    private fun createSession(
        device: CameraDevice,
        binding: Camera2LensDiscovery.Binding,
        token: Int
    ) {
        val chars = runCatching { manager.getCameraCharacteristics(binding.physicalCameraId ?: binding.openCameraId) }
            .getOrElse { manager.getCameraCharacteristics(binding.openCameraId) }
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return tryNextBinding(token, "no stream map")
        previewSize = chooseSize(map.getOutputSizes(SurfaceTexture::class.java), 1920 * 1080)
        val analysisSize = chooseSize(map.getOutputSizes(ImageFormat.YUV_420_888), 1280 * 720)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val surfaceTexture = textureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        configureTransform()
        previewSurface?.release()
        previewSurface = Surface(surfaceTexture)
        reader = ImageReader.newInstance(
            analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 3
        ).also { it.setOnImageAvailableListener(::onImageAvailable, decodeHandler) }

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(value: CameraCaptureSession) {
                if (released || token != generation) { value.close(); return }
                session = value
                requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface!!)
                    addTarget(reader!!.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                updateRepeatingRequest()
                waitingForFirstFrame = true
                firstFrameCount = 0
                textureView.postDelayed({
                    if (waitingForFirstFrame && token == generation) {
                        waitingForFirstFrame = false
                        tryNextBinding(token, "preview produced no frames")
                    }
                }, 2200L)
            }
            override fun onConfigureFailed(value: CameraCaptureSession) {
                value.close(); tryNextBinding(token, "session config failed")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createSessionApi28(
                device,
                previewSurface!!,
                reader!!.surface,
                binding.physicalCameraId,
                callback
            )
        } else {
            @Suppress("DEPRECATION")
            device.createCaptureSession(
                listOf(previewSurface!!, reader!!.surface),
                callback,
                cameraHandler
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createSessionApi28(
        device: CameraDevice,
        preview: Surface,
        analysis: Surface,
        physicalCameraId: String?,
        callback: CameraCaptureSession.StateCallback
    ) {
        val previewOutput = OutputConfiguration(preview)
        val analysisOutput = OutputConfiguration(analysis)
        if (physicalCameraId != null) {
            previewOutput.setPhysicalCameraId(physicalCameraId)
            analysisOutput.setPhysicalCameraId(physicalCameraId)
        }
        device.createCaptureSession(
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(previewOutput, analysisOutput),
                sessionExecutor,
                callback
            )
        )
    }

    private fun configureTransform() {
        val width = textureView.width.toFloat()
        val height = textureView.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val rotation = textureView.display?.rotation ?: Surface.ROTATION_0
        val buffer = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        } else {
            RectF(0f, 0f, previewSize.width.toFloat(), previewSize.height.toFloat())
        }
        val view = RectF(0f, 0f, width, height)
        val matrix = Matrix()
        val scale = max(width / buffer.width(), height / buffer.height())
        matrix.setRectToRect(buffer, view, Matrix.ScaleToFit.CENTER)
        matrix.postScale(scale, scale, width / 2f, height / 2f)
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
        matrix.postRotate(degrees, width / 2f, height / 2f)
        textureView.setTransform(matrix)
    }

    private fun tryNextBinding(token: Int, reason: String) {
        if (released || token != generation) return
        val failed = currentBinding
        failed?.let { failedBindingKeys += bindingKey(it) }
        LogX.w("Camera2 binding failed: %s; binding=%s", reason, failed)
        closeSessionAndReader()
        camera?.close()
        camera = null

        val bindings = lenses.getOrNull(lensIndex)?.bindings.orEmpty()
        val next = (bindingIndex + 1 until bindings.size).firstOrNull { index ->
            bindingKey(bindings[index]) !in failedBindingKeys
        }
        if (next != null) {
            bindingIndex = next
            openSelectedLens()
            return
        }

        // Roll back to the last session that actually delivered preview frames.
        if (lastWorkingLensIndex in lenses.indices && lastWorkingBindingIndex >= 0) {
            val last = lenses[lastWorkingLensIndex].bindings.getOrNull(lastWorkingBindingIndex)
            if (last != null && bindingKey(last) !in failedBindingKeys) {
                lensIndex = lastWorkingLensIndex
                bindingIndex = lastWorkingBindingIndex
                openSelectedLens()
                return
            }
        }

        // Initial startup fallback: use the closest-to-1x lens and its first unfailed binding.
        val mains = lenses.indices.sortedBy { abs(lenses[it].ratio - 1f) }
        for (main in mains) {
            val candidate = lenses[main].bindings.indices.firstOrNull { index ->
                bindingKey(lenses[main].bindings[index]) !in failedBindingKeys
            } ?: continue
            lensIndex = main
            bindingIndex = candidate
            openSelectedLens()
            return
        }
        // Everything exposed by Camera2 failed. Reveal the last frame and stop retrying.
        fadeFrozenFrame()
    }

    private fun bindingKey(binding: Camera2LensDiscovery.Binding): String =
        "${binding.openCameraId}|${binding.physicalCameraId.orEmpty()}"

    private fun onImageAvailable(source: ImageReader) {
        val image = source.acquireLatestImage() ?: return
        val now = System.nanoTime()
        if (!analyze || now - lastAnalyzeNs < 80_000_000L || !decoding.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastAnalyzeNs = now
        try {
            val width = image.width
            val height = image.height
            val y = copyLuma(image)
            val rotation = frameRotationDegrees()
            val rotated = rotateLuma(y, width, height, rotation)
            val outWidth = if (rotation == 90 || rotation == 270) height else width
            val outHeight = if (rotation == 90 || rotation == 270) width else height
            val result = analyzer.analyze(rotated, outWidth, outHeight)
            if (result != null) {
                analyze = false
                textureView.post { onResult(result, outWidth, outHeight) }
            }
        } catch (e: Exception) {
            LogX.w(e)
        } finally {
            image.close()
            decoding.set(false)
        }
    }

    private fun copyLuma(image: Image): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val result = ByteArray(width * height)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val base = buffer.position()
        val limit = buffer.limit()
        for (y in 0 until height) {
            val offset = y * width
            val rowStart = base + y * rowStride
            for (x in 0 until width) {
                val index = rowStart + x * pixelStride
                result[offset + x] = if (index < limit) buffer.get(index) else 0
            }
        }
        return result
    }

    private fun frameRotationDegrees(): Int {
        val displayDegrees = when (textureView.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation - displayDegrees + 360) % 360
    }

    private fun rotateLuma(data: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
        if (degrees == 0) return data
        val out = ByteArray(data.size)
        when (degrees) {
            90 -> for (y in 0 until height) for (x in 0 until width) {
                out[x * height + (height - y - 1)] = data[y * width + x]
            }
            180 -> for (i in data.indices) out[data.lastIndex - i] = data[i]
            270 -> for (y in 0 until height) for (x in 0 until width) {
                out[(width - x - 1) * height + y] = data[y * width + x]
            }
            else -> return data
        }
        return out
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            virtualZoom = (virtualZoom * detector.scaleFactor).coerceIn(
                lenses.firstOrNull()?.ratio ?: 0.5f,
                (lenses.lastOrNull()?.ratio ?: 1f) * maxDigitalZoom()
            )
            val target = selectLensIndex(virtualZoom)
            if (target != lensIndex) {
                failedBindingKeys.clear()
                lensIndex = target
                bindingIndex = 0
                openSelectedLens()
            } else updateRepeatingRequest()
            return true
        }
    }

    private fun selectLensIndex(zoom: Float): Int {
        if (lenses.size <= 1) return 0
        var selected = 0
        for (index in 0 until lenses.lastIndex) {
            val left = lenses[index].ratio.coerceAtLeast(0.01f)
            val right = lenses[index + 1].ratio.coerceAtLeast(left)
            // Geometric midpoint is stable for optical ratios (0.5↔1 => 0.707,
            // 1↔3.2 => 1.789) and avoids switching tele immediately after 1x.
            val boundary = kotlin.math.sqrt((left * right).toDouble()).toFloat()
            if (zoom >= boundary) selected = index + 1 else break
        }
        return selected
    }

    private fun updateRepeatingRequest() {
        val builder = requestBuilder ?: return
        val activeLens = lenses.getOrNull(lensIndex) ?: return
        val localZoom = (virtualZoom / activeLens.ratio).coerceIn(1f, maxDigitalZoom())
        val chars = currentCharacteristics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val range = chars?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (range != null) builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, localZoom.coerceIn(range.lower, range.upper))
            else applyCrop(builder, chars, localZoom)
        } else applyCrop(builder, chars, localZoom)
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
        runCatching { session?.setRepeatingRequest(builder.build(), null, cameraHandler) }
            .onFailure { LogX.w(it) }
    }

    private fun applyCrop(builder: CaptureRequest.Builder, chars: CameraCharacteristics?, zoom: Float) {
        val sensor = chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val width = (sensor.width() / zoom).toInt()
        val height = (sensor.height() / zoom).toInt()
        val left = sensor.centerX() - width / 2
        val top = sensor.centerY() - height / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + width, top + height))
    }

    private fun maxDigitalZoom(): Float = currentCharacteristics()
        ?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        ?.coerceAtLeast(1f) ?: 1f

    private fun currentCharacteristics(): CameraCharacteristics? {
        val id = currentBinding?.physicalCameraId ?: currentBinding?.openCameraId ?: return null
        return runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openSelectedLens()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeSessionAndReader(); return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            if (!waitingForFirstFrame) return
            firstFrameCount++
            if (firstFrameCount >= 2) {
                waitingForFirstFrame = false
                lastWorkingLensIndex = attemptLensIndex
                lastWorkingBindingIndex = attemptBindingIndex
                failedBindingKeys.clear()
                LogX.i(
                    "Camera2 first frames: lens=%s open=%s physical=%s",
                    lenses.getOrNull(lastWorkingLensIndex)?.stableId ?: "?",
                    currentBinding?.openCameraId ?: "?",
                    currentBinding?.physicalCameraId ?: "-"
                )
                fadeFrozenFrame()
            }
        }
    }

    private fun chooseSize(sizes: Array<Size>?, targetArea: Int): Size {
        val valid = sizes.orEmpty().filter { it.width > 0 && it.height > 0 }
        if (valid.isEmpty()) return Size(1280, 720)
        return valid.minByOrNull { abs(it.width * it.height - targetArea) } ?: valid.first()
    }

    private fun closeSessionAndReader() {
        runCatching { session?.stopRepeating() }
        session?.close(); session = null
        reader?.close(); reader = null
        requestBuilder = null
    }

    private fun freezeFrame() {
        val bitmap = textureView.bitmap ?: return
        val parent = textureView.parent as? ViewGroup ?: return
        frozenPreview?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val overlay = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }
        parent.addView(
            overlay,
            parent.indexOfChild(textureView) + 1,
            ViewGroup.LayoutParams(textureView.width, textureView.height)
        )
        frozenPreview = overlay
    }

    private fun fadeFrozenFrame() {
        frozenPreview?.animate()?.alpha(0f)?.setDuration(160L)?.withEndAction {
            frozenPreview?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                (view.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            }
            frozenPreview = null
        }?.start()
    }
}
