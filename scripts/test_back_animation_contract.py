from pathlib import Path

root = Path(__file__).resolve().parents[1]
layout = (root / 'app/src/main/res/layout/activity_scan_result.xml').read_text()
activity = (root / 'app/src/main/kotlin/com/king/zxing/app/ScanResultActivity.kt').read_text()
controller = (root / 'zxing-lite/src/main/kotlin/com/king/zxing/gesture/EdgeSwipeBackController.kt').read_text()
strings = (root / 'app/src/main/res/values/strings.xml').read_text()

checks = {
    'confirm button removed': 'btnConfirm' not in layout and 'btnConfirm' not in activity,
    'confirm string removed': 'scan_result_confirm' not in strings,
    'back arrow retained': '@drawable/ic_back' in layout,
    'center title spacer retained': '<Space' in layout and '@dimen/icon_button_size' in layout,
    'toolbar uses dedicated exit': 'requestToolbarBack()' in activity,
    'toolbar duration 350ms': 'duration = 350L' in controller,
    'toolbar is pure horizontal': 'surface.scaleX = 1f' in controller and 'surface.translationY = 0f' in controller,
    'predictive exit continues offscreen': 'animatePredictiveExit()' in controller and 'surface.width * 1.04f' in controller,
    'finish after animation': 'withEndAction(::finishImmediately)' not in controller and 'finishImmediately()' in controller,
    'cancel guarded': 'animationGeneration' in controller and 'onAnimationCancel' in controller,
}
failed = 0
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
    failed += not ok
print(f'\n{len(checks)-failed}/{len(checks)} passed')
raise SystemExit(1 if failed else 0)
