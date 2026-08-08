//
//  ImageCodeReader.swift
//  ZXingX (iOS)
//
//  相册识别 — iOS counterpart of CodeUtils.parseCode(bitmap).
//  Uses Vision's VNDetectBarcodesRequest, the platform-standard multi-format
//  image barcode reader (QR, Aztec, DataMatrix, PDF417, Code 39/93/128,
//  EAN-8/13, UPC-E, ITF, GS1 DataBar — no Codabar/MaxiCode on iOS).
//

import UIKit
import Vision

enum ImageCodeReader {

    static func readCode(from image: UIImage) async -> String? {
        guard let cgImage = image.cgImage else { return nil }
        return await withCheckedContinuation { continuation in
            let request = VNDetectBarcodesRequest { request, _ in
                let text = (request.results as? [VNBarcodeObservation])?
                    .compactMap(\.payloadStringValue)
                    .first
                continuation.resume(returning: text)
            }
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(returning: nil)
            }
        }
    }
}
