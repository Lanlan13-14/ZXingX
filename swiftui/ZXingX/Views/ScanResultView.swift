//
//  ScanResultView.swift
//  ZXingX (iOS)
//
//  Port of ScanResultActivity: nav title 扫描结果 (system back chevron +
//  native edge-swipe replace the custom EdgeSwipeBackController), content
//  card with the full text, type chip (链接/文本), 复制 / 分享 row and a
//  full-width 打开 button for URLs only.
//  Copy feedback uses a transient toast (iOS has no Toast; light overlay is
//  the standard approach).
//

import SwiftUI

struct ScanResultView: View {

    let text: String

    @State private var showCopiedToast = false
    @State private var shareItem: SharePayload?

    private var isURL: Bool { ScanResultLogic.isHttpURL(text) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                typeChip

                Text(ScanResultLogic.displayText(text, emptyFallback: "未识别到有效内容"))
                    .font(.system(size: 16))
                    .foregroundStyle(Color(uiColor: .label))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(
                        Color(uiColor: .secondarySystemGroupedBackground),
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                    )

                HStack(spacing: 12) {
                    Button {
                        copyResult()
                    } label: {
                        Label("复制", systemImage: "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        shareResult()
                    } label: {
                        Label("分享", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }

                if isURL {
                    Button {
                        openResult()
                    } label: {
                        Label("打开", systemImage: "safari")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("扫描结果")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $shareItem) { item in
            ShareSheet(activityItems: item.items)
        }
        .overlay(alignment: .bottom) {
            if showCopiedToast {
                ToastView(text: "已复制到剪贴板")
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: showCopiedToast)
    }

    private var typeChip: some View {
        Text(isURL ? "链接" : "文本")
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(Color.accentColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                Color.accentColor.opacity(0.12),
                in: Capsule()
            )
    }

    // MARK: - Actions (ScanResultActivity.copyResult / shareResult / openResult)

    private func copyResult() {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        UIPasteboard.general.string = text
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        showCopiedToast = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            showCopiedToast = false
        }
    }

    private func shareResult() {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        shareItem = SharePayload(items: [text])
    }

    private func openResult() {
        guard isURL else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed) else { return }
        UIApplication.shared.open(url)
    }
}

/// Identifiable wrapper so .sheet(item:) re-triggers for repeated shares
/// of identical strings.
struct SharePayload: Identifiable {
    let id = UUID()
    let items: [Any]
}
