//
//  CodeGenerator.swift
//  ZXingX (iOS)
//
//  iOS counterpart of zxing-lite CodeUtils (createQRCode / createBarCode).
//  Uses the platform-standard generators:
//  - QR:      CIFilter "CIQRCodeGenerator"   (correction level chain below)
//  - CODE_128: CIFilter "CICode128BarcodeGenerator" (ASCII only, like ZXing's
//             ISO-8859-1 Code128Writer — non-Latin input fails → caller toast)
//
//  The adaptive ECC fallback chain is ported 1:1 from
//  CodeUtils.adaptiveErrorCorrectionLevels (keyed on UTF-8 byte length):
//    bytes ≤ 80:   logo → H Q M L ; no logo → M L Q H
//    bytes ≤ 400:  Q M L H
//    bytes ≤ 1200: M L Q
//    else:         L M            (near version-40 capacity limits)
//  Logo ratios per level: [r.clamped(0.08...0.25), max(r*0.7, 0.08), no-logo].
//  ZXing MARGIN hint = 1 module → we composite a 1-module quiet zone manually.
//

import CoreImage
import UIKit

enum CodeGenerator {

    // MARK: - QR Code

    static func makeQRCode(content: String, logo: UIImage?) -> UIImage? {
        guard !content.isEmpty else { return nil }
        let bytes = content.utf8.count
        // CodeActivity.createRobustQRCode canvas tiers.
        let size: Int
        let ratio: CGFloat
        switch bytes {
        case ...120: size = 720
        case 121...400: size = 860
        case 401...900: size = 1000
        default: size = 1200
        }
        switch bytes {
        case ...80: ratio = 0.18
        case 81...300: ratio = 0.14
        case 301...800: ratio = 0.10
        default: ratio = 0.08
        }

        let levels = adaptiveLevels(bytes: bytes, hasLogo: logo != nil)
        let ratios: [CGFloat] = logo != nil
            ? [min(max(ratio, 0.08), 0.25), max(ratio * 0.7, 0.08), 0]
            : [0]

        for level in levels {
            guard let raw = qrMatrixImage(content: content, pixelSize: size, level: level) else {
                continue
            }
            if let logo {
                for r in ratios {
                    if r <= 0 { return raw }
                    if let stamped = addLogo(logo, to: raw, ratio: r) {
                        return stamped
                    }
                }
                return raw
            }
            return raw
        }
        // CodeActivity final fallback: smaller canvas, no logo.
        for level in adaptiveLevels(bytes: bytes, hasLogo: false) {
            if let raw = qrMatrixImage(content: content, pixelSize: 800, level: level) {
                return raw
            }
        }
        return nil
    }

    /// 1:1 port of CodeUtils.adaptiveErrorCorrectionLevels.
    static func adaptiveLevels(bytes: Int, hasLogo: Bool) -> [String] {
        switch bytes {
        case ...80:
            return hasLogo ? ["H", "Q", "M", "L"] : ["M", "L", "Q", "H"]
        case 81...400:
            return ["Q", "M", "L", "H"]
        case 401...1200:
            return ["M", "L", "Q"]
        default:
            return ["L", "M"]
        }
    }

    /// Renders the QR matrix scaled to `pixelSize`, then composites it on a
    /// white canvas with a 1-module quiet zone (ZXing MARGIN hint = 1).
    private static func qrMatrixImage(content: String, pixelSize: Int, level: String) -> UIImage? {
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setDefaults()
        filter.setValue(Data(content.utf8), forKey: "inputMessage")
        filter.setValue(level, forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage, output.extent.width > 0 else { return nil }

        let modules = output.extent.width // CIQRCodeGenerator emits 1 pt per module
        let scale = CGFloat(pixelSize) / modules
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let canvasSide = pixelSize + Int(ceil(scale)) * 2 // 1 module margin each side
        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(
            size: CGSize(width: canvasSide, height: canvasSide),
            format: format
        )
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: canvasSide, height: canvasSide))
            let ciContext = CIContext()
            if let cg = ciContext.createCGImage(scaled, from: scaled.extent) {
                ctx.cgContext.interpolationQuality = .none
                let margin = CGFloat(canvasSide - pixelSize) / 2
                // NB: CGContext.draw(_:in:) in a UIKit context renders CGImages
                // vertically flipped (classic gotcha) — a reflected QR is not a
                // valid orientation for standard decoders. UIImage.draw(in:)
                // applies the correct orientation.
                UIImage(cgImage: cg).draw(
                    in: CGRect(x: margin, y: margin, width: CGFloat(pixelSize), height: CGFloat(pixelSize))
                )
            }
        }
    }

    /// CodeUtils.addLogo: logo scaled to `ratio` of the QR width, centered,
    /// on a white rounded pad so the modules under it stay readable.
    private static func addLogo(_ logo: UIImage, to qr: UIImage, ratio: CGFloat) -> UIImage? {
        let side = qr.size.width
        guard side > 0 else { return nil }
        let logoSide = floor(side * ratio)
        guard logoSide >= 8 else { return qr } // too small to stamp; keep raw QR
        let pad = logoSide * 0.16

        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: qr.size, format: format)
        return renderer.image { ctx in
            qr.draw(in: CGRect(origin: .zero, size: qr.size))
            let rect = CGRect(
                x: (side - logoSide) / 2,
                y: (side - logoSide) / 2,
                width: logoSide,
                height: logoSide
            )
            let padRect = rect.insetBy(dx: -pad, dy: -pad)
            UIColor.white.setFill()
            UIBezierPath(roundedRect: padRect, cornerRadius: padRect.width * 0.22).fill()
            logo.draw(in: rect)
        }
    }

    // MARK: - CODE_128 Barcode

    /// CodeActivity.createRobustBarCode:
    /// width = (content.length * 14).coerceIn(600, 1600); first with the
    /// human-readable text under the bars (height 220 + text area), fallback
    /// bars-only (height 200). CODE_128 rejects non-ASCII → nil → toast.
    static func makeCode128(content: String) -> UIImage? {
        guard !content.isEmpty else { return nil }
        // Kotlin length = UTF-16 units; NSString bridges the same count.
        let length = (content as NSString).length
        let width = min(max(length * 14, 600), 1600)
        if let withText = code128Image(content: content, width: width, barHeight: 220, showText: true) {
            return withText
        }
        return code128Image(content: content, width: width, barHeight: 200, showText: false)
    }

    private static func code128Image(content: String, width: Int, barHeight: Int, showText: Bool) -> UIImage? {
        guard let filter = CIFilter(name: "CICode128BarcodeGenerator") else { return nil }
        filter.setDefaults()
        // CICode128BarcodeGenerator expects 7-bit ASCII; utf8 would inject
        // multi-byte garbage, so gate on canLosslesslyConvert first.
        guard let data = content.data(using: .ascii) else { return nil }
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue(NSNumber(value: 20), forKey: "inputQuietSpace")
        guard let output = filter.outputImage, output.extent.width > 0 else { return nil }

        // CodeUtils.addCode: canvas = srcHeight + textSize + offset*2,
        // textSize 40, offset 20 → text strip 40 pt starting 20 below bars.
        let textSize: CGFloat = 40
        let offset: CGFloat = 20
        let canvasHeight = showText
            ? CGFloat(barHeight) + textSize + offset * 2
            : CGFloat(barHeight)

        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(
            size: CGSize(width: width, height: canvasHeight),
            format: format
        )
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: canvasHeight))
            let ciContext = CIContext()
            let scaled = output.transformed(by: CGAffineTransform(
                scaleX: CGFloat(width) / output.extent.width,
                y: CGFloat(barHeight) / output.extent.height
            ))
            if let cg = ciContext.createCGImage(scaled, from: scaled.extent) {
                ctx.cgContext.interpolationQuality = .none
                // UIImage.draw(in:) — CGContext.draw would flip the bars vertically.
                UIImage(cgImage: cg).draw(
                    in: CGRect(x: 0, y: 0, width: width, height: barHeight)
                )
            }
            if showText {
                let paragraph = NSMutableParagraphStyle()
                paragraph.alignment = .center
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: textSize),
                    .foregroundColor: UIColor.black,
                    .paragraphStyle: paragraph
                ]
                (content as NSString).draw(
                    in: CGRect(x: 0, y: CGFloat(barHeight) + offset, width: CGFloat(width), height: textSize),
                    withAttributes: attributes
                )
            }
        }
    }
}
