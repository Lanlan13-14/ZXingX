//
//  CameraPreview.swift
//  ZXingX (iOS)
//
//  AVCaptureVideoPreviewLayer wrapped for SwiftUI (UIViewRepresentable is the
//  standard bridge). Hosts the pinch gesture that drives videoZoomFactor.
//

import AVFoundation
import SwiftUI

struct CameraPreview: UIViewRepresentable {

    final class PreviewView: UIView {
        override class var layerClass: AnyClass {
            AVCaptureVideoPreviewLayer.self
        }

        var previewLayer: AVCaptureVideoPreviewLayer {
            // layerClass guarantees the cast.
            // swiftlint:disable:next force_cast
            layer as! AVCaptureVideoPreviewLayer
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            // Keep metadata/preview orientation aligned with the interface.
            // layoutSubviews fires on every rotation (frame change), which is
            // what makes iPad landscape work without a notification observer.
            guard let connection = previewLayer.connection else { return }
            let orientation = window?.windowScene?.interfaceOrientation ?? .portrait
            let angle = CameraPreview.rotationAngle(for: orientation)
            if connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
        }
    }

    /// videoRotationAngle for a given interface orientation.
    ///
    /// The camera buffer is sensor-native landscape; the angle counts the
    /// clockwise rotation needed to make it upright. Anchor: portrait = 90.
    /// UIInterfaceOrientation is name-mirrored against UIDeviceOrientation
    /// (UI.landscapeLeft is the same physical pose as Device.landscapeRight),
    /// which yields this table:
    ///   portrait → 90, portraitUpsideDown → 270, landscapeLeft → 0, landscapeRight → 180
    static func rotationAngle(for orientation: UIInterfaceOrientation) -> CGFloat {
        switch orientation {
        case .portraitUpsideDown: return 270
        case .landscapeLeft: return 0
        case .landscapeRight: return 180
        default: return 90 // .portrait / .unknown
        }
    }

    final class Coordinator {
        var onPinch: (CGFloat, UIGestureRecognizer.State) -> Void

        init(onPinch: @escaping (CGFloat, UIGestureRecognizer.State) -> Void) {
            self.onPinch = onPinch
        }

        @objc func pinched(_ gesture: UIPinchGestureRecognizer) {
            onPinch(gesture.scale, gesture.state)
        }
    }

    let session: AVCaptureSession
    let onAttachLayer: (AVCaptureVideoPreviewLayer) -> Void
    let onPinch: (CGFloat, UIGestureRecognizer.State) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPinch: onPinch)
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.backgroundColor = .black
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill

        let pinch = UIPinchGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.pinched(_:))
        )
        view.addGestureRecognizer(pinch)

        // Hand the layer to the controller for rectOfInterest / result-point
        // coordinate conversion. Already on the main thread here.
        onAttachLayer(view.previewLayer)
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        if uiView.previewLayer.session !== session {
            uiView.previewLayer.session = session
        }
    }
}
