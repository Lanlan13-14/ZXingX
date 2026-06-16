package com.king.zxing.analyze

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Reader
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.king.logx.LogX
import com.king.zxing.DecodeConfig

/**
 * 条码分析器
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
abstract class BarcodeFormatAnalyzer<T : Reader>(
    decodeConfig: DecodeConfig
) : AreaRectAnalyzer(decodeConfig) {

    protected val reader: T

    constructor(hints: Map<DecodeHintType, Any>?) : this(DecodeConfig().setHints(hints))

    init {
        reader = createReader()
    }

    override fun analyze(
        data: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Result? {
        var rawResult: Result? = null
        runCatching {
            val start = System.currentTimeMillis()
            val source = PlanarYUVLuminanceSource(
                data,
                dataWidth,
                dataHeight,
                left,
                top,
                width,
                height,
                false
            )
            rawResult = decode(source, decodeConfig.isMultiDecode())

            if (rawResult == null && decodeConfig.isSupportVerticalCode()) {
                val rotatedData = ByteArray(data.size)
                for (y in 0 until dataHeight) {
                    for (x in 0 until dataWidth) {
                        rotatedData[x * dataHeight + dataHeight - y - 1] = data[x + y * dataWidth]
                    }
                }
                rawResult = decode(
                    PlanarYUVLuminanceSource(
                        rotatedData,
                        dataHeight,
                        dataWidth,
                        top,
                        left,
                        height,
                        width,
                        false
                    ),
                    decodeConfig.isSupportVerticalCodeMultiDecode()
                )
            }

            if (rawResult == null && decodeConfig.isSupportLuminanceInvert()) {
                rawResult = decode(
                    source.invert(),
                    decodeConfig.isSupportLuminanceInvertMultiDecode()
                )
            }
            if (rawResult != null) {
                val end = System.currentTimeMillis()
                LogX.d("Found barcode in ${end - start} ms")
            }
        }
        reader.reset()
        return rawResult
    }

    /**
     * 解码
     */
    protected open fun decode(source: LuminanceSource, isMultiDecode: Boolean): Result? {
        var result: Result? = null
        runCatching {
            runCatching {
                // 采用HybridBinarizer解析
                result = reader.decode(
                    BinaryBitmap(HybridBinarizer(source)), decodeConfig.getHints()
                )
            }
            if (isMultiDecode && result == null) {
                // 如果没有解析成功，再采用GlobalHistogramBinarizer解析一次
                result = reader.decode(
                    BinaryBitmap(GlobalHistogramBinarizer(source)),
                    decodeConfig.getHints()
                )
            }
        }
        return result
    }

    abstract fun createReader(): T
}
