//
//  AppDelegate.swift
//  ZXingX (iOS)
//
//  Handles the Home Screen quick action "扫一扫" — the iOS counterpart of
//  the Android Quick Settings tile (QuickScanTileService), which jumps
//  straight into continuous scanning.
//
//  Delivery paths covered:
//  - cold start: launchOptions[.shortcutItem] / scene connection options
//  - warm tap:   application(_:performActionFor:completionHandler:)
//

import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {

    /// Quick action type declared in Info.plist UIApplicationShortcutItems.
    static let quickScanType = "com.lanlan13.zxingx.quickscan"

    /// Attached by ZXingXApp once the view tree owns the Router.
    var router: Router? {
        didSet { flushPendingIfPossible() }
    }

    private var pendingQuickScan = false

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if let item = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem,
           item.type == AppDelegate.quickScanType {
            pendingQuickScan = true
            // Return false so the system does not also invoke performActionFor.
            return false
        }
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let item = options.shortcutItem, item.type == AppDelegate.quickScanType {
            pendingQuickScan = true
        }
        let config = UISceneConfiguration(
            name: nil,
            sessionRole: connectingSceneSession.role
        )
        return config
    }

    func application(
        _ application: UIApplication,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        guard shortcutItem.type == AppDelegate.quickScanType else {
            completionHandler(false)
            return
        }
        pendingQuickScan = true
        flushPendingIfPossible()
        completionHandler(true)
    }

    private func flushPendingIfPossible() {
        guard pendingQuickScan, let router else { return }
        pendingQuickScan = false
        Task { @MainActor in router.openQuickScan() }
    }
}
