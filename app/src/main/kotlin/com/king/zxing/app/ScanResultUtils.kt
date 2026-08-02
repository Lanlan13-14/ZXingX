package com.king.zxing.app

import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure helpers for scan-result presentation. Kept free of Android framework
 * types (except none) so unit tests can run on the JVM.
 */
object ScanResultUtils {

    private val WEB_URL: Pattern = Pattern.compile(
        "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?",
        Pattern.CASE_INSENSITIVE
    )

    fun isHttpUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val value = text.trim()
        val lower = value.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false
        }
        return WEB_URL.matcher(value).matches()
    }

    fun contentTypeLabelKey(text: String?): ContentType {
        return if (isHttpUrl(text)) ContentType.URL else ContentType.TEXT
    }

    fun displayText(text: String?, emptyFallback: String): String {
        return if (text.isNullOrBlank()) emptyFallback else text
    }

    enum class ContentType {
        URL,
        TEXT
    }
}
