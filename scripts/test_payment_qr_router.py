from pathlib import Path

root = Path(__file__).resolve().parents[1]
classifier = (root / 'app/src/main/kotlin/com/king/zxing/app/PaymentQrClassifier.kt').read_text()
router = (root / 'app/src/main/kotlin/com/king/zxing/app/PaymentQrRouter.kt').read_text()
main = (root / 'app/src/main/kotlin/com/king/zxing/app/MainActivity.kt').read_text()
multi = (root / 'app/src/main/kotlin/com/king/zxing/app/MultiFormatScanActivity.kt').read_text()
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
checks = {
    'wechat patterns': 'wxp://' in classifier and 'weixin://wxpay/' in classifier,
    'alipay exact host': 'host == "qr.alipay.com"' in classifier,
    'paypal exact hosts': 'www.paypal.com' in classifier and 'paypal.me' in classifier,
    'package targets': all(x in router for x in ['com.tencent.mm','com.eg.android.AlipayGphone','com.paypal.android.p2pmobile']),
    'wechat scanner chain': all(x in router for x in ['weixin://scanqrcode','weixin://dl/scan','LauncherUI.From.Scaner.Shortcut']),
    'alipay scanner only': 'saId=10000007' in router and 'qrcode=' not in router,
    'paypal scanner chain': all(x in router for x in ['paypal://qrcode_scan','paypal://qrcode','paypal://scan']),
    'raw code not forwarded': 'Uri.parse(raw)' not in router and 'encode(raw)' not in router,
    'main route first': 'PaymentQrRouter.openIfPaymentQr(this, content)' in main,
    'continuous route': 'paymentAppOpened = true' in multi,
    'fallback result page': 'ScanResultActivity.createIntent' in main and 'ScanResultActivity.createIntent' in multi,
    'manifest queries': '<queries>' in manifest and 'com.tencent.mm' in manifest,
}
failed = 0
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
    failed += not ok
print(f'\n{len(checks)-failed}/{len(checks)} passed')
raise SystemExit(1 if failed else 0)
