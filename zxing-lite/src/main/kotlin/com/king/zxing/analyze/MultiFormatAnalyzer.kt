package com.king.zxing.analyze

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.king.zxing.DecodeConfig

/**
 * 多格式分析器：主要用于分析识别条形码/二维码
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
open class MultiFormatAnalyzer @JvmOverloads constructor(
    decodeConfig: DecodeConfig = DecodeConfig()
) : BarcodeFormatAnalyzer<MultiFormatReader>(decodeConfig) {

    constructor(hints: Map<DecodeHintType, Any>?) : this(DecodeConfig().setHints(hints))

    override fun decode(source: LuminanceSource, isMultiDecode: Boolean): Result? {
        var result: Result? = null
        runCatching {
            runCatching {
                // 采用HybridBinarizer解析
                result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            }
            if (isMultiDecode && result == null) {
                // 如果没有解析成功，再采用GlobalHistogramBinarizer解析一次
                result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            }
        }
        return result
    }

    override fun createReader(): MultiFormatReader {
        return MultiFormatReader().apply {
            setHints(decodeConfig.getHints())
        }
    }

}

