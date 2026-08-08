//
//  PaymentQrClassifier.swift
//  ZXingX (iOS)
//
//  Port of Android PaymentQrClassifier.kt — strict payment-QR classification.
//  Normal web URLs must remain normal results.
//

import Foundation

enum PaymentQrClassifier {

    enum Provider {
        case wechat
        case alipay
        case paypal
    }

    static func classify(_ raw: String?) -> Provider? {
        let value = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty { return nil }
        let lower = value.lowercased()
        if lower.hasPrefix("wxp://") || lower.hasPrefix("weixin://wxpay/") {
            return .wechat
        }
        // java.net.URI(value) throws on malformed input → null; URLComponents
        // is the closest Foundation equivalent (scheme/host/path extraction).
        guard let components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased() else {
            return nil
        }
        let path = components.path
        if (scheme == "http" || scheme == "https"),
           host == "qr.alipay.com", path.count > 1 {
            return .alipay
        }
        if (scheme == "http" || scheme == "https"),
           isPayPalHost(host), isPayPalPaymentPath(host: host, path: path) {
            return .paypal
        }
        return nil
    }

    private static func isPayPalHost(_ host: String) -> Bool {
        host == "paypal.me" || host == "www.paypal.me" ||
            host == "paypal.com" || host == "www.paypal.com"
    }

    private static func isPayPalPaymentPath(host: String, path: String) -> Bool {
        switch host {
        case "paypal.me", "www.paypal.me":
            return path.count > 1
        default:
            return path.lowercased().hasPrefix("/qrcodes/p2pqrc/")
        }
    }
}
