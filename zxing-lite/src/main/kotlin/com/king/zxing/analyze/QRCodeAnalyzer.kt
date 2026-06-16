package com.king.zxing.analyze

import com.google.zxing.qrcode.QRCodeReader
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager

/**
 * 二维码分析器
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
class QRCodeAnalyzer @JvmOverloads constructor(
    decodeConfig: DecodeConfig = DecodeConfig().setHints(
        DecodeFormatManager.QR_CODE_HINTS
    )
) : BarcodeFormatAnalyzer<QRCodeReader>(decodeConfig) {

    override fun createReader(): QRCodeReader {
        return QRCodeReader()
    }
}
