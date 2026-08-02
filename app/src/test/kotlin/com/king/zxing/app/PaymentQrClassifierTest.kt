package com.king.zxing.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentQrClassifierTest {
    @Test fun classifiesPaymentQrStrictly() {
        val cases = mapOf(
            "wxp://f2f0abc" to PaymentQrClassifier.Provider.WECHAT,
            "weixin://wxpay/bizpayurl?pr=x" to PaymentQrClassifier.Provider.WECHAT,
            "https://qr.alipay.com/fkx123" to PaymentQrClassifier.Provider.ALIPAY,
            "https://www.paypal.com/qrcodes/p2pqrc/ABC" to PaymentQrClassifier.Provider.PAYPAL,
            "https://paypal.me/user" to PaymentQrClassifier.Provider.PAYPAL,
            "https://evil.com/qr.alipay.com/a" to null,
            "https://example.com" to null,
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, PaymentQrClassifier.classify(input))
        }
    }
}
