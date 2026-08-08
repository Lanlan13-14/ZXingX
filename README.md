# ZXingX

![Image](app/src/main/ic_launcher-web.png)

[![CI](https://img.shields.io/github/actions/workflow/status/Lanlan13-14/ZXingX/ci.yml?branch=main&logo=github)](https://github.com/Lanlan13-14/ZXingX/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Lanlan13-14/ZXingX?logo=github)](https://github.com/Lanlan13-14/ZXingX/releases)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen?logo=android)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![License](https://img.shields.io/github/license/Lanlan13-14/ZXingX?logo=gnu)](https://www.gnu.org/licenses/gpl-3.0.html)

基于 [ZXingLite](https://github.com/jenly1314/ZXingLite) 的现代化扫码应用。

在上游扫码与条码生成能力之上，重做了界面与结果展示：独立扫描结果页、浅色/深色主题、统一矢量图标。扫描框支持线条样式与网格样式，可开闪光灯。

默认版本 **v1.0.0**，应用名 **ZXingX**。  
应用包名 **`com.lanlan13.zxingx`**（与上游 ZXingLite 的 `com.king.zxing.app` 不同，可同时安装）。

## 功能

- 连续扫码 / 仅二维码 / 全屏识别 / 相册识图
- 生成二维码 / 条形码：可输入任意内容，点生成后出图（二维码中心为 ZXingX 标识），可分享图片
- 扫描结果页：返回、复制、分享，链接可打开
- 识别微信、支付宝、PayPal 付款二维码后，只用内容判断 App 类型，再链式尝试打开对应 App 的扫码入口；不把二维码正文传给支付 App，失败时保留结果页
- ZXingX 自绘边缘返回：页面跟手缩放、圆角化、取消回弹 / 松手完成；不依赖系统预测性返回动画开关
- Material 3 浅色与深色模式（深色正文为柔和灰，减轻刺眼）
- 闪光灯；预览区双指缩放
- Android 快捷设置「扫一扫」磁贴：点击直接进入连续扫码
- **标准连续变焦**：通过 CameraX 绑定后置逻辑相机，双指缩放调用 `CameraControl.setZoomRatio()`；底层是否切换物理镜头由设备 HAL 决定，不使用镜头 ID 猜测或厂商适配。

## 界面预览

[preview/ui-preview.html](preview/ui-preview.html)  
[preview/generate-preview.html](preview/generate-preview.html)

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

### iOS 版

[`swiftui/`](swiftui/) 目录是功能与界面一致的 iOS 版（SwiftUI + iOS 标准接口），
CI 会与 Android 同步编译。详见 [swiftui/README.md](swiftui/README.md)。

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

## 相机与多摄

扫码页只使用 CameraX / Camera2 公开能力，不包含厂商或机型硬编码：

1. 要求后置镜头方向。
2. 如果设备公开逻辑多摄，优先选择逻辑后置相机；否则使用 CameraX 返回的后置相机。
3. 双指缩放沿用 CameraScan 的标准 `CameraControl.setZoomRatio()` 调用。
4. 底层是否在超广角、主摄、长焦之间切换由设备 Camera HAL 决定，应用不猜测阈值、不读取或绑定未经公开的镜头。

本项目不承诺显式物理镜头切换。设备没有公开逻辑多摄时，只能使用标准后置相机和其支持的连续变焦。

## CI

仅保留一份工作流：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)

| 触发 | 行为 |
| --- | --- |
| `push` / `pull_request` → `main` | 编译 debug APK，上传 artifact；iOS 无签名编译验证并上传 `.app` / 未签名 `.ipa` artifact |
| Actions 手动 Run workflow | 按版本号打 release APK，创建 GitHub Release；`release-ios` 把未签名 iOS `.ipa` 附上同一 Release |

手动发布参数：

| 参数 | 说明 | 默认 |
| --- | --- | --- |
| `version` | 如 `v1.0.0` 或 `1.0.0` | `v1.0.0` |
| `release_notes` | 说明（可选） | 空 |
| `prerelease` | 是否预发布 | `false` |

`versionCode = major * 10000 + minor * 100 + patch`。  
Release 附件：Android `ZXingX-vX.Y.Z.apk`；iOS `ZXingX-vX.Y.Z-unsigned.ipa`
（未签名，需重签名后侧载，见 [swiftui/README.md](swiftui/README.md)）。

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
- 包名改为 `com.lanlan13.zxingx`，可与原版 ZXingLite 共存
- 扫描结果独立页；现代化 UI 与深色模式
- 生成页支持输入自定义内容并分享
- CameraX 后置逻辑相机标准连续变焦（无厂商或机型适配）
- ZXingX 自绘边缘返回动画（不依赖系统实验开关）
- 单一 CI 工作流：自动构建 + 手动发版

上游变更见 [CHANGELOG.md](CHANGELOG.md)。

## License

GNU General Public License v3.0，见 [LICENSE](LICENSE)。

```text
Copyright (C) 2026 Lanlan13-14
Copyright (C) 2018 Jenly Yu (upstream ZXingLite portions)
```

上游 ZXingLite 原为 Apache-2.0；本仓库整体按 GPL-3.0 分发。
