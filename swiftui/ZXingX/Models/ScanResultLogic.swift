//
//  ScanResultLogic.swift
//  ZXingX (iOS)
//
//  Port of Android ScanResultUtils.kt — pure helpers for scan-result
//  presentation. Kept free of UIKit types so unit tests run without a device.
//

import Foundation

enum ScanResultLogic {

    enum ContentType {
        case url
        case text
    }

    /// Kotlin source pattern (CASE_INSENSITIVE, full-string matches()):
    ///   https?://[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:/~+#]*[\w\-@?^=%&/~+#])?
    /// Java/ICU default `\w` is ASCII [A-Za-z0-9_]; NSRegularExpression default
    /// matches the same set (no UREGEX_UWORD flag), so behavior is identical.
    /// Anchored with ^...$ to reproduce Matcher.matches() semantics.
    private static let webURLRegex: NSRegularExpression = {
        // swiftlint:disable:next force_try
        try! NSRegularExpression(
            pattern: #"^https?://[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:/~+#]*[\w\-@?^=%&/~+#])?$"#,
            options: [.caseInsensitive]
        )
    }()

    static func isHttpURL(_ text: String?) -> Bool {
        guard let raw = text else { return false }
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty { return false }
        let lower = value.lowercased()
        guard lower.hasPrefix("http://") || lower.hasPrefix("https://") else {
            return false
        }
        let range = NSRange(value.startIndex..., in: value)
        return webURLRegex.firstMatch(in: value, options: [], range: range) != nil
    }

    static func contentType(for text: String?) -> ContentType {
        isHttpURL(text) ? .url : .text
    }

    static func displayText(_ text: String?, emptyFallback: String) -> String {
        // Kotlin isNullOrBlank(): null, empty, or whitespace-only.
        guard let text = text,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return emptyFallback
        }
        return text
    }
}
