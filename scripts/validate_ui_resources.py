#!/usr/bin/env python3
"""Static validation for the redesigned ZXingLite app UI resources."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
RES = APP / "res"
KT = APP / "kotlin" / "com" / "king" / "zxing" / "app"

errors: list[str] = []
passes: list[str] = []


def ok(msg: str) -> None:
    passes.append(msg)
    print(f"PASS  {msg}")


def fail(msg: str) -> None:
    errors.append(msg)
    print(f"FAIL  {msg}")


def parse_xml(path: Path) -> ET.Element | None:
    try:
        return ET.parse(path).getroot()
    except Exception as exc:  # noqa: BLE001
        fail(f"XML parse {path.relative_to(ROOT)}: {exc}")
        return None


def collect_ids(layout: Path) -> set[str]:
    ids: set[str] = set()
    text = layout.read_text(encoding="utf-8")
    for match in re.finditer(r'@\+id/([A-Za-z0-9_]+)', text):
        ids.add(match.group(1))
    return ids


def main() -> int:
    required_files = [
        RES / "layout" / "activity_main.xml",
        RES / "layout" / "activity_scan_result.xml",
        RES / "layout" / "code_activity.xml",
        RES / "layout" / "toolbar.xml",
        RES / "values" / "colors.xml",
        RES / "values-night" / "colors.xml",
        RES / "values" / "styles.xml",
        RES / "values-night" / "styles.xml",
        RES / "values" / "strings.xml",
        RES / "values" / "dimens.xml",
        RES / "anim" / "slide_in_right.xml",
        RES / "anim" / "slide_out_left.xml",
        RES / "anim" / "slide_in_left.xml",
        RES / "anim" / "slide_out_right.xml",
        KT / "ScanResultActivity.kt",
        KT / "ScanResultUtils.kt",
        KT / "MainActivity.kt",
        KT / "MultiFormatScanActivity.kt",
    ]
    for path in required_files:
        if path.exists():
            ok(f"exists {path.relative_to(ROOT)}")
        else:
            fail(f"missing {path.relative_to(ROOT)}")

    # All XML under res must parse
    xml_files = list(RES.rglob("*.xml"))
    for path in xml_files:
        root = parse_xml(path)
        if root is not None:
            ok(f"xml {path.relative_to(ROOT)}")

    # Color tokens present in light + night
    required_colors = [
        "colorPrimary",
        "colorBackground",
        "colorSurface",
        "colorTextPrimary",
        "colorTextSecondary",
        "colorIcon",
        "colorSeparatorHairline",
        "colorSurfaceSecondary",
    ]
    for mode in ("values", "values-night"):
        colors_path = RES / mode / "colors.xml"
        root = parse_xml(colors_path)
        if root is None:
            continue
        names = {el.attrib.get("name") for el in root.findall("color")}
        for name in required_colors:
            if name in names:
                ok(f"{mode} has color {name}")
            else:
                fail(f"{mode} missing color {name}")

    # Theme parent is DayNight
    styles = (RES / "values" / "styles.xml").read_text(encoding="utf-8")
    if "Theme.Material3.DayNight.NoActionBar" in styles:
        ok("AppTheme uses Material3 DayNight")
    else:
        fail("AppTheme is not Material3 DayNight")

    night_styles = (RES / "values-night" / "styles.xml").read_text(encoding="utf-8")
    if 'android:windowLightStatusBar">false' in night_styles:
        ok("night theme disables light status bar")
    else:
        fail("night theme missing windowLightStatusBar=false")

    # Result layout structure matches reference screenshot semantics
    result_ids = collect_ids(RES / "layout" / "activity_scan_result.xml")
    for required in ("btnClose", "btnConfirm", "tvTitle", "tvResult", "btnCopy", "btnShare", "btnOpen"):
        if required in result_ids:
            ok(f"result layout id {required}")
        else:
            fail(f"result layout missing id {required}")

    result_xml = (RES / "layout" / "activity_scan_result.xml").read_text(encoding="utf-8")
    if "扫描结果" in (RES / "values" / "strings.xml").read_text(encoding="utf-8") or "@string/scan_result_title" in result_xml:
        ok("result title string wired")
    else:
        fail("result title not wired")

    # Main no longer uses Toast for scan results
    main_kt = (KT / "MainActivity.kt").read_text(encoding="utf-8")
    if "ScanResultActivity" in main_kt and "openScanResult" in main_kt:
        ok("MainActivity routes to ScanResultActivity")
    else:
        fail("MainActivity does not open ScanResultActivity")
    if "Toast.makeText" in main_kt:
        fail("MainActivity still shows Toast for results")
    else:
        ok("MainActivity no longer uses Toast for results")

    multi_kt = (KT / "MultiFormatScanActivity.kt").read_text(encoding="utf-8")
    if "ScanResultActivity" in multi_kt and "Toast" not in multi_kt:
        ok("MultiFormatScanActivity routes to result page without Toast")
    else:
        fail("MultiFormatScanActivity still toast-based or missing result page")

    manifest = (APP / "AndroidManifest.xml").read_text(encoding="utf-8")
    if ".ScanResultActivity" in manifest:
        ok("ScanResultActivity registered in manifest")
    else:
        fail("ScanResultActivity missing from manifest")

    # Drawable vectors exist
    for name in (
        "ic_close",
        "ic_check",
        "ic_back",
        "ic_copy",
        "ic_share",
        "ic_open",
        "bg_surface_card",
        "bg_icon_button",
    ):
        path = RES / "drawable" / f"{name}.xml"
        if path.exists():
            ok(f"drawable {name}")
        else:
            fail(f"drawable missing {name}")

    # Home actions present
    main_ids = collect_ids(RES / "layout" / "activity_main.xml")
    for required in (
        "btnMultiFormat",
        "btnQRCode",
        "btnFullQRCode",
        "btnPickPhoto",
        "btnGenerateQrCode",
        "btnGenerateBarcode",
    ):
        if required in main_ids:
            ok(f"home action id {required}")
        else:
            fail(f"home missing id {required}")

    # Icon drawables must be 24x24 viewport vectors
    for name in (
        "ic_close",
        "ic_check",
        "ic_back",
        "ic_chevron_right",
        "ic_scan_multi",
        "ic_scan_qr",
        "ic_scan_full",
        "ic_photo",
        "ic_generate_qr",
        "ic_generate_barcode",
        "ic_copy",
        "ic_share",
        "ic_open",
    ):
        path = RES / "drawable" / f"{name}.xml"
        text = path.read_text(encoding="utf-8")
        if 'android:viewportWidth="24"' in text and 'android:viewportHeight="24"' in text:
            ok(f"vector viewport 24 {name}")
        else:
            fail(f"vector viewport not 24 {name}")
        if "emoji" in text.lower():
            fail(f"emoji reference in {name}")

    # Preview must use SVG symbols, not emoji
    preview = ROOT / "preview" / "ui-preview.html"
    if preview.exists():
        ptxt = preview.read_text(encoding="utf-8")
        if 'id="ic-scan-multi"' in ptxt and "<use href=\"#ic-scan-multi\"" in ptxt:
            ok("preview uses SVG symbol icons")
        else:
            fail("preview missing SVG symbol icons")
        # crude emoji ranges
        if re.search(r"[\U0001F300-\U0001FAFF]", ptxt):
            fail("preview still contains emoji glyphs")
        else:
            ok("preview has no emoji glyphs")
        if "grid-template-columns: var(--badge)" in ptxt or "grid-template-columns: var(--badge) minmax(0, 1fr) var(--chev)" in ptxt:
            ok("preview rows use grid alignment")
        else:
            fail("preview rows not grid-aligned")
    else:
        fail("preview html missing")

    # Single CI workflow: build + versioned release
    wf_dir = ROOT / ".github" / "workflows"
    ci_wf = wf_dir / "ci.yml"
    if ci_wf.exists():
        ok("workflow exists ci.yml")
    else:
        fail("workflow missing ci.yml")
    extras = [p.name for p in wf_dir.glob("*.yml") if p.name != "ci.yml"]
    if extras:
        fail(f"extra workflows present: {extras}")
    else:
        ok("only one workflow file (ci.yml)")
    rtxt = ci_wf.read_text(encoding="utf-8") if ci_wf.exists() else ""
    if 'default: "v1.0.0"' in rtxt or "default: 'v1.0.0'" in rtxt:
        ok("release default version v1.0.0")
    else:
        fail("release default version is not v1.0.0")
    if "VERSION_NAME=" in rtxt and "VERSION_CODE=" in rtxt and "softprops/action-gh-release" in rtxt:
        ok("release writes version and publishes GitHub Release")
    else:
        fail("release path incomplete in ci.yml")
    if "assembleDebug" in rtxt and "workflow_dispatch" in rtxt:
        ok("ci.yml covers build and manual release")
    else:
        fail("ci.yml missing build or dispatch")
    if "jenly1314/actions" in rtxt:
        fail("workflows still depend on upstream reusable actions")
    else:
        ok("workflows are self-contained")

    # gradle version defaults
    gp = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    if re.search(r"^VERSION_NAME=1\.0\.0\s*$", gp, re.M):
        ok("gradle.properties VERSION_NAME=1.0.0")
    else:
        fail("gradle.properties VERSION_NAME is not 1.0.0")
    if re.search(r"^VERSION_CODE=1\s*$", gp, re.M):
        ok("gradle.properties VERSION_CODE=1")
    else:
        fail("gradle.properties VERSION_CODE is not 1")

    # App branding
    strings = (RES / "values" / "strings.xml").read_text(encoding="utf-8")
    if ">ZXingX<" in strings:
        ok("app_name is ZXingX")
    else:
        fail("app_name is not ZXingX")

    night_colors = (RES / "values-night" / "colors.xml").read_text(encoding="utf-8")
    # primary text must be soft gray, not pure white
    m = re.search(
        r'<color name="colorTextPrimary">(#?[A-Fa-f0-9]+)</color>',
        night_colors,
    )
    if m and m.group(1).upper() not in {"#FFFFFF", "#FFF", "FFFFFF", "FFF"}:
        ok(f"night colorTextPrimary soft gray {m.group(1)}")
    else:
        fail("night colorTextPrimary is pure white or missing")
    m_icon = re.search(
        r'<color name="colorIcon">(#?[A-Fa-f0-9]+)</color>',
        night_colors,
    )
    if m_icon:
        ok(f"night colorIcon {m_icon.group(1)}")
    else:
        fail("night colorIcon missing")

    # launcher assets exist
    if (ROOT / "design" / "zxingx_icon.svg").exists():
        ok("design zxingx_icon.svg exists")
    else:
        fail("design zxingx_icon.svg missing")
    for dens in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        fg = RES / f"mipmap-{dens}" / "ic_launcher_foreground.png"
        lg = RES / f"mipmap-{dens}" / "ic_launcher.png"
        if fg.exists() and lg.exists():
            ok(f"launcher density {dens}")
        else:
            fail(f"launcher density missing {dens}")
    bg = (RES / "values" / "ic_launcher_background.xml").read_text(encoding="utf-8")
    if "#FFFFFF" in bg or "#ffffff" in bg:
        ok("launcher background white")
    else:
        fail("launcher background not white")

    # README covers release version input
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "ZXingX" in readme and "现代化" in readme:
        ok("README brands ZXingX modernization")
    else:
        fail("README missing ZXingX modernization description")
    if "v1.0.0" in readme and "Release" in readme and "VERSION_NAME" in readme:
        ok("README documents versioned release")
    else:
        fail("README missing versioned release docs")
    if re.search(r"[\U0001F300-\U0001FAFF]", readme):
        fail("README contains emoji")
    else:
        ok("README has no emoji")
    if "ZXingX-" in rtxt and ".apk" in rtxt:
        ok("release APK named ZXingX")
    else:
        fail("release APK name not ZXingX")

    print()
    print(f"Summary: {len(passes)} passed, {len(errors)} failed")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
