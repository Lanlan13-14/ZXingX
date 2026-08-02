#!/usr/bin/env python3
"""Execute ScanResultUtils semantics without Android/Gradle.

Mirrors app/src/main/kotlin/.../ScanResultUtils.kt so logic regressions
are caught in this sandbox (no Android SDK / Kotlin compiler required).
"""

from __future__ import annotations

import re
import sys
from enum import Enum

WEB_URL = re.compile(
    r"https?://[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:/~+#]*[\w\-@?^=%&/~+#])?",
    re.IGNORECASE,
)


class ContentType(Enum):
    URL = "URL"
    TEXT = "TEXT"


def is_http_url(text: str | None) -> bool:
    if text is None or not str(text).strip():
        return False
    value = text.strip()
    lower = value.lower()
    if not (lower.startswith("http://") or lower.startswith("https://")):
        return False
    return WEB_URL.fullmatch(value) is not None


def content_type_label_key(text: str | None) -> ContentType:
    return ContentType.URL if is_http_url(text) else ContentType.TEXT


def display_text(text: str | None, empty_fallback: str) -> str:
    if text is None or not str(text).strip():
        return empty_fallback
    return text


def run() -> int:
    tests = []

    def check(name: str, cond: bool) -> None:
        tests.append((name, cond))
        print(("PASS" if cond else "FAIL"), name)

    check("https url", is_http_url("https://github.com/Lanlan13-14/ZXingLite"))
    check("http url", is_http_url("http://example.com/path?q=1"))
    check("trim+case url", is_http_url("  HTTPS://Example.COM/a  "))
    check("null", not is_http_url(None))
    check("empty", not is_http_url(""))
    check("spaces", not is_http_url("   "))
    check("plain text", not is_http_url("hello world"))
    check("ftp", not is_http_url("ftp://example.com"))
    check("www only", not is_http_url("www.example.com"))
    check("javascript", not is_http_url("javascript:alert(1)"))
    check("type url", content_type_label_key("https://a.com") is ContentType.URL)
    check("type text", content_type_label_key("纯文本结果") is ContentType.TEXT)
    check("display null", display_text(None, "空") == "空")
    check("display blank", display_text("  ", "空") == "空")
    check("display value", display_text("内容", "空") == "内容")

    failed = sum(1 for _, ok in tests if not ok)
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(run())
