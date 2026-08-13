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

    /// Front/back switching (Android: ivSwitchCamera). Default is back, the
    /// button is only shown when the device reports both cameras, and the
    /// torch button hides while the front camera is active.
    @Published private(set) var isFrontCamera = false
    @Published private(set) var canSwitchCamera = false
    @Published private(set) var isTorchAvailable = false
    /// Screen flash (front-camera fill light): two white bars + full
    /// brightness while the active camera has no torch. Android parity:
    /// BarcodeCameraScanActivity.setScreenFlash.
    @Published private(set) var isScreenFlashOn = false
    private var savedScreenBrightness: CGFloat?

    /// Result marker shown by the full-screen scanner (displayResultPoint on
    /// Android). Preview-layer coordinates.
    @Published var resultPoint: CGPoint?

    /// Fired on the main actor once per detection while analysis is active.
    var onCodeScanned: (String, AVMetadataMachineReadableCodeObject?) -> Void = { _, _ in }

    private let sessionQueue = DispatchQueue(label: "com.lanlan13.zxingx.capture")
    private let metadataOutput = AVCaptureMetadataOutput()
    private var device: AVCaptureDevice?
    /// Position of the currently bound device; read/written on `sessionQueue`.
    private var activePosition: AVCaptureDevice.Position = .back
    private weak var previewLayer: AVCaptureVideoPreviewLayer?

    /// Bumps on every configureSession completion (success or fail). The
    /// scanner flip waits for this instead of a fixed delay so the second
    /// half of the card-flip never opens onto a black preview.
    @Published private(set) var sessionEpoch = 0

    /// Cached DiscoverySession results; camera presence does not change
    /// during a scan session, and rediscovering on every flip was extra
    /// session-queue work sitting on the critical path of a front switch.
    private var cachedHasBack: Bool?
    private var cachedHasFront: Bool?

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
        setScreenFlash(false) // 离开扫码页时恢复屏幕亮度
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
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if self.session.isRunning {
                Task { @MainActor in self.state = .running }
                return
            }
            self.configureSession(preferredPosition: self.activePosition)
        }
    }

    /// Device-type preference order per position.
    ///
    /// Back: published logical multi-camera first (Triple → DualWide → Dual →
    /// Wide), matching Android LogicalMultiCameraConfig.
    ///
    /// Front: Wide first, TrueDepth last. TrueDepth brings up the depth ISP
    /// (the same class of cost as AdaptiveCameraConfig on Android) and is
    /// what makes a front switch feel like the flip finished long before
    /// the picture arrived. QR scanning does not need depth.
    nonisolated static func deviceTypes(
        for position: AVCaptureDevice.Position
    ) -> [AVCaptureDevice.DeviceType] {
        switch position {
        case .back:
            return [
                .builtInTripleCamera,
                .builtInDualWideCamera,
                .builtInDualCamera,
                .builtInWideAngleCamera
            ]
        case .front:
            return [
                .builtInWideAngleCamera,
                .builtInTrueDepthCamera
            ]
        default:
            return [.builtInWideAngleCamera]
        }
    }

    /// Session preset per facing. Front cameras on phones rarely deliver a
    /// useful 1080p stream for a QR box; 720p is the first format they
    /// actually settle on and skips a negotiation that shows up as a black
    /// preview after the flip.
    nonisolated static func sessionPreset(
        for position: AVCaptureDevice.Position
    ) -> AVCaptureSession.Preset {
        position == .front ? .hd1280x720 : .high
    }

    /// Android CameraSwitchTiming.shouldReveal: the second half of the
    /// card-flip must not start until the first half is done AND the new
    /// session has published a frame (or the timeout fires).
    nonisolated static func shouldReveal(
        firstHalfDone: Bool,
        previewReady: Bool,
        elapsedMs: Int
    ) -> Bool {
        guard firstHalfDone else { return false }
        return previewReady || elapsedMs >= Self.revealTimeoutMs
    }

    nonisolated static let flipHalfDurationMs = 300
    nonisolated static let revealTimeoutMs = 1200
    nonisolated static let revealPollMs = 50
    /// Reference card perspective: 1200 / 320 = 3.75 → 1 / 3.75 ≈ 0.27,
    /// but the existing SwiftUI effect uses 0.4 to match the Android
    /// cameraDistance visually. Kept here so tests can lock it.
    nonisolated static let flipPerspective: CGFloat = 0.4

    nonisolated static func opposite(
        of position: AVCaptureDevice.Position
    ) -> AVCaptureDevice.Position {
        position == .front ? .back : .front
    }

    /// First device for `position`, falling back to the opposite position
    /// (Android CameraFacingPlan: a device whose preferred-facing camera is
    /// missing must still open SOMETHING instead of a black screen).
    private func discoverDevice(
        preferred position: AVCaptureDevice.Position
    ) -> (AVCaptureDevice, AVCaptureDevice.Position)? {
        let primary = AVCaptureDevice.DiscoverySession(
            deviceTypes: Self.deviceTypes(for: position),
            mediaType: .video,
            position: position
        )
        if let found = primary.devices.first { return (found, position) }
        let other = Self.opposite(of: position)
        let fallback = AVCaptureDevice.DiscoverySession(
            deviceTypes: Self.deviceTypes(for: other),
            mediaType: .video,
            position: other
        )
        if let found = fallback.devices.first { return (found, other) }
        return nil
    }

    private func hasAnyCamera(at position: AVCaptureDevice.Position) -> Bool {
        !AVCaptureDevice.DiscoverySession(
            deviceTypes: Self.deviceTypes(for: position),
            mediaType: .video,
            position: position
        ).devices.isEmpty
    }

    /// (Re)binds the session to the preferred position. Idempotent: existing
    /// inputs are removed first, so this is safe both for the first start,
    /// for restarts after stop(), and for live front/back switching while the
    /// session keeps running. Runs on `sessionQueue`.
    private func configureSession(preferredPosition: AVCaptureDevice.Position) {
        let session = self.session
        session.beginConfiguration()
        for input in session.inputs {
            session.removeInput(input)
        }

        guard let (device, usedPosition) = discoverDevice(preferred: preferredPosition) else {
            session.commitConfiguration()
            Task { @MainActor in
                self.state = .failed("no_camera")
                self.sessionEpoch += 1
            }
            return
        }

        do {
            let input = try AVCaptureDeviceInput(device: device)
            guard session.canAddInput(input) else {
                session.commitConfiguration()
                Task { @MainActor in
                    self.state = .failed("input")
                    self.sessionEpoch += 1
                }
                return
            }
            session.addInput(input)
            let preset = Self.sessionPreset(for: usedPosition)
            if session.canSetSessionPreset(preset) {
                session.sessionPreset = preset
            } else if session.canSetSessionPreset(.vga640x480) {
                session.sessionPreset = .vga640x480
            }
        } catch {
            session.commitConfiguration()
            Task { @MainActor in
                self.state = .failed("input")
                self.sessionEpoch += 1
            }
            return
        }

        if !session.outputs.contains(self.metadataOutput) {
            guard session.canAddOutput(self.metadataOutput) else {
                session.commitConfiguration()
                Task { @MainActor in
                    self.state = .failed("output")
                    self.sessionEpoch += 1
                }
                return
            }
            session.addOutput(self.metadataOutput)
        }
        // metadataObjectTypes must be set AFTER the output is added, and
        // limited to what the current input reports as available.
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
        self.activePosition = usedPosition
        let hasBack = self.cachedCameraPresence(at: .back)
        let hasFront = self.cachedCameraPresence(at: .front)
        let switchable = hasBack && hasFront
        if !session.isRunning {
            session.startRunning()
        }

        Task { @MainActor in
            self.setScreenFlash(false) // 换镜头后原补光状态不再有效
            self.isFrontCamera = usedPosition == .front
            self.canSwitchCamera = switchable
            self.isTorchAvailable = device.hasTorch
            self.isTorchOn = false
            self.zoomRange = device.minAvailableVideoZoomFactor...device.maxAvailableVideoZoomFactor
            self.zoomFactor = device.videoZoomFactor
            self.resultPoint = nil
            self.state = .running
            self.sessionEpoch += 1
            self.applyAnalysisRectIfNeeded()
        }
    }

    private func cachedCameraPresence(at position: AVCaptureDevice.Position) -> Bool {
        switch position {
        case .back:
            if let cached = cachedHasBack { return cached }
            let found = hasAnyCamera(at: .back)
            cachedHasBack = found
            return found
        case .front:
            if let cached = cachedHasFront { return cached }
            let found = hasAnyCamera(at: .front)
            cachedHasFront = found
            return found
        default:
            return hasAnyCamera(at: position)
        }
    }

    // MARK: - Front/back switch (ivSwitchCamera on Android)

    /// Toggles between the front and back camera. No-op unless the device
    /// actually has both (same rule that shows the Android switch button).
    func switchCamera() {
        guard canSwitchCamera, state == .running else { return }
        let target: AVCaptureDevice.Position = isFrontCamera ? .back : .front
        sessionQueue.async { [weak self] in
            self?.configureSession(preferredPosition: target)
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

    /// Flashlight button routing (Android FillLightPlan): a camera with a
    /// flash unit drives the hardware torch; one without (front cameras)
    /// toggles the screen flash instead. `hasTorch` is nil while binding —
    /// then the position heuristic decides (front cameras never have a torch).
    nonisolated static func usesScreenFlash(hasTorch: Bool?, isFront: Bool) -> Bool {
        let has = hasTorch ?? !isFront
        return !has
    }

    func setTorch(_ on: Bool) {
        if Self.usesScreenFlash(hasTorch: device?.hasTorch, isFront: isFrontCamera) {
            setScreenFlash(on)
            return
        }
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

    /// Front-facing fill light: white bars overlay (ScannerView) plus maximum
    /// screen brightness; the previous brightness is restored on off.
    func setScreenFlash(_ on: Bool) {
        guard on != isScreenFlashOn else { return }
        isScreenFlashOn = on
        if on {
            if savedScreenBrightness == nil {
                savedScreenBrightness = UIScreen.main.brightness
            }
            UIScreen.main.brightness = 1.0
        } else {
            if let saved = savedScreenBrightness {
                UIScreen.main.brightness = saved
                savedScreenBrightness = nil
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
