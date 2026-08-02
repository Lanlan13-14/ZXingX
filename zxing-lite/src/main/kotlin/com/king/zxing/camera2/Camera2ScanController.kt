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
import java.util.concurrent.Executor
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
    private val onMultiTouch: () -> Unit = {},
    private val onResult: (Result, Int, Int) -> Unit
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("ZXingX-Camera2").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val decodeThread = HandlerThread("ZXingX-Decode").apply { start() }
    private val decodeHandler = Handler(decodeThread.looper)
    private val sessionExecutor = Executor { command -> cameraHandler.post(command) }
    private val decoding = AtomicBoolean(false)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var lenses: List<Camera2LensDiscovery.Lens> = emptyList()
    @Volatile private var lensIndex = -1
    private var bindingIndex = 0
    @Volatile private var virtualZoom = 1f
    private var currentBinding: Camera2LensDiscovery.Binding? = null
    private var attemptBinding: Camera2LensDiscovery.Binding? = null
    private var attemptLensIndex = -1
    private var attemptBindingIndex = -1
    private var lastWorkingLensIndex = -1
    private var lastWorkingBindingIndex = -1
    private val failedBindingKeys = mutableSetOf<String>()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    @Volatile private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    @Volatile private var activeSurfaceTexture: SurfaceTexture? = null
    private var requestBuilder: CaptureRequest.Builder? = null
    @Volatile private var torch = false
    @Volatile private var analyze = true
    @Volatile private var released = false
    @Volatile private var started = false
    @Volatile private var discovering = false
    private var generation = 0
    private var frozenPreview: ImageView? = null
    private var previewSize = Size(1280, 720)
    private var sensorOrientation = 90
    @Volatile private var displayRotationDegrees = 0
    private var waitingForFirstFrame = false
    private var firstFrameCount = 0
    private var lastAnalyzeNs = 0L
    private var switchInFlight = false
    private var pendingLensIndex: Int? = null
    private var firstFrameTimeout: Runnable? = null
    private var lensSwitchDebounce: Runnable? = null
    private var debouncedTargetIndex: Int? = null

    fun start() {
        if (released || started) return
        started = true
        textureView.surfaceTextureListener = surfaceListener
        textureView.setOnTouchListener { _, event ->
            if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                onMultiTouch()
            }
            scaleDetector.onTouchEvent(event)
            true
        }
        if (lenses.isNotEmpty()) {
            if (textureView.isAvailable) textureView.post(::requestOpenSelectedLens)
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
                        "${lens.id}@${"%.2f".format(java.util.Locale.US, lens.ratio)}x=" +
                            lens.bindings.joinToString(prefix = "[", postfix = "]") { binding ->
                                "open:${binding.openCameraId}/physical:${binding.physicalCameraId ?: "-"}"
                            }
                    }
                )
                lensIndex = lenses.indices.minByOrNull { abs(lenses[it].ratio - 1f) } ?: -1
                if (lensIndex < 0) {
                    LogX.e("Camera2: no usable back lens")
                } else if (textureView.isAvailable) {
                    textureView.post(::requestOpenSelectedLens)
                }
            }
        }
    }

    fun setAnalyzeImage(enabled: Boolean) { analyze = enabled }
    fun isTorchEnabled(): Boolean = torch
    fun hasFlashUnit(): Boolean {
        val openId = currentBinding?.openCameraId ?: return false
        return runCatching { manager.getCameraCharacteristics(openId) }
            .getOrNull()
            ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    fun enableTorch(enabled: Boolean) {
        cameraHandler.post {
            torch = enabled && hasFlashUnit()
            updateRepeatingRequest()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        discovering = false
        val token = ++generation
        waitingForFirstFrame = false
        firstFrameTimeout?.let(textureView::removeCallbacks)
        firstFrameTimeout = null
        switchInFlight = false
        pendingLensIndex = null
        lensSwitchDebounce?.let(cameraHandler::removeCallbacks)
        lensSwitchDebounce = null
        debouncedTargetIndex = null
        textureView.post(::removeFrozenFrameImmediately)
        cameraHandler.post {
            if (token != generation) return@post
            closeSessionAndReader()
            camera?.close()
            camera = null
        }
    }

    fun release() {
        if (released) return
        released = true
        started = false
        discovering = false
        ++generation
        waitingForFirstFrame = false
        firstFrameTimeout?.let(textureView::removeCallbacks)
        firstFrameTimeout = null
        switchInFlight = false
        pendingLensIndex = null
        lensSwitchDebounce?.let(cameraHandler::removeCallbacks)
        lensSwitchDebounce = null
        debouncedTargetIndex = null
        textureView.post(::removeFrozenFrameImmediately)
        cameraHandler.post {
            closeSessionAndReader()
            camera?.close()
            camera = null
            previewSurface?.release()
            previewSurface = null
            activeSurfaceTexture = null
            cameraThread.quitSafely()
        }
        decodeThread.quitSafely()
    }

    private fun requestOpenSelectedLens() {
        if (released || !started || lensIndex !in lenses.indices || !textureView.isAvailable) return
        val startingTransaction = !switchInFlight
        switchInFlight = true
        if (startingTransaction) freezeFrame()
        val surfaceTexture = textureView.surfaceTexture ?: return
        activeSurfaceTexture = surfaceTexture
        cameraHandler.post { openSelectedLensOnCameraThread(surfaceTexture) }
    }

    @SuppressLint("MissingPermission")
    private fun openSelectedLensOnCameraThread(surfaceTexture: SurfaceTexture) {
        if (released || !started || lensIndex !in lenses.indices) return
        val lens = lenses[lensIndex]
        if (bindingIndex !in lens.bindings.indices) bindingIndex = 0
        val binding = lens.bindings[bindingIndex]
        attemptBinding = binding
        attemptLensIndex = lensIndex
        attemptBindingIndex = bindingIndex
        val token = ++generation
        LogX.i(
            "Camera2 open attempt: lens=%s ratio=%f open=%s physical=%s",
            lens.id,
            lens.ratio,
            binding.openCameraId,
            binding.physicalCameraId ?: "-"
        )
        closeSessionAndReader()
        val existing = camera
        if (existing != null && existing.id == binding.openCameraId) {
            currentBinding = binding
            createSession(existing, binding, token, surfaceTexture)
            return
        }
        existing?.close()
        camera = null
        manager.openCamera(binding.openCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (released || token != generation) { device.close(); return }
                camera = device
                currentBinding = binding
                createSession(device, binding, token, surfaceTexture)
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
        token: Int,
        surfaceTexture: SurfaceTexture
    ) {
        val logicalChars = runCatching { manager.getCameraCharacteristics(binding.openCameraId) }
            .getOrElse { return tryNextBinding(token, "logical characteristics: ${it.message}") }
        val lensChars = binding.physicalCameraId?.let { id ->
            runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
        } ?: logicalChars
        val logicalMap = logicalChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return tryNextBinding(token, "no logical stream map")
        val lensMap = lensChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: logicalMap
        previewSize = chooseCompatibleSize(
            logicalMap.getOutputSizes(SurfaceTexture::class.java),
            lensMap.getOutputSizes(SurfaceTexture::class.java),
            1920 * 1080
        )
        val analysisSize = chooseCompatibleSize(
            logicalMap.getOutputSizes(ImageFormat.YUV_420_888),
            lensMap.getOutputSizes(ImageFormat.YUV_420_888),
            1280 * 720
        )
        sensorOrientation = lensChars.get(CameraCharacteristics.SENSOR_ORIENTATION)
            ?: logicalChars.get(CameraCharacteristics.SENSOR_ORIENTATION)
            ?: 90
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        textureView.post(::configureTransform)
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
                firstFrameTimeout?.let(textureView::removeCallbacks)
                firstFrameTimeout = Runnable {
                    if (waitingForFirstFrame && token == generation) {
                        waitingForFirstFrame = false
                        cameraHandler.post { tryNextBinding(token, "preview produced no frames") }
                    }
                }.also { textureView.postDelayed(it, 2200L) }
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
        displayRotationDegrees = degrees.toInt()
        matrix.postRotate(degrees, width / 2f, height / 2f)
        textureView.setTransform(matrix)
    }

    private fun tryNextBinding(token: Int, reason: String) {
        if (released || token != generation) return
        val failed = attemptBinding
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
            activeSurfaceTexture?.let(::openSelectedLensOnCameraThread)
            return
        }

        // Roll back to the last session that actually delivered preview frames.
        if (lastWorkingLensIndex in lenses.indices && lastWorkingBindingIndex >= 0) {
            val last = lenses[lastWorkingLensIndex].bindings.getOrNull(lastWorkingBindingIndex)
            if (last != null && bindingKey(last) !in failedBindingKeys) {
                pendingLensIndex = null
                lensIndex = lastWorkingLensIndex
                bindingIndex = lastWorkingBindingIndex
                activeSurfaceTexture?.let(::openSelectedLensOnCameraThread)
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
            activeSurfaceTexture?.let(::openSelectedLensOnCameraThread)
            return
        }
        // Everything exposed by Camera2 failed. Reveal the last frame and stop retrying.
        pendingLensIndex = null
        switchInFlight = false
        textureView.post { fadeFrozenFrame() }
    }

    private fun bindingKey(binding: Camera2LensDiscovery.Binding): String =
        "${binding.openCameraId}|${binding.physicalCameraId.orEmpty()}"

    private fun onImageAvailable(source: ImageReader) {
        if (source !== reader) {
            source.acquireLatestImage()?.close()
            return
        }
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

    private fun frameRotationDegrees(): Int =
        (sensorOrientation - displayRotationDegrees + 360) % 360

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
            val requestedZoom = virtualZoom
            cameraHandler.post { handleZoomRequest(requestedZoom) }
            return true
        }
    }

    private fun handleZoomRequest(requestedZoom: Float) {
        if (released || !started) return
        virtualZoom = requestedZoom
        val target = selectLensIndex(requestedZoom)
        // Digital zoom remains frame-by-frame responsive while physical rebind waits
        // for 140 ms of target stability. This collapses noisy pinch crossings.
        if (!switchInFlight) updateRepeatingRequest()
        if (target == lensIndex) {
            lensSwitchDebounce?.let(cameraHandler::removeCallbacks)
            lensSwitchDebounce = null
            debouncedTargetIndex = null
            return
        }
        if (switchInFlight) {
            pendingLensIndex = target
            return
        }
        if (debouncedTargetIndex == target) return
        lensSwitchDebounce?.let(cameraHandler::removeCallbacks)
        debouncedTargetIndex = target
        lensSwitchDebounce = Runnable {
            lensSwitchDebounce = null
            val stableTarget = debouncedTargetIndex
            debouncedTargetIndex = null
            if (stableTarget != null && stableTarget == selectLensIndex(virtualZoom) && stableTarget != lensIndex) {
                beginLensSwitch(stableTarget)
            }
        }.also { cameraHandler.postDelayed(it, 140L) }
    }

    private fun beginLensSwitch(target: Int) {
        if (target !in lenses.indices || target == lensIndex) return
        failedBindingKeys.clear()
        lensSwitchDebounce?.let(cameraHandler::removeCallbacks)
        lensSwitchDebounce = null
        debouncedTargetIndex = null
        pendingLensIndex = null
        lensIndex = target
        bindingIndex = 0
        textureView.post(::requestOpenSelectedLens)
    }

    private fun finishLensSwitch() {
        switchInFlight = false
        val pending = pendingLensIndex
        pendingLensIndex = null
        if (pending != null && pending != lensIndex) {
            cameraHandler.post { beginLensSwitch(pending) }
        } else {
            updateRepeatingRequest()
        }
    }

    private fun selectLensIndex(zoom: Float): Int {
        if (lenses.size <= 1) return 0
        var selected = lensIndex.coerceIn(0, lenses.lastIndex)
        // Switch near each lens' intrinsic optical ratio, not at the midpoint.
        // Small asymmetric hysteresis prevents boundary oscillation while keeping
        // effective FOV continuous (e.g. main 3.1x -> tele 1.0x at a 3.2x lens).
        while (selected < lenses.lastIndex) {
            val nextRatio = lenses[selected + 1].ratio
            if (zoom >= nextRatio * 0.97f) selected++ else break
        }
        while (selected > 0) {
            val currentRatio = lenses[selected].ratio
            if (zoom < currentRatio * 0.93f) selected-- else break
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
        // CaptureRequest keys are validated by the opened CameraDevice (logical parent
        // for physical outputs), so zoom/crop/flash limits must come from openCameraId.
        val id = currentBinding?.openCameraId ?: return null
        return runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            textureView.post(::requestOpenSelectedLens)
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
                firstFrameTimeout?.let(textureView::removeCallbacks)
                firstFrameTimeout = null
                lastWorkingLensIndex = attemptLensIndex
                lastWorkingBindingIndex = attemptBindingIndex
                failedBindingKeys.clear()
                attemptBinding = null
                LogX.i(
                    "Camera2 first frames: lens=%s open=%s physical=%s",
                    lenses.getOrNull(lastWorkingLensIndex)?.id ?: "?",
                    currentBinding?.openCameraId ?: "?",
                    currentBinding?.physicalCameraId ?: "-"
                )
                fadeFrozenFrame { cameraHandler.post(::finishLensSwitch) }
            }
        }
    }

    private fun chooseCompatibleSize(
        logicalSizes: Array<Size>?,
        lensSizes: Array<Size>?,
        targetArea: Int
    ): Size {
        val logical = logicalSizes.orEmpty().filter { it.width > 0 && it.height > 0 }
        val lensKeys = lensSizes.orEmpty().map { it.width to it.height }.toSet()
        val common = logical.filter { (it.width to it.height) in lensKeys }
        val candidates = common.ifEmpty { lensSizes.orEmpty().filter { it.width > 0 && it.height > 0 } }
        if (candidates.isEmpty()) return Size(1280, 720)
        return candidates.minByOrNull { size ->
            val areaPenalty = abs(size.width * size.height - targetArea).toLong()
            val ratio = size.width.toFloat() / size.height.coerceAtLeast(1)
            val ratioPenalty = (abs(ratio - 16f / 9f) * targetArea).toLong()
            areaPenalty + ratioPenalty
        } ?: candidates.first()
    }

    private fun closeSessionAndReader() {
        runCatching { session?.stopRepeating() }
        session?.close(); session = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close(); reader = null
        requestBuilder = null
    }

    private fun freezeFrame() {
        val bitmap = textureView.bitmap ?: return
        val parent = textureView.parent as? ViewGroup ?: run {
            bitmap.recycle()
            return
        }
        // Remove the previous overlay synchronously before replacing the reference.
        removeFrozenFrameImmediately()
        val overlay = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
            alpha = 1f
        }
        parent.addView(
            overlay,
            parent.indexOfChild(textureView) + 1,
            ViewGroup.LayoutParams(textureView.width, textureView.height)
        )
        frozenPreview = overlay
    }

    private fun fadeFrozenFrame(onEnd: () -> Unit = {}) {
        val overlay = frozenPreview ?: run {
            onEnd()
            return
        }
        overlay.animate().cancel()
        overlay.animate()
            .alpha(0f)
            .setDuration(240L)
            .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction {
                // This callback owns only the overlay it captured. Never touch a newer one.
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                (overlay.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                if (frozenPreview === overlay) frozenPreview = null
                onEnd()
            }
            .start()
    }

    private fun removeFrozenFrameImmediately() {
        val overlay = frozenPreview ?: return
        overlay.animate().cancel()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        (overlay.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        if (frozenPreview === overlay) frozenPreview = null
    }
}
