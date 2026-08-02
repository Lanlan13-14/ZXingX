package com.king.zxing.app

import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.zxing.Camera2BarcodeScanActivity
import com.king.zxing.DecodeConfig
import com.king.zxing.analyze.MultiFormatAnalyzer

/**
 * 连续扫码（识别多种格式）示例
 *
 * 每次识别后进入扫描结果页，关闭结果页后继续分析。
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
class MultiFormatScanActivity : Camera2BarcodeScanActivity() {

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        setAnalyzeImage(true)
    }

    override fun createAnalyzer(): com.king.zxing.analyze.ImageAnalyzer {
        val decodeConfig = DecodeConfig()
            .setSupportVerticalCode(true)
            .setSupportLuminanceInvert(true)
        return MultiFormatAnalyzer(decodeConfig)
    }

    override fun onScanResult(result: AnalyzeResult<Result>) {
        setAnalyzeImage(false)
        val intent = ScanResultActivity.createIntent(this, result.result.text)
        resultLauncher.launch(intent)
    }
}
