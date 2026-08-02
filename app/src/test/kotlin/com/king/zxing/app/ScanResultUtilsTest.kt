package com.king.zxing.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResultUtilsTest {

    @Test
    fun isHttpUrl_acceptsValidHttpAndHttps() {
        assertTrue(ScanResultUtils.isHttpUrl("https://github.com/Lanlan13-14/ZXingLite"))
        assertTrue(ScanResultUtils.isHttpUrl("http://example.com/path?q=1"))
        assertTrue(ScanResultUtils.isHttpUrl("  HTTPS://Example.COM/a  "))
    }

    @Test
    fun isHttpUrl_rejectsNonUrls() {
        assertFalse(ScanResultUtils.isHttpUrl(null))
        assertFalse(ScanResultUtils.isHttpUrl(""))
        assertFalse(ScanResultUtils.isHttpUrl("   "))
        assertFalse(ScanResultUtils.isHttpUrl("hello world"))
        assertFalse(ScanResultUtils.isHttpUrl("ftp://example.com"))
        assertFalse(ScanResultUtils.isHttpUrl("www.example.com"))
        assertFalse(ScanResultUtils.isHttpUrl("javascript:alert(1)"))
    }

    @Test
    fun contentTypeLabelKey_mapsUrlAndText() {
        assertEquals(
            ScanResultUtils.ContentType.URL,
            ScanResultUtils.contentTypeLabelKey("https://a.com")
        )
        assertEquals(
            ScanResultUtils.ContentType.TEXT,
            ScanResultUtils.contentTypeLabelKey("纯文本结果")
        )
    }

    @Test
    fun displayText_usesFallbackWhenBlank() {
        assertEquals("空", ScanResultUtils.displayText(null, "空"))
        assertEquals("空", ScanResultUtils.displayText("  ", "空"))
        assertEquals("内容", ScanResultUtils.displayText("内容", "空"))
    }
}
