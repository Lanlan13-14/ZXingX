package com.king.zxing.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

object PaymentQrRouter {
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    private const val PAYPAL_PACKAGE = "com.paypal.android.p2pmobile"

    /** Returns true only after an intent was successfully handed to the target app. */
    fun openIfPaymentQr(activity: Activity, raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        val provider = PaymentQrClassifier.classify(value) ?: return false
        val intent = when (provider) {
            PaymentQrClassifier.Provider.WECHAT -> packageViewIntent(value, WECHAT_PACKAGE)
            PaymentQrClassifier.Provider.ALIPAY -> alipayIntent(value)
            PaymentQrClassifier.Provider.PAYPAL -> packageViewIntent(value, PAYPAL_PACKAGE)
        }
        return launch(activity, intent)
    }

    private fun packageViewIntent(value: String, packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(value)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun alipayIntent(value: String): Intent {
        val encoded = Uri.encode(value)
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("alipays://platformapi/startapp?saId=10000007&qrcode=$encoded")
        ).apply {
            setPackage(ALIPAY_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun launch(activity: Activity, intent: Intent): Boolean {
        return try {
            // Do not depend only on resolveActivity; package visibility and OEM wrappers vary.
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
