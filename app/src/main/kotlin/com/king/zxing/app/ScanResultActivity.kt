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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        // Default result for system back / predictive back. Do not intercept the gesture.
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

        btnClose.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnConfirm.setOnClickListener { finishWithOk() }
        btnCopy.setOnClickListener { copyResult() }
        btnShare.setOnClickListener { shareResult() }
        btnOpen.setOnClickListener { openResult() }
    }

    private fun finishWithOk() {
        setResult(RESULT_OK)
        finish()
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
