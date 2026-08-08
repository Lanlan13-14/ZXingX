//
//  ShareSheet.swift
//  ZXingX (iOS)
//
//  UIActivityViewController bridge — the standard iOS share sheet,
//  replacing Android's Intent.createChooser(ACTION_SEND).
//

import SwiftUI
import UIKit

struct ShareSheet: UIViewControllerRepresentable {

    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
