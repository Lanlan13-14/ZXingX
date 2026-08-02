package com.king.zxing.app

import android.content.Intent
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.zxing.Camera2BarcodeScanActivity
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.analyze.MultiFormatAnalyzer


/**
 * 扫二维码识别示例
 *
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * <p>
 * <a href="https://github.com/jenly1314">Follow me</a>
 */
class QRCodeScanActivity : Camera2BarcodeScanActivity() {

    override fun createAnalyzer(): com.king.zxing.analyze.ImageAnalyzer {
        //初始化解码配置
        val decodeConfig = DecodeConfig()
        decodeConfig.setHints(DecodeFormatManager.QR_CODE_HINTS) // 如果只有识别二维码的需求，这样设置效率会更高，不设置默认为DecodeFormatManager.DEFAULT_HINTS
            .setFullAreaScan(false) // 设置是否全区域识别，默认false
            .setAreaRectRatio(0.8f) // 设置识别区域比例，默认0.8，设置的比例最终会在预览区域裁剪基于此比例的一个矩形进行扫码识别
            .setAreaRectVerticalOffset(0) // 设置识别区域垂直方向偏移量，默认为0，为0表示居中，可以为负数
            .setAreaRectHorizontalOffset(0) // 设置识别区域水平方向偏移量，默认为0，为0表示居中，可以为负数

        // BarcodeCameraScanActivity默认使用的MultiFormatAnalyzer，这里也可以改为使用QRCodeAnalyzer
        return MultiFormatAnalyzer(decodeConfig)
    }

    /**
     * 布局ID；通过覆写此方法可以自定义布局
     *
     * @return 布局ID
     */
    override fun getLayoutId(): Int {
        return R.layout.activity_qrcode_scan
    }

    override fun onScanResult(result: AnalyzeResult<Result>) {
        setAnalyzeImage(false)
        val intent = Intent()
        intent.putExtra(com.king.camera.scan.CameraScan.SCAN_RESULT, result.result.text)
        setResult(RESULT_OK, intent)
        finish()
    }
}
