//
//  CodeGeneratorView.swift
//  ZXingX (iOS)
//
//  Port of CodeActivity (生成二维码 / 生成条形码):
//  - format chip (二维码 · QR_CODE / 条形码 · CODE_128)
//  - labeled input card, prefilled with the same sample content
//  - 生成 button (IME "done" also generates; keyboard hides on generate)
//  - generated image + 当前内容 caption + 分享图片
//  - initial auto-generate on appear (CodeActivity: generateFromInput(false))
//  - failure toasts (empty input / QR too long / CODE_128 charset limits)
//
//  QR input is multiline (TextEditor); barcode input is single-line
//  (TextField, no suggestions) — matching the Android inputType flags.
//

import SwiftUI

struct CodeGeneratorView: View {

    let isQRCode: Bool
    let title: String

    @State private var content: String
    @State private var generatedImage: UIImage?
    @State private var lastEncodedContent = ""
    @State private var toastMessage: String?
    @State private var sharePayload: SharePayload?
    @FocusState private var inputFocused: Bool

    init(isQRCode: Bool, title: String) {
        self.isQRCode = isQRCode
        self.title = title
        // Same samples as strings.xml (generate_sample_*).
        _content = State(initialValue: isQRCode
            ? "https://github.com/Lanlan13-14/ZXingX"
            : "1234567890")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                formatChip

                Text("要编码的内容")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(uiColor: .secondaryLabel))

                inputCard

                Button {
                    generateFromInput(showEmptyToast: true)
                } label: {
                    Text("生成")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                if let image = generatedImage {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.none)
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .padding(16)
                        .background(
                            Color.white,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                        )

                    Text("当前内容：\(lastEncodedContent)")
                        .font(.system(size: 12))
                        .foregroundStyle(Color(uiColor: .secondaryLabel))
                        .frame(maxWidth: .infinity)

                    Button {
                        shareGenerated(image)
                    } label: {
                        Label("分享图片", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }
            }
            .padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            // CodeActivity: generateFromInput(showEmptyToast = false)
            generateFromInput(showEmptyToast: false)
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheet(activityItems: payload.items)
        }
        .overlay(alignment: .bottom) {
            if let message = toastMessage {
                ToastView(text: message)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: toastMessage)
    }

    // MARK: - Pieces

    private var formatChip: some View {
        Text(isQRCode ? "二维码 · QR_CODE" : "条形码 · CODE_128")
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(Color.accentColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.accentColor.opacity(0.12), in: Capsule())
    }

    private var inputCard: some View {
        Group {
            if isQRCode {
                TextEditor(text: $content)
                    .frame(minHeight: 96)
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("完成") { generateFromInput(showEmptyToast: true) }
                        }
                    }
            } else {
                TextField("例如订单号、商品编码…", text: $content)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.asciiCapable)
                    .onSubmit { generateFromInput(showEmptyToast: true) }
            }
        }
        .focused($inputFocused)
        .padding(12)
        .background(
            Color(uiColor: .secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
        )
        .overlay(
            Group {
                if isQRCode && content.isEmpty {
                    Text("例如网址、微信号、纯文本…")
                        .font(.system(size: 15))
                        .foregroundStyle(Color(uiColor: .placeholderText))
                        .padding(.leading, 16)
                        .padding(.top, 20)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        .allowsHitTesting(false)
                }
            }
        )
    }

    // MARK: - Actions (CodeActivity.generateFromInput / shareGeneratedCode)

    private func generateFromInput(showEmptyToast: Bool) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            if showEmptyToast { showToast("请先输入内容") }
            return
        }
        inputFocused = false // hideKeyboard()
        let image = isQRCode
            ? CodeGenerator.makeQRCode(content: trimmed, logo: UIImage(named: "Logo"))
            : CodeGenerator.makeCode128(content: trimmed)
        guard let image else {
            showToast(isQRCode
                ? "内容过长，无法生成二维码（约 2KB 以内更稳妥）。可缩短后再试。"
                : "条形码生成失败。CODE_128 对字符有限制，请改用数字/字母或改用二维码。")
            return
        }
        generatedImage = image
        lastEncodedContent = trimmed
    }

    private func shareGenerated(_ image: UIImage) {
        // Android shares image/png + EXTRA_TEXT with the encoded content.
        sharePayload = SharePayload(items: [image, lastEncodedContent])
    }

    private func showToast(_ message: String) {
        toastMessage = message
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if toastMessage == message { toastMessage = nil }
        }
    }
}
