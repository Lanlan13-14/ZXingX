//
//  CameraController.swift
//  ZXingX (iOS)
//
//  AVFoundation barcode-scanning engine — the iOS-standard replacement for
//  the Android ZXingLite CameraScan + MultiFormatAnalyzer pipeline.
//
//  Android → iOS mapping:
//  - CameraX Preview + ImageAnalysis      → AVCaptureSession + AVCaptureMetadataOutput
//  - DecodeConfig hints                   → metadataObjectTypes (see ScanMode)
//  - DecodeConfig.areaRectRatio(0.8)      → metadataOutput.rectOfInterest
//  - CameraScan.setPlayBeep(true)         → BeepPlayer + success haptic
//  - CameraControl.setZoomRatio() pinch   → AVCaptureDevice.videoZoomFactor
//  - logical multi-camera / physical lens switchover (Camera2 interop)
//    → AVCaptureDevice virtual devices (Triple / DualWide / Dual): the system
//      switches constituent cameras automatically across the published optical
//      zoom factors (virtualDeviceSwitchOverVideoZoomFactors). No lens buttons,
//      no vendor/model hardcoding — identical product rule as Android.
//

import AVFoundation
import UIKit

@MainActor
final class CameraController: NSObject, ObservableObject {

    enum State: Equatable {
        case idle
        case requestingAuthorization
        case running
        case stopped
        case unauthorized
        case failed(String)
    }

    let mode: ScanMode

    /// Exposed read-only for the preview layer; configured on `sessionQueue`.
    let session = AVCaptureSession()

    @Published private(set) var state: State = .idle
    @Published private(set) var isTorchOn = false
    @Published private(set) var zoomFactor: CGFloat = 1
    @Published private(set) var zoomRange: ClosedRange<CGFloat> = 1...1

    /// Result marker shown by the full-screen scanner (displayResultPoint on
    /// Android). Preview-layer coordinates.
    @Published var resultPoint: CGPoint?

    /// Fired on the main actor once per detection while analysis is active.
    var onCodeScanned: (String, AVMetadataMachineReadableCodeObject?) -> Void = { _, _ in }

    private let sessionQueue = DispatchQueue(label: "com.lanlan13.zxingx.capture")
    private let metadataOutput = AVCaptureMetadataOutput()
    private var device: AVCaptureDevice?
    private weak var previewLayer: AVCaptureVideoPreviewLayer?

    /// Android CameraScan.setAnalyzeImage(false): detection callbacks are
    /// dropped while a result page or a payment app is covering the scanner.
    private var analysisSuspended = false

    private var pinchBaseZoom: CGFloat = 1

    init(mode: ScanMode) {
        self.mode = mode
        super.init()
    }

    // MARK: - Lifecycle

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndRun()
        case .notDetermined:
            state = .requestingAuthorization
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    guard let self else { return }
                    if granted {
                        self.configureAndRun()
                    } else {
                        self.state = .unauthorized
                    }
                }
            }
        default:
            state = .unauthorized
        }
    }

    func stop() {
        let session = self.session
        sessionQueue.async {
            if session.isRunning { session.stopRunning() }
        }
        state = .stopped
    }

    /// Android: cameraScan.setAnalyzeImage(true) after the result page closes.
    func resumeAnalysis() {
        analysisSuspended = false
        resultPoint = nil
    }

    // MARK: - Configuration (sessionQueue)

    private func configureAndRun() {
        let mode = self.mode
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let session = self.session
            if session.isRunning {
                Task { @MainActor in self.state = .running }
                return
            }

            session.beginConfiguration()
            session.sessionPreset = .high

            // Prefer the device's published logical multi-camera (Triple →
            // DualWide → Dual → Wide), exactly the Android selection rule.
            let discovery = AVCaptureDevice.DiscoverySession(
                deviceTypes: [
                    .builtInTripleCamera,
                    .builtInDualWideCamera,
                    .builtInDualCamera,
                    .builtInWideAngleCamera
                ],
                mediaType: .video,
                position: .back
            )
            guard let device = discovery.devices.first else {
                session.commitConfiguration()
                Task { @MainActor in self.state = .failed("no_camera") }
                return
            }

            do {
                let input = try AVCaptureDeviceInput(device: device)
                guard session.canAddInput(input) else {
                    session.commitConfiguration()
                    Task { @MainActor in self.state = .failed("input") }
                    return
                }
                session.addInput(input)
            } catch {
                session.commitConfiguration()
                Task { @MainActor in self.state = .failed("input") }
                return
            }

            guard session.canAddOutput(self.metadataOutput) else {
                session.commitConfiguration()
                Task { @MainActor in self.state = .failed("output") }
                return
            }
            session.addOutput(self.metadataOutput)
            // metadataObjectTypes must be set AFTER the output is added, and
            // limited to what the device reports as available.
            let wanted = mode.metadataObjectTypes
            self.metadataOutput.metadataObjectTypes =
                wanted.filter { self.metadataOutput.availableMetadataObjectTypes.contains($0) }
            self.metadataOutput.setMetadataObjectsDelegate(self, queue: .main)

            do {
                try device.lockForConfiguration()
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }
                device.unlockForConfiguration()
            } catch {
                // Focus tuning is best-effort; scanning still works.
            }

            // Commit BEFORE startRunning: configuration only takes effect on commit.
            session.commitConfiguration()
            self.device = device
            session.startRunning()

            Task { @MainActor in
                self.zoomRange = device.minAvailableVideoZoomFactor...device.maxAvailableVideoZoomFactor
                self.zoomFactor = device.videoZoomFactor
                self.isTorchOn = device.isTorchActive
                self.state = .running
                self.applyAnalysisRectIfNeeded()
            }
        }
    }

    // MARK: - Analysis rect (DecodeConfig.areaRectRatio)

    /// Called by the SwiftUI overlay whenever the preview geometry changes.
    /// rectOfInterest uses metadata coordinates; the preview layer performs
    /// the standard conversion.
    func updateAnalysisRect(previewBounds: CGRect) {
        guard mode.analysisAreaRatio != nil else { return }
        let side = min(previewBounds.width, previewBounds.height) * (mode.analysisAreaRatio ?? 1)
        let rect = CGRect(
            x: previewBounds.midX - side / 2,
            y: previewBounds.midY - side / 2,
            width: side,
            height: side
        )
        pendingAnalysisRect = rect
        applyAnalysisRectIfNeeded()
    }

    private var pendingAnalysisRect: CGRect?

    private func applyAnalysisRectIfNeeded() {
        guard state == .running,
              let rect = pendingAnalysisRect,
              let layer = previewLayer else { return }
        metadataOutput.rectOfInterest = layer.metadataOutputRectConverted(fromLayerRect: rect)
    }

    func attachPreviewLayer(_ layer: AVCaptureVideoPreviewLayer) {
        previewLayer = layer
        applyAnalysisRectIfNeeded()
    }

    // MARK: - Torch (flashlight button)

    func setTorch(_ on: Bool) {
        guard let device, device.hasTorch, device.isTorchAvailable else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                device.torchMode = on ? .on : .off
                device.unlockForConfiguration()
                Task { @MainActor in self.isTorchOn = on }
            } catch {
                // Torch unavailable right now — keep UI state unchanged.
            }
        }
    }

    // MARK: - Pinch zoom (双指缩放, multi-camera switchover is automatic)

    func handlePinch(scale: CGFloat, phase: UIGestureRecognizer.State) {
        guard let device else { return }
        switch phase {
        case .began:
            pinchBaseZoom = device.videoZoomFactor
        case .changed:
            let target = (pinchBaseZoom * scale)
                .clamped(to: zoomRange)
            guard abs(target - zoomFactor) > 0.001 else { return }
            sessionQueue.async {
                do {
                    try device.lockForConfiguration()
                    device.videoZoomFactor = target
                    device.unlockForConfiguration()
                    Task { @MainActor in self.zoomFactor = target }
                } catch {
                    // Ignore transient configuration races.
                }
            }
        default:
            break
        }
    }

    // MARK: - Detection (AVCaptureMetadataOutputObjectsDelegate, main queue)

    private func handleDetected(text: String, object: AVMetadataMachineReadableCodeObject?) {
        guard !analysisSuspended else { return }
        analysisSuspended = true // Android: setAnalyzeImage(false) on first hit

        if let object, let layer = previewLayer,
           let transformed = layer.transformedMetadataObject(for: object) {
            resultPoint = CGPoint(x: transformed.bounds.midX, y: transformed.bounds.midY)
        }

        BeepPlayer.play()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onCodeScanned(text, object)
    }
}

extension CameraController: AVCaptureMetadataOutputObjectsDelegate {
    nonisolated func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects
            .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
            .first,
              let text = object.stringValue,
              !text.isEmpty else { return }
        Task { @MainActor in
            self.handleDetected(text: text, object: object)
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
