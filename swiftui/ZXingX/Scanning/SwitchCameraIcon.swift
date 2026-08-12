//
//  SwitchCameraIcon.swift
//  ZXingX (iOS)
//
//  Self-drawn front/back camera switch glyph — the same geometry as the
//  Android zxl_ic_switch_camera.xml vector (solid camera body + top hump,
//  knocked-out lens and two circular arrows, even-odd fill on a 24x24 grid).
//  Not an SF Symbol; drawn as a Canvas so it matches the Android icon exactly.
//

import SwiftUI

struct SwitchCameraIcon: View {
    var body: some View {
        Canvas { context, size in
            let u = min(size.width, size.height) / 24
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: x * u, y: y * u)
            }
            var path = Path()
            path.move(to: pt(5.3, 8.5))
            path.addLine(to: pt(18.7, 8.5))
            path.addCurve(to: pt(20.8, 10.6), control1: pt(19.86, 8.5), control2: pt(20.8, 9.44))
            path.addLine(to: pt(20.8, 16.6))
            path.addCurve(to: pt(18.7, 18.7), control1: pt(20.8, 17.76), control2: pt(19.86, 18.7))
            path.addLine(to: pt(5.3, 18.7))
            path.addCurve(to: pt(3.2, 16.6), control1: pt(4.14, 18.7), control2: pt(3.2, 17.76))
            path.addLine(to: pt(3.2, 10.6))
            path.addCurve(to: pt(5.3, 8.5), control1: pt(3.2, 9.44), control2: pt(4.14, 8.5))
            path.closeSubpath()
            path.move(to: pt(9.5, 8.5))
            path.addLine(to: pt(10.5, 6.2))
            path.addLine(to: pt(13.5, 6.2))
            path.addLine(to: pt(14.5, 8.5))
            path.closeSubpath()
            path.move(to: pt(10.1, 13.6))
            path.addCurve(to: pt(12, 11.7), control1: pt(10.1, 12.55), control2: pt(10.95, 11.7))
            path.addCurve(to: pt(13.9, 13.6), control1: pt(13.05, 11.7), control2: pt(13.9, 12.55))
            path.addCurve(to: pt(12, 15.5), control1: pt(13.9, 14.65), control2: pt(13.05, 15.5))
            path.addCurve(to: pt(10.1, 13.6), control1: pt(10.95, 15.5), control2: pt(10.1, 14.65))
            path.closeSubpath()
            path.move(to: pt(8.52, 15))
            path.addCurve(to: pt(12, 17.35), control1: pt(9.1, 16.42), control2: pt(10.47, 17.35))
            path.addCurve(to: pt(15.48, 15), control1: pt(13.53, 17.35), control2: pt(14.9, 16.42))
            path.addLine(to: pt(15.37, 13.78))
            path.addLine(to: pt(14.41, 14.57))
            path.addCurve(to: pt(12, 16.2), control1: pt(14.01, 15.56), control2: pt(13.06, 16.2))
            path.addCurve(to: pt(9.59, 14.57), control1: pt(10.94, 16.2), control2: pt(9.99, 15.56))
            path.closeSubpath()
            path.move(to: pt(15.48, 12.2))
            path.addCurve(to: pt(12, 9.85), control1: pt(14.9, 10.78), control2: pt(13.53, 9.85))
            path.addCurve(to: pt(8.52, 12.2), control1: pt(10.47, 9.85), control2: pt(9.1, 10.78))
            path.addLine(to: pt(8.63, 13.42))
            path.addLine(to: pt(9.59, 12.63))
            path.addCurve(to: pt(12, 11), control1: pt(9.99, 11.64), control2: pt(10.94, 11))
            path.addCurve(to: pt(14.41, 12.63), control1: pt(13.06, 11), control2: pt(14.01, 11.64))
            path.closeSubpath()
            context.fill(path, with: .color(.white), style: FillStyle(eoFill: true))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

#Preview {
    ZStack {
        Color.black
        SwitchCameraIcon()
            .frame(width: 28, height: 28)
    }
}
