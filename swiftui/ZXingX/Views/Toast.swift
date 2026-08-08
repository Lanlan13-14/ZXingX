//
//  Toast.swift
//  ZXingX (iOS)
//
//  Minimal transient toast — the standard iOS stand-in for Android Toast.
//

import SwiftUI

struct ToastView: View {

    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.black.opacity(0.75), in: Capsule())
            .padding(.bottom, 32)
    }
}
