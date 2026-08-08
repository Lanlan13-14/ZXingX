//
//  ScanResultLogicTests.swift
//  ZXingX (iOS) — ports ScanResultUtilsTest.kt case-for-case.
//

import XCTest
@testable import ZXingX

final class ScanResultLogicTests: XCTestCase {

    func testIsHttpUrlAcceptsValidHttpAndHttps() {
        XCTAssertTrue(ScanResultLogic.isHttpURL("https://github.com/Lanlan13-14/ZXingLite"))
        XCTAssertTrue(ScanResultLogic.isHttpURL("http://example.com/path?q=1"))
        XCTAssertTrue(ScanResultLogic.isHttpURL("  HTTPS://Example.COM/a  "))
    }

    func testIsHttpUrlRejectsNonUrls() {
        XCTAssertFalse(ScanResultLogic.isHttpURL(nil))
        XCTAssertFalse(ScanResultLogic.isHttpURL(""))
        XCTAssertFalse(ScanResultLogic.isHttpURL("   "))
        XCTAssertFalse(ScanResultLogic.isHttpURL("hello world"))
        XCTAssertFalse(ScanResultLogic.isHttpURL("ftp://example.com"))
        XCTAssertFalse(ScanResultLogic.isHttpURL("www.example.com"))
        XCTAssertFalse(ScanResultLogic.isHttpURL("javascript:alert(1)"))
    }

    func testContentTypeMapsUrlAndText() {
        XCTAssertEqual(ScanResultLogic.contentType(for: "https://a.com"), .url)
        XCTAssertEqual(ScanResultLogic.contentType(for: "纯文本结果"), .text)
    }

    func testDisplayTextUsesFallbackWhenBlank() {
        XCTAssertEqual(ScanResultLogic.displayText(nil, emptyFallback: "空"), "空")
        XCTAssertEqual(ScanResultLogic.displayText("  ", emptyFallback: "空"), "空")
        XCTAssertEqual(ScanResultLogic.displayText("内容", emptyFallback: "空"), "内容")
    }
}
