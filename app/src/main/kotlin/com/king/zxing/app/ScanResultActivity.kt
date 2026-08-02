package com.king.zxing.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.king.zxing.gesture.EdgeSwipeBackController

/**
 * 扫描结果页：替代 Toast 小消息提示。
 *
 * 顶部：关闭 · 标题 · 确认；正文展示完整扫描内容；底部提供复制/分享/打开。
 */
class ScanResultActivity : AppCompatActivity() {

    private lateinit var tvResult: TextView
    private lateinit var tvTypeChip: TextView
    private lateinit var btnOpen: MaterialButton

    private var resultText: String = ""
    private lateinit var swipeBack: EdgeSwipeBackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)
        swipeBack = EdgeSwipeBackController.install(this)

        // Default result for any cancelled/committed back path.
        setResult(RESULT_CANCELED)
        resultText = intent.getStringExtra(EXTRA_RESULT).orEmpty()

        tvResult = findViewById(R.id.tvResult)
        tvTypeChip = findViewById(R.id.tvTypeChip)
        btnOpen = findViewById(R.id.btnOpen)

        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        val btnConfirm = findViewById<ImageButton>(R.id.btnConfirm)
        val btnCopy = findViewById<MaterialButton>(R.id.btnCopy)
        val btnShare = findViewById<MaterialButton>(R.id.btnShare)

        tvResult.text = ScanResultUtils.displayText(
            resultText,
            getString(R.string.scan_result_empty)
        )

        val isUrl = ScanResultUtils.isHttpUrl(resultText)
        tvTypeChip.text = when (ScanResultUtils.contentTypeLabelKey(resultText)) {
            ScanResultUtils.ContentType.URL -> getString(R.string.scan_result_type_url)
            ScanResultUtils.ContentType.TEXT -> getString(R.string.scan_result_type_text)
        }
        btnOpen.isVisible = isUrl

        btnClose.setOnClickListener { swipeBack.requestBack() }
        btnConfirm.setOnClickListener { finishWithOk() }
        btnCopy.setOnClickListener { copyResult() }
        btnShare.setOnClickListener { shareResult() }
        btnOpen.setOnClickListener { openResult() }
    }

    private fun finishWithOk() {
        setResult(RESULT_OK)
        swipeBack.requestBack()
    }

    private fun copyResult() {
        if (resultText.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("scan_result", resultText))
        Toast.makeText(this, R.string.scan_result_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareResult() {
        if (resultText.isBlank()) return
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, resultText)
        }
        startActivity(Intent.createChooser(share, getString(R.string.scan_result_share)))
    }

    private fun openResult() {
        if (!ScanResultUtils.isHttpUrl(resultText)) return
        val uri = Uri.parse(resultText.trim())
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    companion object {
        const val EXTRA_RESULT = "extra_scan_result"

        fun createIntent(activity: android.app.Activity, result: String?): Intent {
            return Intent(activity, ScanResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result.orEmpty())
            }
        }
    }
}
