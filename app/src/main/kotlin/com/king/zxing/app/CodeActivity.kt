/*
 * Copyright (C) 2018 Jenly Yu
 * Copyright (C) 2026 Lanlan13-14
 *
 * Licensed under the GNU General Public License, Version 3.
 */
package com.king.zxing.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.king.zxing.util.CodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 生成二维码 / 条形码。
 *
 * 用户可输入任意内容，点「生成」后编码为图片；二维码中心使用 ZXingX logo。
 */
class CodeActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvBarcodeFormat: TextView
    private lateinit var tvEncoded: TextView
    private lateinit var etContent: EditText
    private lateinit var ivCode: ImageView
    private lateinit var btnGenerate: MaterialButton
    private lateinit var btnShareCode: MaterialButton

    private var isQRCode: Boolean = true
    private var generatedBitmap: Bitmap? = null
    private var lastContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.code_activity)

        ivCode = findViewById(R.id.ivCode)
        tvTitle = findViewById(R.id.tvTitle)
        tvBarcodeFormat = findViewById(R.id.tvBarcodeFormat)
        tvEncoded = findViewById(R.id.tvEncoded)
        etContent = findViewById(R.id.etContent)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnShareCode = findViewById(R.id.btnShareCode)

        tvTitle.text = intent.getStringExtra(MainActivity.KEY_TITLE)
        isQRCode = intent.getBooleanExtra(MainActivity.KEY_IS_QR_CODE, true)

        if (isQRCode) {
            tvBarcodeFormat.text = getString(R.string.generate_format_qr)
            etContent.hint = getString(R.string.generate_input_hint_qr)
            etContent.setText(getString(R.string.generate_sample_qr_content))
            etContent.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            tvBarcodeFormat.text = getString(R.string.generate_format_code128)
            etContent.hint = getString(R.string.generate_input_hint_barcode)
            etContent.setText(getString(R.string.generate_sample_barcode_content))
            etContent.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        etContent.setSelection(etContent.text?.length ?: 0)

        btnGenerate.setOnClickListener { generateFromInput() }
        btnShareCode.setOnClickListener { shareGeneratedCode() }
        etContent.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                generateFromInput()
                true
            } else {
                false
            }
        }

        // 进入页面先按默认示例生成一版，方便立刻看到效果
        generateFromInput(showEmptyToast = false)
    }

    private fun generateFromInput(showEmptyToast: Boolean = true) {
        val content = etContent.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) {
            if (showEmptyToast) {
                Toast.makeText(this, R.string.generate_empty, Toast.LENGTH_SHORT).show()
            }
            return
        }
        hideKeyboard()
        btnGenerate.isEnabled = false
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                if (isQRCode) {
                    val logo = BitmapFactory.decodeResource(resources, R.drawable.logo)
                    CodeUtils.createQRCode(content, 600, logo)
                } else {
                    CodeUtils.createBarCode(
                        content,
                        BarcodeFormat.CODE_128,
                        800,
                        200,
                        null,
                        true
                    )
                }
            }
            btnGenerate.isEnabled = true
            if (bitmap == null) {
                Toast.makeText(this@CodeActivity, R.string.generate_failed, Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            generatedBitmap = bitmap
            lastContent = content
            ivCode.setImageBitmap(bitmap)
            tvEncoded.text = getString(R.string.generate_encoded_fmt, content)
            btnShareCode.isEnabled = true
        }
    }

    private fun shareGeneratedCode() {
        val bitmap = generatedBitmap ?: return
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { writeBitmapToCache(bitmap) } ?: run {
                Toast.makeText(this@CodeActivity, R.string.generate_share_failed, Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, lastContent)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.generate_share)))
        }
    }

    private fun writeBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val dir = File(cacheDir, "shared_codes").apply { mkdirs() }
            val file = File(dir, if (isQRCode) "zxingx-qr.png" else "zxingx-barcode.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etContent.windowToken, 0)
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.ivLeft -> finish()
        }
    }
}
