/*
 * Copyright (C) 2018 Jenly Yu
 * Copyright (C) 2026 Lanlan13-14
 *
 * Licensed under the GNU General Public License, Version 3.
 */
package com.king.zxing.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.king.zxing.util.CodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 生成二维码 / 条形码演示页。
 *
 * 展示库的「编码」能力：把字符串画成可扫描的图。
 * 二维码中心使用 ZXingX logo，而不是上游 Lite 标识。
 */
class CodeActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvBarcodeFormat: TextView
    private lateinit var tvContent: TextView
    private lateinit var ivCode: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.code_activity)
        ivCode = findViewById(R.id.ivCode)
        tvTitle = findViewById(R.id.tvTitle)
        tvBarcodeFormat = findViewById(R.id.tvBarcodeFormat)
        tvContent = findViewById(R.id.tvContent)
        tvTitle.text = intent.getStringExtra(MainActivity.KEY_TITLE)
        val isQRCode = intent.getBooleanExtra(MainActivity.KEY_IS_QR_CODE, false)

        if (isQRCode) {
            val content = getString(R.string.generate_sample_qr_content)
            tvBarcodeFormat.text = getString(R.string.generate_format_qr)
            tvContent.text = content
            createQRCode(content)
        } else {
            val content = getString(R.string.generate_sample_barcode_content)
            tvBarcodeFormat.text = getString(R.string.generate_format_code128)
            tvContent.text = content
            createBarCode(content)
        }
    }

    private fun createQRCode(content: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                // Center logo is ZXingX branding (app/src/main/res/drawable-xxhdpi/logo.png)
                val logo = BitmapFactory.decodeResource(resources, R.drawable.logo)
                CodeUtils.createQRCode(content, 600, logo)
            }
            ivCode.setImageBitmap(bitmap)
        }
    }

    private fun createBarCode(content: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                CodeUtils.createBarCode(content, BarcodeFormat.CODE_128, 800, 200, null, true)
            }
            ivCode.setImageBitmap(bitmap)
        }
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.ivLeft -> finish()
        }
    }
}
