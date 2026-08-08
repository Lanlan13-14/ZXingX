//
//  ZXingXApp.swift
//  ZXingX (iOS)
//
//  SwiftUI port of the ZXingX Android app (github.com/Lanlan13-14/ZXingX).
//  Same screens and features, native iOS interactions and platform APIs:
//  AVFoundation live scanning, Vision image decoding, CoreImage code
//  generation, PhotosUI picker, UIActivityViewController sharing, Home
//  Screen quick action, system light/dark appearance, native swipe-back.
//

import SwiftUI

@main
struct ZXingXApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var router = Router()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(router)
                .onAppear {
                    // Let the AppDelegate flush a pending quick action.
                    appDelegate.router = router
                }
        }
    }
}
