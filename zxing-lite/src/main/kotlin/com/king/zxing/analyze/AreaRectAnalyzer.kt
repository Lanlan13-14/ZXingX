package com.king.zxing.analyze

import android.graphics.Rect
import com.google.zxing.Result
import com.king.zxing.DecodeConfig

/**
 * 矩阵区域分析器：主要用于锁定具体的识别区域
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
abstract class AreaRectAnalyzer(protected val decodeConfig: DecodeConfig) : ImageAnalyzer() {

    override fun analyze(data: ByteArray, width: Int, height: Int): Result? {
        if (decodeConfig.isFullAreaScan()) {
            // 使用全区域进行扫码识别
            return analyze(data, width, height, 0, 0, width, height)
        }

        val rect: Rect? = decodeConfig.getAnalyzeAreaRect()
        if (rect != null) {
            // 如果分析区域不为空，则使用指定的区域进行扫码识别
            return analyze(data, width, height, rect.left, rect.top, rect.width(), rect.height())
        }

        // 如果分析区域为空，则通过识别区域比例和相关的偏移量计算出最终的区域进行扫码识别
        val size = (minOf(width, height) * decodeConfig.getAreaRectRatio()).toInt()
        val left = (width - size) / 2 + decodeConfig.getAreaRectHorizontalOffset()
        val top = (height - size) / 2 + decodeConfig.getAreaRectVerticalOffset()

        return analyze(data, width, height, left, top, size, size)
    }

    abstract fun analyze(
        data: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Result?
}
