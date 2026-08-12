//
//  ScannerView.swift
//  ZXingX (iOS)
//
//  Full-screen scanner page (pushed in the shared NavigationStack, nav bar
//  hidden — standard iOS camera-UI convention). Top overlay bar mirrors
//  toolbar_capture.xml: back chevron + centered white "ZXingX" title.
//
//  Result flow mirrors the Android activities exactly:
//  - continuous: result page pushes on top; popping it resumes analysis.
//    Payment QRs open the payment app and analysis resumes on foregrounding
//    (MultiFormatScanActivity.paymentAppOpened + onResume).
//  - qrBox / qrFullScreen (single-shot): the scanner replaces itself with the
//    result page (Android: setResult + finish, MainActivity opens the result
//    page) so swiping back from the result lands on Home. Payment QRs just
//    pop the scanner before jumping out.
//

import AVFoundation
import SwiftUI

struct ScannerView: View {

    let mode: ScanMode

    @EnvironmentObject private var router: Router
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var controller: CameraController

    init(mode: ScanMode) {
        self.mode = mode
        _controller = StateObject(wrappedValue: CameraController(mode: mode))
    }

    var body: some View {
        // Full-screen GeometryReader so overlay coordinates and the preview
        // layer's coordinate space (used for rectOfInterest conversion) match
        // exactly, including notch / home-indicator areas.
        GeometryReader { proxy in
            ZStack {
                Color.black

                CameraPreview(
                    session: controller.session,
                    onAttachLayer: { layer in controller.attachPreviewLayer(layer) },
                    onPinch: { scale, state in controller.handlePinch(scale: scale, phase: state) }
                )

                ViewfinderOverlay(
                    mode: mode,
                    previewSize: proxy.size,
                    resultPoint: controller.resultPoint,
                    torchOn: controller.isTorchOn,
                    torchAvailable: controller.isTorchAvailable,
                    onToggleTorch: { controller.setTorch(!controller.isTorchOn) }
                )

                // Front/back switch, bottom center (Android: ivSwitchCamera at
                // the bottom of zxl_camera_scan.xml). Only when the device has
                // both cameras.
                if controller.canSwitchCamera {
                    VStack {
                        Spacer()
                        Button {
                            controller.switchCamera()
                        } label: {
                            SwitchCameraIcon()
                                .frame(width: 26, height: 26)
                                .frame(width: 52, height: 52)
                                .background(.black.opacity(0.35), in: Circle())
                                .contentShape(Circle())
                        }
                        .accessibilityLabel("切换前置/后置摄像头")
                        .padding(.bottom, proxy.safeAreaInsets.bottom + 40)
                    }
                }

                topBar(topInset: proxy.safeAreaInsets.top)

                if controller.state == .unauthorized {
                    permissionCard
                }
            }
            .onChange(of: proxy.size) { newSize in
                controller.updateAnalysisRect(
                    previewBounds: CGRect(origin: .zero, size: newSize)
                )
            }
            .onAppear {
                controller.updateAnalysisRect(
                    previewBounds: CGRect(origin: .zero, size: proxy.size)
                )
            }
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            controller.onCodeScanned = { text, _ in handleDetected(text) }
            // Returning from a pushed result page lands here as well:
            // analysis resumes, matching setAnalyzeImage(true) on Android.
            controller.resumeAnalysis()
            controller.start()
        }
        .onDisappear {
            controller.stop()
        }
        .onChange(of: scenePhase) { phase in
            // Back from WeChat/Alipay/PayPal: resume scanning
            // (MultiFormatScanActivity.onResume with paymentAppOpened).
            if phase == .active {
                controller.resumeAnalysis()
            }
        }
    }

    // MARK: - Result routing

    private func handleDetected(_ text: String) {
        if PaymentQrRouter.openIfPaymentQr(text) {
            // Payment app opened. Single-shot scanners finish (Android).
            if !mode.isContinuous {
                router.pop()
            }
            return
        }
        switch mode {
        case .continuous:
            router.open(.result(text))
        case .qrBox:
            router.replaceTopWith(.result(text))
        case .qrFullScreen:
            // displayResultPoint(): let the marker flash briefly, then finish.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                router.replaceTopWith(.result(text))
            }
        }
    }

    // MARK: - Chrome

    private func topBar(topInset: CGFloat) -> some View {
        VStack {
            ZStack {
                Text("ZXingX")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                HStack {
                    Button {
                        router.pop()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("返回")
                    Spacer()
                }
            }
            .padding(.top, topInset + 8)
            Spacer()
        }
    }

    private var permissionCard: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.system(size: 40))
                .foregroundStyle(.white)
            Text("需要相机权限才能扫码")
                .font(.headline)
                .foregroundStyle(.white)
            Button("去设置开启") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 16))
    }
}
