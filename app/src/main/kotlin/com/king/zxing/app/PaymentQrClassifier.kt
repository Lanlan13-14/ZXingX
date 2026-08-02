package com.king.zxing.app

import java.net.URI
import java.util.Locale

/** Strict payment-QR classification; normal web URLs must remain normal results. */
object PaymentQrClassifier {
    enum class Provider { WECHAT, ALIPAY, PAYPAL }

    fun classify(raw: String?): Provider? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val lower = value.lowercase(Locale.ROOT)
        if (lower.startsWith("wxp://") || lower.startsWith("weixin://wxpay/")) {
            return Provider.WECHAT
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path.orEmpty()
        if (scheme in setOf("http", "https") && host == "qr.alipay.com" && path.length > 1) {
            return Provider.ALIPAY
        }
        if (scheme in setOf("http", "https") && isPayPalHost(host) && isPayPalPaymentPath(host, path)) {
            return Provider.PAYPAL
        }
        return null
    }

    private fun isPayPalHost(host: String): Boolean =
        host == "paypal.me" || host == "www.paypal.me" ||
            host == "paypal.com" || host == "www.paypal.com"

    private fun isPayPalPaymentPath(host: String, path: String): Boolean = when (host) {
        "paypal.me", "www.paypal.me" -> path.length > 1
        else -> path.startsWith("/qrcodes/p2pqrc/", ignoreCase = true)
    }
}
