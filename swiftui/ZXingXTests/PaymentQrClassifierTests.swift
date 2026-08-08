//
//  PaymentQrClassifierTests.swift
//  ZXingX (iOS) — ports PaymentQrClassifierTest.kt case-for-case.
//

import XCTest
@testable import ZXingX

final class PaymentQrClassifierTests: XCTestCase {

    func testClassifiesPaymentQrStrictly() {
        let cases: [(String, PaymentQrClassifier.Provider?)] = [
            ("wxp://f2f0abc", .wechat),
            ("weixin://wxpay/bizpayurl?pr=x", .wechat),
            ("https://qr.alipay.com/fkx123", .alipay),
            ("https://www.paypal.com/qrcodes/p2pqrc/ABC", .paypal),
            ("https://paypal.me/user", .paypal),
            ("https://evil.com/qr.alipay.com/a", nil),
            ("https://example.com", nil)
        ]
        for (input, expected) in cases {
            XCTAssertEqual(
                PaymentQrClassifier.classify(input),
                expected,
                "input: \(input)"
            )
        }
    }

    func testRejectsBlankAndMalformed() {
        XCTAssertNil(PaymentQrClassifier.classify(nil))
        XCTAssertNil(PaymentQrClassifier.classify(""))
        XCTAssertNil(PaymentQrClassifier.classify("   "))
    }
}
