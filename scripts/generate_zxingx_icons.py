#!/usr/bin/env python3
"""Generate ZXingX launcher icons from the design SVG."""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SRC_SVG = ROOT / "design" / "zxingx_icon.svg"
WEB_PNG = ROOT / "app" / "src" / "main" / "ic_launcher-web.png"

# Adaptive icon foreground: 108dp base; content inset so safe zone (~66%) keeps full art.
# We draw full 1024 art into the center 72/108 of the canvas.
FOREGROUND_SVG = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
  <defs>
    <clipPath id="sqClip">
      <path d="M512 24C818 24 1000 206 1000 512S818 1000 512 1000 24 818 24 512 206 24 512 24Z"/>
    </clipPath>
  </defs>
  <!-- transparent outside; white plate only inside rounded square so adaptive mask is clean -->
  <g transform="translate(128,128) scale(0.75)">
    <rect width="1024" height="1024" fill="#ffffff"/>
    <g clip-path="url(#sqClip)">
      <g stroke="#1d1d1f" stroke-width="40" stroke-linecap="round" fill="none">
        <path d="M181 281 V211 a30 30 0 0 1 30-30 h70"/>
        <path d="M843 281 V211 a30 30 0 0 0-30-30 h-70"/>
        <path d="M181 743 V813 a30 30 0 0 0 30 30 h70"/>
        <path d="M843 743 V813 a30 30 0 0 1-30 30 h-70"/>
      </g>
      <g fill="none" stroke-width="32">
        <rect x="276" y="265" width="176" height="176" rx="34" stroke="#1d1d1f"/>
        <rect x="572" y="265" width="176" height="176" rx="34" stroke="#8e8e93"/>
        <rect x="276" y="583" width="176" height="176" rx="34" stroke="#8e8e93"/>
      </g>
      <rect x="340" y="329" width="48" height="48" rx="12" fill="#0A84FF"/>
      <rect x="576" y="587" width="72" height="72" rx="17" fill="#1d1d1f"/>
      <rect x="672" y="587" width="72" height="72" rx="17" fill="#8e8e93"/>
      <rect x="576" y="683" width="72" height="72" rx="17" fill="#8e8e93"/>
      <rect x="672" y="683" width="72" height="72" rx="17" fill="#0A84FF"/>
      <rect x="276" y="505" width="472" height="14" rx="7" fill="#0A84FF"/>
    </g>
  </g>
</svg>
"""

# Legacy launcher (fully filled square with squircle clip already in source)
LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive foreground sizes (108dp * density)
FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def render(svg_path: Path, out_png: Path, size: int) -> None:
    out_png.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "rsvg-convert",
            "-w",
            str(size),
            "-h",
            str(size),
            str(svg_path),
            "-o",
            str(out_png),
        ],
        check=True,
    )


def main() -> None:
    if not SRC_SVG.exists():
        raise SystemExit(f"missing {SRC_SVG}")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        fg_svg = tmp_path / "foreground.svg"
        fg_svg.write_text(FOREGROUND_SVG, encoding="utf-8")

        for folder, size in LEGACY_SIZES.items():
            base = RES / folder
            render(SRC_SVG, base / "ic_launcher.png", size)
            render(SRC_SVG, base / "ic_launcher_round.png", size)
            print(f"legacy {folder} {size}px")

        for folder, size in FOREGROUND_SIZES.items():
            base = RES / folder
            render(fg_svg, base / "ic_launcher_foreground.png", size)
            print(f"foreground {folder} {size}px")

        render(SRC_SVG, WEB_PNG, 512)
        print(f"web {WEB_PNG} 512px")

    # Adaptive XML + background color
    (RES / "values" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        "    <color name=\"ic_launcher_background\">#FFFFFF</color>\n"
        "</resources>\n",
        encoding="utf-8",
    )
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
        "</adaptive-icon>\n"
    )
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(adaptive, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(adaptive, encoding="utf-8")

    # Vector background for older tooling references
    (RES / "drawable" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '    <path android:fillColor="#FFFFFF" android:pathData="M0,0h108v108h-108z"/>\n'
        "</vector>\n",
        encoding="utf-8",
    )
    print("adaptive resources written")


if __name__ == "__main__":
    main()
