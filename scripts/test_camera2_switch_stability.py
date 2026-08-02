from pathlib import Path

root = Path(__file__).resolve().parents[1]
controller = (root / 'zxing-lite/src/main/kotlin/com/king/zxing/camera2/Camera2ScanController.kt').read_text()
swipe = (root / 'zxing-lite/src/main/kotlin/com/king/zxing/gesture/EdgeSwipeBackController.kt').read_text()
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
activity = (root / 'zxing-lite/src/main/kotlin/com/king/zxing/Camera2BarcodeScanActivity.kt').read_text()
layout = (root / 'zxing-lite/src/main/res/layout/zxl_camera2_scan.xml').read_text()
qr_layout = (root / 'app/src/main/res/layout/activity_qrcode_scan.xml').read_text()

checks = {
    'native predictive disabled': 'android:enableOnBackInvokedCallback="false"' in manifest,
    'no native progress callback': 'handleOnBackProgressed' not in swipe,
    'custom edge gesture all versions': 'installLegacyEdgeGesture()' in swipe,
    'multi touch cancels swipe': 'MotionEvent.ACTION_POINTER_DOWN' in swipe and 'event.pointerCount > 1' in swipe,
    'swipe force reset': 'fun forceReset()' in swipe,
    'cancelled animations do not finish': 'onAnimationCancel' in swipe and 'animationGeneration' in swipe,
    'camera multi-touch resets swipe': 'onMultiTouch = { swipeBack.forceReset() }' in activity,
    'serialized switch state': 'switchInFlight' in controller and 'pendingLensIndex' in controller,
    'zoom handled on camera thread': 'cameraHandler.post { handleZoomRequest(requestedZoom) }' in controller,
    'switch coalescing': 'if (switchInFlight)' in controller and 'pendingLensIndex = target' in controller,
    'single freeze per transaction': 'if (startingTransaction) freezeFrame()' in controller,
    'frozen overlay instance safe': 'if (frozenPreview === overlay)' in controller,
    'first frame timeout cancellable': 'firstFrameTimeout?.let(textureView::removeCallbacks)' in controller,
    'actual attempted binding tracked': 'attemptBinding = binding' in controller,
    'rollback to last stable frames': 'lastWorkingLensIndex' in controller and 'lastWorkingBindingIndex' in controller,
    'one Camera2 callback executor': 'Executor { command -> cameraHandler.post(command) }' in controller,
    'physical switch debounce': 'postDelayed(it, 140L)' in controller and 'lensSwitchDebounce' in controller,
    'stale reader callbacks ignored': 'if (source !== reader)' in controller,
    'camera roots opaque black': '@android:color/black' in layout and '@android:color/black' in qr_layout,
    'lifecycle resets swipe surface': 'onWindowFocusChanged' in activity and 'swipeBack.forceReset()' in activity,
}

failed = 0
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
    failed += not ok
print(f'\n{len(checks)-failed}/{len(checks)} passed')
raise SystemExit(1 if failed else 0)
