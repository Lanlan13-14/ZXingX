# ZXingX

![Image](app/src/main/ic_launcher-web.png)

[![CI](https://img.shields.io/github/actions/workflow/status/Lanlan13-14/ZXingX/ci.yml?branch=main&logo=github)](https://github.com/Lanlan13-14/ZXingX/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Lanlan13-14/ZXingX?logo=github)](https://github.com/Lanlan13-14/ZXingX/releases)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen?logo=android)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![License](https://img.shields.io/github/license/Lanlan13-14/ZXingX?logo=gnu)](https://www.gnu.org/licenses/gpl-3.0.html)

基于 [ZXingLite](https://github.com/jenly1314/ZXingLite) 的现代化扫码应用。

在上游扫码与条码生成能力之上，重做了界面与结果展示：独立扫描结果页、浅色/深色主题、统一矢量图标。扫描框支持线条样式与网格样式，可开闪光灯。


## 功能

- 连续扫码 / 仅二维码 / 全屏识别 / 相册识图
- 生成二维码、CODE_128 条形码
- 扫描结果页：关闭 / 确认，复制、分享，链接可打开
- Material 3 浅色与深色模式（深色正文为柔和灰，减轻刺眼）
- 闪光灯、可自定义取景框

## 界面预览

[preview/ui-preview.html](preview/ui-preview.html)

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

版本号写在 `gradle.properties`：

```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
```

重新生成启动图标：

```bash
python3 scripts/generate_zxingx_icons.py
```

## 使用说明

### 打开扫描结果页

```kotlin
val intent = ScanResultActivity.createIntent(this, resultText)
startActivity(intent)
```

### 自定义扫码页

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

### 生成与解析

```kotlin
CodeUtils.createQRCode(content, 600, logo)
CodeUtils.createBarCode(content, BarcodeFormat.CODE_128, 800, 200)
CodeUtils.parseCode(bitmap)
CodeUtils.parseQRCode(bitmap)
```

库层细节见上游 [ZXingLite](https://github.com/jenly1314/ZXingLite) 与 [CameraScan](https://github.com/jenly1314/CameraScan)。  
作为依赖引入上游库时：

```gradle
implementation 'com.github.jenly1314:zxing-lite:3.5.0'
```

`compileSdk` 需 >= 35（v3.4.0+）。

## CI

仅保留一份工作流：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)

| 触发 | 行为 |
| --- | --- |
| `push` / `pull_request` → `main` | 编译 debug APK，上传 artifact |
| Actions 手动 Run workflow | 按版本号打 release APK，创建 GitHub Release |

手动发布参数：

| 参数 | 说明 | 默认 |
| --- | --- | --- |
| `version` | 如 `v1.0.0` 或 `1.0.0` | `v1.0.0` |
| `release_notes` | 说明（可选） | 空 |
| `prerelease` | 是否预发布 | `false` |

`versionCode = major * 10000 + minor * 100 + patch`。  
Release 附件名：`ZXingX-vX.Y.Z.apk`。

Release 使用仓库内固定密钥 `app/keystore/zxingx-release.jks` 签名，保证 CI 多次发版可互相覆盖安装。

若手机上已装的是旧版（debug 签名或其它密钥），更新会报「与已安装应用签名不同 / -7」，需**先卸载再安装一次**；之后用同一密钥打的包即可正常更新。

## 校验脚本

```bash
python3 scripts/validate_ui_resources.py
python3 scripts/run_scan_result_tests.py
```

## 相关项目

- [jenly1314/ZXingLite](https://github.com/jenly1314/ZXingLite)（上游）
- [CameraScan](https://github.com/jenly1314/CameraScan)
- [ViewfinderView](https://github.com/jenly1314/ViewfinderView)

## 版本

### v1.0.0

- 更名 ZXingX，启动图标按设计稿重制
- 扫描结果独立页；现代化 UI 与深色模式
- 单一 CI 工作流：自动构建 + 手动发版

上游变更见 [CHANGELOG.md](CHANGELOG.md)。

## License

GNU General Public License v3.0，见 [LICENSE](LICENSE)。

```text
Copyright (C) 2026 Lanlan13-14
Copyright (C) 2018 Jenly Yu (upstream ZXingLite portions)
```

上游 ZXingLite 原为 Apache-2.0；本仓库整体按 GPL-3.0 分发。
