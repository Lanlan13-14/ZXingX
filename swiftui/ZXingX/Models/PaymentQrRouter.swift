//
//  PaymentQrRouter.swift
//  ZXingX (iOS)
//
//  iOS counterpart of Android PaymentQrRouter.kt.
//  Same contract: only the QR *content* decides which app family it belongs to;
//  we chain-attempt the payment app's own scan entry and NEVER hand the QR
//  payload to the payment app. Returns false when nothing can be opened so the
//  caller keeps the normal result page (Android: ActivityNotFoundException).
//
//  iOS notes:
//  - `canOpenURL` requires the schemes in Info.plist LSApplicationQueriesSchemes.
//  - WeChat: weixin://scanqrcode opens WeChat's scanner directly; dl/scan and
//    the bare scheme are fallbacks (mirrors the Android ComponentName chain).
//  - Alipay: saId=10000007 is the scan entry on both platforms; iOS scheme is
//    alipayqr:// (alipays:// kept for completeness).
//

import UIKit

enum PaymentQrRouter {

    @MainActor
    static func openIfPaymentQr(_ raw: String?) -> Bool {
        guard let provider = PaymentQrClassifier.classify(raw) else { return false }
        switch provider {
        case .wechat:
            return openFirst([
                "weixin://scanqrcode",
                "weixin://dl/scan",
                "weixin://"
            ])
        case .alipay:
            return openFirst([
                "alipayqr://platformapi/startapp?saId=10000007",
                "alipays://platformapi/startapp?saId=10000007",
                "alipay://"
            ])
        case .paypal:
            return openFirst([
                "paypal://qrcode_scan",
                "paypal://qrcode",
                "paypal://scan",
                "paypal://"
            ])
        }
    }

    @MainActor
    private static func openFirst(_ candidates: [String]) -> Bool {
        let app = UIApplication.shared
        for candidate in candidates {
            guard let url = URL(string: candidate), app.canOpenURL(url) else { continue }
            app.open(url, options: [:], completionHandler: nil)
            return true
        }
        return false
    }
}
