//
//  Router.swift
//  ZXingX (iOS)
//
//  Single source of truth for navigation. Android analogs:
//  - MainActivity.startScan / startGenerateCodeActivity → open(_:)
//  - single-shot scanner setResult+finish then MainActivity opens the result
//    page → replaceTopWith(_:) (result's back-swipe lands on Home)
//  - QuickScanTileService → openQuickScan()
//

import SwiftUI

enum Route: Hashable {
    case scan(ScanMode)
    case result(String)
    case generateQR
    case generateBarcode
}

@MainActor
final class Router: ObservableObject {

    @Published var path = NavigationPath()

    func open(_ route: Route) {
        path.append(route)
    }

    func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    /// Android single-shot finish(): drop the scanner, push the result.
    /// Both mutations happen in the same runloop so NavigationStack coalesces
    /// them into one transition.
    func replaceTopWith(_ route: Route) {
        if !path.isEmpty { path.removeLast() }
        path.append(route)
    }

    /// Quick action "扫一扫" (Quick Settings tile on Android) → continuous scan.
    func openQuickScan() {
        path.append(Route.scan(.continuous))
    }
}
