# ZXingX

![Image](app/src/main/ic_launcher-web.png)

[![CI](https://img.shields.io/github/actions/workflow/status/Lanlan13-14/ZXingX/build.yml?branch=main&logo=github)](https://github.com/Lanlan13-14/ZXingX/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Lanlan13-14/ZXingX?logo=github)](https://github.com/Lanlan13-14/ZXingX/releases)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen?logo=android)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![License](https://img.shields.io/github/license/Lanlan13-14/ZXingX?logo=open-source-initiative)](https://opensource.org/licenses/apache-2-0)

**ZXingX** 是基于 [jenly1314/ZXingLite](https://github.com/jenly1314/ZXingLite) 的 **现代化修改版** 示例应用。

保留上游扫码 / 生成能力，重做 App 视觉与结果页，并提供可写版本号的 GitHub Actions 编译与发布流程。

当前默认版本：**v1.0.0**  
应用显示名：**ZXingX**

## 本版改动

1. **扫描结果页**  
   识别成功后进入独立页面，不再使用 Toast：
   - 顶栏：关闭 / 标题「扫描结果」/ 确认
   - 正文：完整文本（可选中）
   - 底部：复制、分享；`http(s)` 链接额外提供打开

2. **现代化 UI + 深色模式**  
   - Material 3 DayNight  
   - 分组列表、系统蓝强调色、统一 24dp 矢量图标  
   - 深色模式：正文使用柔和灰（`#D1D1D6`），避免纯白刺眼；图标略亮于正文  
   - 浅色 / 深色完整 token

3. **品牌**  
   - 应用名：ZXingX  
   - 启动图标按设计稿（取景框四角 + QR 定位块 + 蓝色扫描线）生成全密度资源

4. **CI / Release**  
   - `Build`：自动 `assembleDebug`  
   - `Release`：手动输入版本号（默认 `v1.0.0`），写入版本后打包并创建 GitHub Release

浏览器预览：[preview/ui-preview.html](preview/ui-preview.html)  
图标源文件：[design/zxingx_icon.svg](design/zxingx_icon.svg)

## 引入（库）

模块 `zxing-lite` 仍可被依赖。稳定上游坐标示例：

```gradle
implementation 'com.github.jenly1314:zxing-lite:3.5.0'
```

> v3.4.0+ 要求 **compileSdk >= 35**。

## 使用

### 扫描结果页

```kotlin
val intent = ScanResultActivity.createIntent(this, resultText)
startActivity(intent)
```

### 扫码 Activity 示例

```kotlin
class QRCodeScanActivity : BarcodeCameraScanActivity() {

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        cameraScan.setPlayBeep(true)
    }

    override fun createAnalyzer(): Analyzer<Result>? {
        val decodeConfig = DecodeConfig()
            .setHints(DecodeFormatManager.QR_CODE_HINTS)
            .setFullAreaScan(false)
            .setAreaRectRatio(0.8f)
        return MultiFormatAnalyzer(decodeConfig)
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false)
        val intent = Intent()
        intent.putExtra(CameraScan.SCAN_RESULT, result.result.text)
        setResult(RESULT_OK, intent)
        finish()
    }
}
```

### CodeUtils

```kotlin
CodeUtils.createQRCode(content, 600, logo)
CodeUtils.createBarCode(content, BarcodeFormat.CODE_128, 800, 200)
CodeUtils.parseCode(bitmap)
CodeUtils.parseQRCode(bitmap)
```

更多库能力见上游与 [CameraScan](https://github.com/jenly1314/CameraScan)。

## 本地构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

版本号（`gradle.properties`）：

```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
```

重新生成启动图标：

```bash
python3 scripts/generate_zxingx_icons.py
```

Release 默认使用 debug 签名以便 CI 无密钥安装；生产请替换 `app/build.gradle.kts` 中的签名配置。

## GitHub Actions

### Build

`.github/workflows/build.yml` — push / PR 到 `main` 或 `master` 时编译 debug APK。

### Release（可写版本号）

`.github/workflows/release.yml` — Actions 中手动运行：

| 参数 | 说明 | 默认 |
| --- | --- | --- |
| `version` | 标签，如 `v1.0.0` 或 `1.0.0` | `v1.0.0` |
| `release_notes` | 说明（Markdown） | 空 |
| `prerelease` | 预发布 | `false` |

流程：规范化版本 → 写入 `VERSION_NAME` / `VERSION_CODE` → `assembleRelease` → GitHub Release，附件 `ZXingX-vX.Y.Z.apk`。

`versionCode = major * 10000 + minor * 100 + patch`（`1.2.3` → `10203`）。

## 静态校验

```bash
python3 scripts/validate_ui_resources.py
python3 scripts/run_scan_result_tests.py
```

## 相关

- 上游：[jenly1314/ZXingLite](https://github.com/jenly1314/ZXingLite)
- [CameraScan](https://github.com/jenly1314/CameraScan)
- [ViewfinderView](https://github.com/jenly1314/ViewfinderView)

## 版本日志

#### v1.0.0

- 产品名 ZXingX；启动图标按设计稿重制
- 扫描结果独立页；现代化 UI 与深色模式（柔和灰正文）
- 自包含 Build / Release Actions（默认 `v1.0.0`）

#### 上游 v3.5.0

- 见 [CHANGELOG.md](CHANGELOG.md)

## License

```text
Copyright 2018 Jenly Yu
Copyright 2026 Lanlan13-14 (ZXingX modernization fork)

Licensed under the Apache License, Version 2.0
```
