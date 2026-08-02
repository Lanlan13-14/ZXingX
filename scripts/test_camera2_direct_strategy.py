from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
discovery = (ROOT / 'zxing-lite/src/main/kotlin/com/king/zxing/camera2/Camera2LensDiscovery.kt').read_text()
controller = (ROOT / 'zxing-lite/src/main/kotlin/com/king/zxing/camera2/Camera2ScanController.kt').read_text()
activities = [
    ROOT / 'app/src/main/kotlin/com/king/zxing/app/QRCodeScanActivity.kt',
    ROOT / 'app/src/main/kotlin/com/king/zxing/app/MultiFormatScanActivity.kt',
    ROOT / 'app/src/main/kotlin/com/king/zxing/app/FullScreenQRCodeScanActivity.kt',
]

checks = {
    'camera2 public ID discovery': 'cameraIdList' in discovery,
    'bounded hidden ID probing': 'val ranges = listOf(0..9, 20..29, 40..49, 80..89, 100..119)' in discovery and 'consecutiveMisses >= 6' in discovery,
    'logical physical discovery': 'physicalCameraIds' in discovery,
    'physical binding model': 'physicalCameraId: String?' in discovery,
    'stable reference main': 'it.first == "0"' in discovery and 'isLogical(chars)' in discovery,
    '35mm equivalent focal': '43.2666f' in discovery,
    'direct camera open': 'manager.openCamera(binding.openCameraId' in controller,
    'physical output binding': 'setPhysicalCameraId(physicalCameraId)' in controller,
    'camera2 YUV reader': 'ImageReader.newInstance' in controller and 'YUV_420_888' in controller,
    'ZXing luma decode': 'analyzer.analyze(rotated' in controller,
    'preview transition cover': 'freezeFrame' in controller and 'fadeFrozenFrame' in controller,
    'first preview frame gate': 'onSurfaceTextureUpdated' in controller and 'firstFrameCount >= 2' in controller,
    'binding fallback': 'tryNextBinding(token' in controller,
    'app migrated to camera2': all('Camera2BarcodeScanActivity' in p.read_text() for p in activities),
}

failed = []
for name, passed in checks.items():
    print(('PASS' if passed else 'FAIL'), name)
    if not passed:
        failed.append(name)

print(f'\n{len(checks) - len(failed)}/{len(checks)} passed')
if failed:
    raise SystemExit(1)
