package com.king.zxing.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object PaymentQrRouter {
    private const val WECHAT = "com.tencent.mm"
    private const val ALIPAY = "com.eg.android.AlipayGphone"
    private const val PAYPAL = "com.paypal.android.p2pmobile"

    fun openIfPaymentQr(activity: Activity, raw: String?): Boolean {
        return when (PaymentQrClassifier.classify(raw)) {
            PaymentQrClassifier.Provider.WECHAT -> openWeChatScanner(activity)
            PaymentQrClassifier.Provider.ALIPAY -> openAlipayScanner(activity)
            PaymentQrClassifier.Provider.PAYPAL -> openPayPalScanner(activity)
            null -> false
        }
    }

    private fun openWeChatScanner(activity: Activity): Boolean {
        val intents = listOf(
            Intent().setComponent(
                ComponentName(WECHAT, "com.tencent.mm.plugin.scanner.ui.BaseScanUI")
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            Intent().setComponent(ComponentName(WECHAT, "com.tencent.mm.ui.LauncherUI"))
                .putExtra("LauncherUI.From.Scaner.Shortcut", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            Intent(Intent.ACTION_VIEW, Uri.parse("weixin://scanqrcode")).setPackage(WECHAT),
            Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/scan")).setPackage(WECHAT)
        )
        return intents.any { launch(activity, it) }
    }

    private fun openAlipayScanner(activity: Activity): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("alipays://platformapi/startapp?saId=10000007")
        ).setPackage(ALIPAY)
        return launch(activity, intent)
    }

    private fun openPayPalScanner(activity: Activity): Boolean {
        val shortcuts = listOf("paypal://qrcode_scan", "paypal://qrcode", "paypal://scan")
        return shortcuts.any { uri ->
            launch(activity, Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(PAYPAL))
        }
    }

    private fun launch(activity: Activity, intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
