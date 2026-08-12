//
//  ViewfinderOverlay.swift
//  ZXingX (iOS)
//
//  SwiftUI rendering of ZXingLite's ViewfinderView styles:
//  - boxed (扫二维码 / 连续扫码): dimmed mask outside the frame, corner
//    brackets, tip label below (vvLabelTextLocation="bottom"), laser sweep.
//    qrBox uses the grid laser (vvLaserStyle="grid"), continuous the line laser.
//  - fullScreen (全屏识别): ViewfinderStyle.POPULAR — large corner frame close
//    to the screen edges, sweeping line, no dim mask (full-area scan).
//  - Flashlight button under the frame (activity_qrcode_scan.xml ivFlashlight).
//

import SwiftUI

struct ViewfinderOverlay: View {

    let mode: ScanMode
    let previewSize: CGSize
    let resultPoint: CGPoint?
    let torchOn: Bool
    /// Whether the flashlight button can do anything on the active camera:
    /// hardware torch on the back, screen flash on the front. Only a camera
    /// with neither (rare) hides the button.
    let fillLightAvailable: Bool
    let onToggleTorch: () -> Void

    /// Visible frame of the classic viewfinder: ViewfinderView's framing rect
    /// is a centered square at 5/8 of the short edge (ZXingLite default).
    private let frameRatio: CGFloat = 0.625

    @State private var laserProgress: CGFloat = 0

    var body: some View {
        let frame = frameRect(in: previewSize)
        ZStack {
            if mode == .qrFullScreen {
                popularFrame(frame)
                laserLine(in: frame)
            } else {
                dimMask(hole: frame)
                cornerBrackets(frame)
                if mode == .qrBox {
                    gridLaser(in: frame)
                } else {
                    laserLine(in: frame)
                }
                if mode == .qrBox {
                    Text("将二维码放入框内，即可自动扫描")
                        .font(.system(size: 14))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.6), radius: 2, y: 1)
                        .position(x: frame.midX, y: frame.maxY + 24)
                }
            }

            if let point = resultPoint {
                Circle()
                    .fill(Color.accentColor)
                    .frame(width: 20, height: 20)
                    .overlay(Circle().stroke(.white, lineWidth: 2))
                    .position(point)
            }

            if fillLightAvailable {
                torchButton(below: frame)
            }
        }
        .onAppear { startLaser() }
    }

    // MARK: - Geometry

    private func frameRect(in size: CGSize) -> CGRect {
        guard size.width > 0, size.height > 0 else { return .zero }
        if mode == .qrFullScreen {
            // POPULAR: wide frame, nearly edge-to-edge, vertically centered.
            let side = min(size.width * 0.86, size.height * 0.6)
            return CGRect(
                x: (size.width - side) / 2,
                y: (size.height - side) / 2,
                width: side,
                height: side
            )
        }
        let side = min(size.width, size.height) * frameRatio
        return CGRect(
            x: (size.width - side) / 2,
            y: (size.height - side) / 2,
            width: side,
            height: side
        )
    }

    // MARK: - Pieces

    private func dimMask(hole: CGRect) -> some View {
        Rectangle()
            .fill(Color.black.opacity(0.45))
            .mask(
                ZStack {
                    Rectangle()
                    RoundedRectangle(cornerRadius: 4)
                        .frame(width: hole.width, height: hole.height)
                        .position(x: hole.midX, y: hole.midY)
                        .blendMode(.destinationOut)
                }
                .compositingGroup()
            )
    }

    private func cornerBrackets(_ frame: CGRect, color: Color = .white) -> some View {
        Canvas { context, _ in
            let arm: CGFloat = 24
            let width: CGFloat = 4
            var path = Path()
            let left = frame.minX, right = frame.maxX
            let top = frame.minY, bottom = frame.maxY
            // top-left
            path.move(to: CGPoint(x: left, y: top + arm)); path.addLine(to: CGPoint(x: left, y: top)); path.addLine(to: CGPoint(x: left + arm, y: top))
            // top-right
            path.move(to: CGPoint(x: right - arm, y: top)); path.addLine(to: CGPoint(x: right, y: top)); path.addLine(to: CGPoint(x: right, y: top + arm))
            // bottom-left
            path.move(to: CGPoint(x: left, y: bottom - arm)); path.addLine(to: CGPoint(x: left, y: bottom)); path.addLine(to: CGPoint(x: left + arm, y: bottom))
            // bottom-right
            path.move(to: CGPoint(x: right - arm, y: bottom)); path.addLine(to: CGPoint(x: right, y: bottom)); path.addLine(to: CGPoint(x: right, y: bottom - arm))
            context.stroke(path, with: .color(color), lineWidth: width)
        }
    }

    private func popularFrame(_ frame: CGRect) -> some View {
        cornerBrackets(frame, color: .white)
    }

    private func laserLine(in frame: CGRect) -> some View {
        let y = frame.minY + 8 + (frame.height - 16) * laserProgress
        return RoundedRectangle(cornerRadius: 1)
            .fill(Color.accentColor)
            .frame(width: frame.width - 24, height: 2)
            .shadow(color: Color.accentColor.opacity(0.8), radius: 4)
            .position(x: frame.midX, y: y)
    }

    private func gridLaser(in frame: CGRect) -> some View {
        // vvLaserStyle="grid": a small grid block sweeping vertically.
        let gridHeight: CGFloat = 56
        let y = frame.minY + 8 + (frame.height - 16 - gridHeight) * laserProgress + gridHeight / 2
        return Canvas { context, _ in
            var path = Path()
            let step: CGFloat = 8
            let left = frame.minX + 12, right = frame.maxX - 12
            let top = y - gridHeight / 2, bottom = y + gridHeight / 2
            var x = left
            while x <= right {
                path.move(to: CGPoint(x: x, y: top))
                path.addLine(to: CGPoint(x: x, y: bottom))
                x += step
            }
            var gy = top
            while gy <= bottom {
                path.move(to: CGPoint(x: left, y: gy))
                path.addLine(to: CGPoint(x: right, y: gy))
                gy += step
            }
            context.stroke(path, with: .color(.accentColor.opacity(0.55)), lineWidth: 0.5)
        }
    }

    private func torchButton(below frame: CGRect) -> some View {
        Button(action: onToggleTorch) {
            Image(systemName: torchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                .font(.system(size: 22))
                .foregroundStyle(torchOn ? Color.accentColor : .white)
                .frame(width: 52, height: 52)
                .background(.black.opacity(0.35), in: Circle())
        }
        .accessibilityLabel("闪光灯")
        .position(x: frame.midX, y: frame.maxY + (mode == .qrBox ? 96 : 72))
    }

    // MARK: - Animation

    private func startLaser() {
        laserProgress = 0
        withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: true)) {
            laserProgress = 1
        }
    }
}
