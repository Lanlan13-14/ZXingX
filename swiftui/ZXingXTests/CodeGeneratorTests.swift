//
//  CodeGeneratorTests.swift
//  ZXingX (iOS)
//
//  Generation smoke tests + the ported adaptive-ECC chain
//  (CodeUtils.adaptiveErrorCorrectionLevels) byte-length boundaries.
//

import XCTest
@testable import ZXingX

final class CodeGeneratorTests: XCTestCase {

    // MARK: - adaptiveLevels chain (port of CodeUtils)

    func testAdaptiveLevelsShortWithLogo() {
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 0, hasLogo: true), ["H", "Q", "M", "L"])
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 80, hasLogo: true), ["H", "Q", "M", "L"])
    }

    func testAdaptiveLevelsShortNoLogo() {
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 1, hasLogo: false), ["M", "L", "Q", "H"])
    }

    func testAdaptiveLevelsMedium() {
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 81, hasLogo: true), ["Q", "M", "L", "H"])
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 400, hasLogo: false), ["Q", "M", "L", "H"])
    }

    func testAdaptiveLevelsLong() {
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 401, hasLogo: true), ["M", "L", "Q"])
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 1200, hasLogo: false), ["M", "L", "Q"])
    }

    func testAdaptiveLevelsNearCapacity() {
        XCTAssertEqual(CodeGenerator.adaptiveLevels(bytes: 1201, hasLogo: true), ["L", "M"])
    }

    // MARK: - QR generation

    func testMakeQRCodeShortContentWithLogo() {
        let logo = UIImage(systemName: "qrcode") // any non-nil image exercises the logo path
        let image = CodeGenerator.makeQRCode(content: "hello", logo: logo)
        XCTAssertNotNil(image)
        XCTAssertGreaterThan(image?.size.width ?? 0, 0)
    }

    func testMakeQRCodeEmptyFails() {
        XCTAssertNil(CodeGenerator.makeQRCode(content: "", logo: nil))
    }

    func testMakeQRCodeLongUTF8Content() {
        // ~600 UTF-8 bytes: exercises the mid-tier canvas + ECC fallback.
        let content = String(repeating: " ZXingX中文", count: 60)
        XCTAssertNotNil(CodeGenerator.makeQRCode(content: content, logo: nil))
    }

    // MARK: - CODE_128 generation

    func testMakeCode128ASCII() {
        let image = CodeGenerator.makeCode128(content: "1234567890")
        XCTAssertNotNil(image)
        XCTAssertEqual(image?.size.width, 600) // 10 chars * 14 clamped to ≥600
    }

    func testMakeCode128RejectsNonASCII() {
        // CODE_128 is ASCII-only on both platforms → nil → failure toast.
        XCTAssertNil(CodeGenerator.makeCode128(content: "中文内容"))
    }

    func testMakeCode128EmptyFails() {
        XCTAssertNil(CodeGenerator.makeCode128(content: ""))
    }
}
