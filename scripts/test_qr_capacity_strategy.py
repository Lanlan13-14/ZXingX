#!/usr/bin/env python3
"""Document/verify QR encode strategy used by CodeUtils + CodeActivity."""

from __future__ import annotations


def ecc_levels(byte_len: int, has_logo: bool) -> list[str]:
    if byte_len <= 80:
        return (
            ["H", "Q", "M", "L"]
            if has_logo
            else ["M", "L", "Q", "H"]
        )
    if byte_len <= 400:
        return ["Q", "M", "L", "H"]
    if byte_len <= 1200:
        return ["M", "L", "Q"]
    return ["L", "M"]


def qr_size(byte_len: int) -> int:
    if byte_len <= 120:
        return 720
    if byte_len <= 400:
        return 860
    if byte_len <= 900:
        return 1000
    return 1200


def logo_ratio(byte_len: int) -> float:
    if byte_len <= 80:
        return 0.18
    if byte_len <= 300:
        return 0.14
    if byte_len <= 800:
        return 0.10
    return 0.08


def main() -> int:
    cases = [
        (20, True, ["H", "Q", "M", "L"], 720, 0.18),
        (200, True, ["Q", "M", "L", "H"], 860, 0.14),
        (600, True, ["M", "L", "Q"], 1000, 0.10),
        (1500, True, ["L", "M"], 1200, 0.08),
        (50, False, ["M", "L", "Q", "H"], 720, 0.18),
    ]
    failed = 0
    for byte_len, has_logo, exp_levels, exp_size, exp_ratio in cases:
        got_levels = ecc_levels(byte_len, has_logo)
        got_size = qr_size(byte_len)
        got_ratio = logo_ratio(byte_len)
        ok = got_levels == exp_levels and got_size == exp_size and abs(got_ratio - exp_ratio) < 1e-6
        print(("PASS" if ok else "FAIL"), f"bytes={byte_len}", got_levels, got_size, got_ratio)
        if not ok:
            failed += 1
            print("  expected", exp_levels, exp_size, exp_ratio)
    # UTF-8 Chinese multi-byte awareness note
    text = "测" * 100
    utf8_len = len(text.encode("utf-8"))
    assert utf8_len == 300
    print("PASS utf8 chinese 100 chars ->", utf8_len, "bytes", ecc_levels(utf8_len, True))
    print(f"\n{len(cases) + 1 - failed}/{len(cases) + 1} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
