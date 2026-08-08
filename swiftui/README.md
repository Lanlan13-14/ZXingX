# ZXingX for iOS (swiftui/)

[ZXingX](https://github.com/Lanlan13-14/ZXingX) 的 iOS 版。界面与功能与 Android 版一致，
交互按 iOS 习惯实现，全部使用 iOS 平台标准接口（无任何第三方依赖）。

- SwiftUI 界面，跟随系统浅色/深色外观
- 最低系统 iOS 17.0，Xcode 16+，仅 iPhone（竖屏）
- Bundle ID `com.lanlan13.zxingx`，应用名 **ZXingX**，版本 1.0.0

## 功能映射（Android → iOS）

| Android 实现 | iOS 标准接口 |
| --- | --- |
| CameraX + ZXingLite MultiFormatAnalyzer 实时识别 | AVFoundation `AVCaptureMetadataOutput` |
| DecodeConfig `areaRectRatio(0.8)` 识别区域 | `AVCaptureMetadataOutput.rectOfInterest` |
| 扫二维码（线条/网格扫描框） | SwiftUI 取景框覆盖层（同款网格激光动画） |
| 全屏识别（ViewfinderStyle.POPULAR + 结果点） | 同款角落框样式 + 识别结果点 |
| 闪光灯 | `AVCaptureDevice.torchMode` |
| 预览双指缩放 | `AVCaptureDevice.videoZoomFactor` |
| 多摄捏合切换（Camera2 物理镜头） | 虚拟设备（Triple/DualWide/Dual）自动跨焦段切换（`virtualDeviceSwitchOverVideoZoomFactors`），无镜头按钮 |
| 识别提示音 `setPlayBeep(true)` | 内存合成 PCM 提示音 + 系统触感反馈 |
| 相册识图 `CodeUtils.parseCode` | Vision `VNDetectBarcodesRequest` + PhotosUI `PhotosPicker`（无需相册权限） |
| 生成二维码（容错降级链 + 中心 logo） | `CIQRCodeGenerator`，同款 H→Q→M→L 降级链与 logo 叠加 |
| 生成条形码 CODE_128（含文字） | `CICode128BarcodeGenerator` + 文字绘制 |
| 扫描结果页（复制/分享/打开） | SwiftUI 页面，`UIPasteboard` / `UIActivityViewController` / `UIApplication.open` |
| 微信/支付宝/PayPal 付款码路由 | `canOpenURL` 链式尝试各 App 扫码入口，不传二维码正文，失败保留结果页 |
| 快捷设置「扫一扫」磁贴 | 主屏幕快捷操作（UIApplicationShortcutItem）→ 连续扫码 |
| 自绘边缘返回手势 | 系统原生侧滑返回（无需自绘） |
| Material 3 浅/深色（iOS 色系） | UIKit 系统语义色（即该色系的来源），自动深浅色 |

## 与 Android 版的已知差异

- **码制覆盖**：iOS AVFoundation/Vision 不支持 Codabar、MaxiCode、UPC-EAN-extension；
  UPC-A 会以 EAN-13 形式返回（与 ZXing 的 UPC/EAN 读取行为一致）。
  其余（QR、Aztec、DataMatrix、PDF417、Code 39/93/128、EAN-8/13、UPC-E、ITF、
  GS1 DataBar）双端一致。
- **付款码路由**：微信尝试 `weixin://scanqrcode → weixin://dl/scan → weixin://`；
  支付宝 `alipayqr://platformapi/startapp?saId=10000007`（与 Android 同一扫码入口
  saId）；PayPal 同 Android 的链式顺序。对应 scheme 已写入
  `LSApplicationQueriesSchemes`。
- **系统限制**：iOS 快捷操作需在主屏幕长按图标使用；没有与快捷设置磁贴完全等价的
  系统入口。

## 构建

```bash
cd swiftui
open ZXingX.xcodeproj        # Xcode 16+，选择 ZXingX scheme 运行
```

命令行：

```bash
xcodebuild test \
  -project ZXingX.xcodeproj -scheme ZXingX \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO
```

真机安装需要在 Xcode 里选择自己的 Development Team 签名；
发布 App Store 需要 Apple Developer 账号（Archive → Distribute）。

## CI

仓库唯一的工作流 `.github/workflows/ci.yml` 同时编译两端：

| 触发 | Android job | iOS job |
| --- | --- | --- |
| push / PR → main | 单测 + debug APK artifact | 单测（模拟器）+ 无签名 Release 编译，上传 `.app` |
| 手动 Run workflow（发布） | release APK + GitHub Release | 同上（无签名编译验证） |

iOS 安装包必须经 Apple Developer 签名，因此 Release 只包含 Android APK；
iOS 发布需本地 Archive。

## 目录结构

```
swiftui/
├── ZXingX.xcodeproj/          # Xcode 16 工程（synchronized groups）
│   └── xcshareddata/xcschemes/ZXingX.xcscheme
├── ZXingX/
│   ├── ZXingXApp.swift        # App 入口
│   ├── AppDelegate.swift      # 主屏幕快捷操作「扫一扫」
│   ├── Router.swift           # 导航状态（对应各 Activity 跳转语义）
│   ├── Info.plist             # 相机权限说明 / URL schemes / 快捷操作
│   ├── Assets.xcassets/       # AppIcon（同一设计稿 SVG 栅格化）+ QR 中心 logo
│   ├── Models/                # 支付码分类/路由、结果工具（逐行移植 + 同款单测）
│   ├── Scanning/              # 相机控制器、预览层、取景框、扫描页
│   ├── CodeGen/               # 二维码 / CODE_128 生成（容错链逐行对齐）
│   ├── Vision/                # 相册图片识别
│   ├── Views/                 # 首页 / 结果页 / 生成页 / 分享 / 轻提示
│   └── Util/                  # 提示音合成
└── ZXingXTests/               # XCTest（含 Android JVM 测试的全部用例）
```

## License

与主仓库一致：GPL-3.0。
