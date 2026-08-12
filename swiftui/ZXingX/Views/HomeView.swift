//
//  HomeView.swift
//  ZXingX (iOS)
//
//  Port of MainActivity (activity_main.xml): large-title "扫一扫", grouped
//  sections 扫描 / 生成, card rows = icon badge + title + description + chevron.
//  iOS idiom: inset-grouped List with NavigationLink rows — same information
//  hierarchy, native look and feel (highlight, chevron, swipe back).
//
//  The palette comes straight from UIColor system semantics, which are exactly
//  the values the Android app hardcoded in colors.xml (#F2F2F7 grouped
//  background, #007AFF/#0A84FF accent, #1C1C1E dark surfaces, …).
//

import PhotosUI
import SwiftUI

struct HomeView: View {

    @EnvironmentObject private var router: Router
    @State private var pickedItem: PhotosPickerItem?

    var body: some View {
        NavigationStack(path: $router.path) {
            ZStack {
                // iPad: the list is capped at a readable width and centered;
                // the grouped background fills the margins so the hidden
                // list background blends in seamlessly (light & dark).
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                List {
                    Section("扫描") {
                        actionRow(
                            icon: "barcode.viewfinder",
                            title: "连续扫码",
                            description: "识别多种一维码与二维码",
                            route: .scan(.continuous)
                        )
                        actionRow(
                            icon: "qrcode.viewfinder",
                            title: "扫二维码",
                            description: "仅识别二维码，更快更准",
                            route: .scan(.qrBox)
                        )
                        actionRow(
                            icon: "viewfinder",
                            title: "全屏识别",
                            description: "全画面识别二维码",
                            route: .scan(.qrFullScreen)
                        )
                        photoRow
                    }

                    Section("生成") {
                        actionRow(
                            icon: "qrcode",
                            title: "生成二维码",
                            description: "输入文字或链接，生成可扫二维码",
                            route: .generateQR
                        )
                        actionRow(
                            icon: "barcode",
                            title: "生成条形码",
                            description: "输入内容，生成 CODE_128 条码",
                            route: .generateBarcode
                        )
                    }
                }
                .scrollContentBackground(.hidden)
                .frame(maxWidth: 720)
            }
            .navigationTitle("扫一扫")
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .scan(let mode):
                    ScannerView(mode: mode)
                case .result(let text):
                    ScanResultView(text: text)
                case .generateQR:
                    CodeGeneratorView(isQRCode: true, title: "生成二维码")
                case .generateBarcode:
                    CodeGeneratorView(isQRCode: false, title: "生成条形码")
                }
            }
        }
    }

    // MARK: - Rows

    private func actionRow(icon: String, title: String, description: String, route: Route) -> some View {
        NavigationLink(value: route) {
            rowLabel(icon: icon, title: title, description: description)
        }
    }

    /// 相册识别 — PhotosPicker (PhotosUI), the standard iOS photo access;
    /// no photo-library permission prompt needed. Manual chevron so the row
    /// matches the NavigationLink rows (Android shows one on every row).
    private var photoRow: some View {
        PhotosPicker(
            selection: $pickedItem,
            matching: .images,
            photoLibrary: .shared()
        ) {
            HStack {
                rowLabel(
                    icon: "photo",
                    title: "相册识别",
                    description: "从图片中识别条码"
                )
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(uiColor: .tertiaryLabel))
            }
        }
        .onChange(of: pickedItem) { item in
            guard let item else { return }
            pickedItem = nil
            Task { await handlePickedPhoto(item) }
        }
    }

    private func rowLabel(icon: String, title: String, description: String) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemFill))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .font(.system(size: 17))
                    .foregroundStyle(Color(uiColor: .label))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16))
                    .foregroundStyle(Color(uiColor: .label))
                Text(description)
                    .font(.system(size: 12))
                    .foregroundStyle(Color(uiColor: .secondaryLabel))
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Photo decode

    private func handlePickedPhoto(_ item: PhotosPickerItem) async {
        // MainActivity.parsePhoto: bitmap load failure only logs and returns…
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        // …while a failed decode still opens the result page, which then shows
        // 未识别到有效内容 (CodeUtils.parseCode → null → openScanResult(null)).
        let text = await ImageCodeReader.readCode(from: image)
        if PaymentQrRouter.openIfPaymentQr(text) { return }
        router.open(.result(text ?? ""))
    }
}
