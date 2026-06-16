/*
 * Copyright (C) 2018 Jenly Yu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.king.zxing.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.king.logx.LogX
import com.king.zxing.DecodeFormatManager
import java.util.HashMap

/**
 * 二维码/条形码工具类：主要包括二维码/条形码的解析与生成
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
object CodeUtils {

    /** 默认解析图片目标宽度。 */
    const val DEFAULT_REQ_WIDTH = 480

    /** 默认解析图片目标高度。 */
    const val DEFAULT_REQ_HEIGHT = 640

    /**
     * 生成二维码。
     *
     * 该方法对应 Java 版本中的多组重载：可选中间 Logo、Logo 比例、编码参数与二维码颜色。
     * 在 Kotlin 中通过默认参数进行统一。
     *
     * @param content 二维码内容。
     * @param size 二维码边长（像素）。
     * @param logo 二维码中间的 Logo；为 `null` 时不添加。
     * @param ratio Logo 占二维码宽度比例，建议不超过 `0.3`。
     * @param hints 编码参数，默认使用高容错、`utf-8`、边距 `1`。
     * @param codeColor 二维码前景色。
     * @return 生成成功返回 [Bitmap]，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun createQRCode(
        content: String,
        size: Int,
        logo: Bitmap? = null,
        @FloatRange(from = 0.0, to = 1.0) ratio: Float = 0.2f,
        hints: Map<EncodeHintType, *>? = buildQRCodeHints(),
        @ColorInt codeColor: Int = Color.BLACK
    ): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (bitMatrix.get(x, y)) codeColor else Color.WHITE
                }
            }

            var bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            if (logo != null) {
                bitmap = addLogo(bitmap, logo, ratio) ?: return null
            }
            bitmap
        } catch (e: Exception) {
            LogX.w(e)
            null
        }
    }

    /**
     * 在二维码中间添加 Logo。
     *
     * @param src 原始二维码图。
     * @param logo Logo 图。
     * @param ratio Logo 占二维码宽度比例。
     * @return 合成后的位图；当输入无效或处理失败时返回 `null`。
     */
    private fun addLogo(src: Bitmap?, logo: Bitmap?, @FloatRange(from = 0.0, to = 1.0) ratio: Float): Bitmap? {
        if (src == null) {
            return null
        }
        if (logo == null) {
            return src
        }

        val srcWidth = src.width
        val srcHeight = src.height
        val logoWidth = logo.width
        val logoHeight = logo.height

        if (srcWidth == 0 || srcHeight == 0) {
            return null
        }
        if (logoWidth == 0 || logoHeight == 0) {
            return src
        }

        val scaleFactor = srcWidth * ratio / logoWidth
        return try {
            val bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(src, 0f, 0f, null)
            canvas.scale(scaleFactor, scaleFactor, srcWidth / 2f, srcHeight / 2f)
            canvas.drawBitmap(logo, (srcWidth - logoWidth) / 2f, (srcHeight - logoHeight) / 2f, null)
            canvas.save()
            canvas.restore()
            bitmap
        } catch (e: Exception) {
            LogX.w(e)
            null
        }
    }

    /**
     * 解析二维码图片路径，返回文本内容。
     *
     * @param bitmapPath 需要解析的图片路径。
     * @return 解析成功返回文本，失败返回 `null`。
     */
    @JvmStatic
    fun parseQRCode(bitmapPath: String): String? {
        return parseQRCodeResult(bitmapPath)?.text
    }

    /**
     * 解析二维码图片路径，返回 [Result]。
     *
     * 当原图尺寸大于目标尺寸时会先压缩，再进行解析。
     * 当 `reqWidth` 和 `reqHeight` 小于等于 0 时不压缩。
     *
     * @param bitmapPath 需要解析的图片路径。
     * @param reqWidth 请求目标宽度。
     * @param reqHeight 请求目标高度。
     * @return 解析结果，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseQRCodeResult(bitmapPath: String, reqWidth: Int = DEFAULT_REQ_WIDTH, reqHeight: Int = DEFAULT_REQ_HEIGHT): Result? {
        return parseCodeResult(bitmapPath, reqWidth, reqHeight, DecodeFormatManager.QR_CODE_HINTS)
    }

    /**
     * 解析一维码/二维码图片路径，返回文本内容。
     *
     * @param bitmapPath 需要解析的图片路径。
     * @param hints 解析编码类型配置。
     * @return 解析成功返回文本，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseCode(bitmapPath: String, hints: Map<DecodeHintType, Any>? = DecodeFormatManager.ALL_HINTS): String? {
        return parseCodeResult(bitmapPath, hints = hints)?.text
    }

    /**
     * 解析二维码位图，返回文本内容。
     */
    @JvmStatic
    fun parseQRCode(bitmap: Bitmap): String? {
        return parseCode(bitmap, DecodeFormatManager.QR_CODE_HINTS)
    }

    /**
     * 解析一维码/二维码位图，返回文本内容。
     *
     * @param bitmap 需要解析的位图。
     * @param hints 解析编码类型配置。
     * @return 解析成功返回文本，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseCode(bitmap: Bitmap, hints: Map<DecodeHintType, Any>? = DecodeFormatManager.ALL_HINTS): String? {
        return parseCodeResult(bitmap, hints)?.text
    }

    /**
     * 解析一维码/二维码图片路径，返回 [Result]。
     *
     * @param bitmapPath 需要解析的图片路径。
     * @param reqWidth 请求目标宽度。
     * @param reqHeight 请求目标高度。
     * @param hints 解析编码类型配置。
     * @return 解析结果，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseCodeResult(
        bitmapPath: String,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT,
        hints: Map<DecodeHintType, Any>? = DecodeFormatManager.ALL_HINTS
    ): Result? {
        val bitmap = compressBitmap(bitmapPath, reqWidth, reqHeight) ?: return null
        return parseCodeResult(bitmap, hints)
    }

    /**
     * 解析一维码/二维码位图，返回 [Result]。
     *
     * @param bitmap 需要解析的位图。
     * @param hints 解析编码类型配置。
     * @return 解析结果，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseCodeResult(bitmap: Bitmap, hints: Map<DecodeHintType, Any>? = DecodeFormatManager.ALL_HINTS): Result? {
        return parseCodeResult(getRGBLuminanceSource(bitmap), hints)
    }

    /**
     * 解析一维码/二维码亮度源，返回 [Result]。
     *
     * 解析策略会按如下顺序尝试：
     * 1) 原图；2) 反色图；3) 支持旋转时的逆时针旋转图。
     *
     * @param source 亮度源。
     * @param hints 解析编码类型配置。
     * @return 解析结果，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun parseCodeResult(source: LuminanceSource?, hints: Map<DecodeHintType, Any>? = DecodeFormatManager.ALL_HINTS): Result? {
        var result: Result? = null
        val reader = MultiFormatReader()
        try {
            reader.setHints(hints)
            if (source != null) {
                result = decodeInternal(reader, source)
                if (result == null) {
                    result = decodeInternal(reader, source.invert())
                }
                if (result == null && source.isRotateSupported) {
                    result = decodeInternal(reader, source.rotateCounterClockwise())
                }
            }
        } catch (e: Exception) {
            LogX.w(e)
        } finally {
            reader.reset()
        }
        return result
    }

    /**
     * 内部解析实现：优先 [HybridBinarizer]，失败后尝试 [GlobalHistogramBinarizer]。
     */
    private fun decodeInternal(reader: MultiFormatReader, source: LuminanceSource): Result? {
        var result: Result? = null
        try {
            try {
                result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: Exception) {
            }
            if (result == null) {
                result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            }
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * 压缩图片到目标尺寸后再用于解析。
     *
     * 当 `reqWidth` 和 `reqHeight` 都大于 0 时按采样率压缩，否则按原图解码。
     */
    private fun compressBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (reqWidth > 0 && reqHeight > 0) {
            val newOpts = BitmapFactory.Options()
            newOpts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, newOpts)
            newOpts.inSampleSize = getSampleSize(reqWidth, reqHeight, newOpts)
            newOpts.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(path, newOpts)
        }
        return BitmapFactory.decodeFile(path)
    }

    /**
     * 根据目标尺寸计算采样率。
     */
    private fun getSampleSize(reqWidth: Int, reqHeight: Int, newOpts: BitmapFactory.Options): Int {
        val width = newOpts.outWidth.toFloat()
        val height = newOpts.outHeight.toFloat()
        var wSize = 1
        if (width > reqWidth) {
            wSize = (width / reqWidth).toInt()
        }
        var hSize = 1
        if (height > reqHeight) {
            hSize = (height / reqHeight).toInt()
        }
        var size = maxOf(wSize, hSize)
        if (size <= 0) {
            size = 1
        }
        return size
    }

    /**
     * 将位图转换为 [RGBLuminanceSource]。
     */
    private fun getRGBLuminanceSource(bitmap: Bitmap): RGBLuminanceSource {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return RGBLuminanceSource(width, height, pixels)
    }

    /**
     * 生成条形码（默认格式 [BarcodeFormat.CODE_128]）。
     *
     * 对应 Java 多重重载的 Kotlin 统一入口，可控制编码参数、是否显示文字、文字大小和颜色。
     *
     * @param content 条形码内容。
     * @param desiredWidth 目标宽度。
     * @param desiredHeight 目标高度。
     * @param hints 编码参数。
     * @param isShowText 是否在条形码下方显示文本。
     * @param textSize 文本字号（当 `isShowText=true` 时生效）。
     * @param codeColor 条形码颜色。
     * @return 生成成功返回 [Bitmap]，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun createBarCode(
        content: String,
        desiredWidth: Int,
        desiredHeight: Int,
        hints: Map<EncodeHintType, *>? = null,
        isShowText: Boolean = false,
        textSize: Int = 40,
        @ColorInt codeColor: Int = Color.BLACK
    ): Bitmap? {
        return createBarCode(content, BarcodeFormat.CODE_128, desiredWidth, desiredHeight, hints, isShowText, textSize, codeColor)
    }

    /**
     * 生成条形码。
     *
     * @param content 条形码内容。
     * @param format 条形码格式。
     * @param desiredWidth 目标宽度。
     * @param desiredHeight 目标高度。
     * @param hints 编码参数。
     * @param isShowText 是否在条形码下方显示文本。
     * @param textSize 文本字号（当 `isShowText=true` 时生效）。
     * @param codeColor 条形码颜色。
     * @return 生成成功返回 [Bitmap]，失败返回 `null`。
     */
    @JvmStatic
    @JvmOverloads
    fun createBarCode(
        content: String,
        format: BarcodeFormat,
        desiredWidth: Int,
        desiredHeight: Int,
        hints: Map<EncodeHintType, *>? = null,
        isShowText: Boolean = false,
        textSize: Int = 40,
        @ColorInt codeColor: Int = Color.BLACK
    ): Bitmap? {
        if (TextUtils.isEmpty(content)) {
            return null
        }
        val white = Color.WHITE
        val writer = MultiFormatWriter()
        return try {
            val result = writer.encode(content, format, desiredWidth, desiredHeight, hints)
            val width = result.width
            val height = result.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (result.get(x, y)) codeColor else white
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            if (isShowText) {
                addCode(bitmap, content, textSize, codeColor, textSize / 2)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            LogX.w(e)
            null
        }
    }

    /**
     * 在条形码下方添加文本信息。
     *
     * @param src 条形码位图。
     * @param code 文本内容。
     * @param textSize 文本字号。
     * @param textColor 文本颜色。
     * @param offset 文本与条码之间的垂直偏移。
     * @return 叠加文本后的位图，失败返回 `null`。
     */
    private fun addCode(src: Bitmap?, code: String?, textSize: Int, @ColorInt textColor: Int, offset: Int): Bitmap? {
        if (src == null) {
            return null
        }
        if (TextUtils.isEmpty(code)) {
            return src
        }

        val srcWidth = src.width
        val srcHeight = src.height
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null
        }

        return try {
            val bitmap = Bitmap.createBitmap(srcWidth, srcHeight + textSize + offset * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(src, 0f, 0f, null)
            val paint = TextPaint()
            paint.textSize = textSize.toFloat()
            paint.color = textColor
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(code!!, srcWidth / 2f, srcHeight + textSize / 2f + offset, paint)
            canvas.save()
            canvas.restore()
            bitmap
        } catch (e: Exception) {
            LogX.w(e)
            null
        }
    }

    /**
     * 构建二维码默认编码参数。
     *
     * - 字符集：`utf-8`
     * - 容错级别：`H`
     * - 边距：`1`
     */
    private fun buildQRCodeHints(): Map<EncodeHintType, Any> {
        return HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.CHARACTER_SET, "utf-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            put(EncodeHintType.MARGIN, 1)
        }
    }
}
