//
//  ScanMode.swift
//  ZXingX (iOS)
//
//  The three scanner flavors, mirroring the Android activities:
//  - continuous  ← MultiFormatScanActivity  (multi-format, stays alive after each result)
//  - qrBox       ← QRCodeScanActivity       (QR only, boxed viewfinder, grid laser)
//  - qrFullScreen← FullScreenQRCodeScanActivity (QR anywhere, corner-frame style)
//

import AVFoundation

enum ScanMode: Hashable {
    case continuous
    case qrBox
    case qrFullScreen

    /// Decode formats per mode.
    ///
    /// Android (ZXing MultiFormatAnalyzer, DEFAULT_HINTS) vs iOS AVFoundation:
    /// supported natively: QR, Aztec, DataMatrix, PDF417, Code 39/93/128,
    /// EAN-8, EAN-13, UPC-E, ITF/Interleaved 2 of 5, GS1 DataBar.
    /// NOT available on iOS: Codabar, MaxiCode, UPC-EAN-extension.
    /// UPC-A codes are reported by AVFoundation as EAN-13 (leading 0),
    /// same as ZXing's UPC/EAN reader behavior hints.
    var metadataObjectTypes: [AVMetadataObject.ObjectType] {
        switch self {
        case .continuous:
            return [
                .qr, .aztec, .dataMatrix, .pdf417,
                .code39, .code93, .code128,
                .ean8, .ean13, .upce,
                .itf14, .interleaved2of5,
                .gs1DataBar, .gs1DataBarLimited, .gs1DataBarExpanded
            ]
        case .qrBox, .qrFullScreen:
            return [.qr]
        }
    }

    var isContinuous: Bool {
        if case .continuous = self { return true }
        return false
    }

    /// DecodeConfig.areaRectRatio (0.8) — analysis crop as a centered square
    /// whose side is this fraction of the preview's short edge.
    /// nil = full-area scan (qrFullScreen: DecodeConfig.setFullAreaScan(true)).
    var analysisAreaRatio: CGFloat? {
        switch self {
        case .qrBox: return 0.8
        case .continuous, .qrFullScreen: return nil
        }
    }
}
