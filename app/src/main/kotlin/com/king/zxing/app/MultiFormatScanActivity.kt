package com.king.zxing.app

import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.zxing.BarcodeCameraScanActivity
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
class MultiFormatScanActivity : BarcodeCameraScanActivity() {

    private var paymentAppOpened = false

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 结果页关闭后继续扫码
        cameraScan.setAnalyzeImage(true)
    }

    override fun onResume() {
        super.onResume()
        if (paymentAppOpened) {
            paymentAppOpened = false
            cameraScan.setAnalyzeImage(true)
        }
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        cameraScan.setPlayBeep(true)
    }

    override fun createAnalyzer(): Analyzer<Result>? {
        val decodeConfig = DecodeConfig()
            .setSupportVerticalCode(true)
            .setSupportLuminanceInvert(true)
        return MultiFormatAnalyzer(decodeConfig)
    }

    override fun getLayoutId(): Int {
        return super.getLayoutId()
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false)
        val content = result.result.text.orEmpty()
        if (PaymentQrRouter.openIfPaymentQr(this, content)) {
            paymentAppOpened = true
            return
        }
        resultLauncher.launch(ScanResultActivity.createIntent(this, content))
    }
}
