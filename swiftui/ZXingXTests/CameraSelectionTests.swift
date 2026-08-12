//
//  CameraSelectionTests.swift
//  ZXingX (iOS) — ports CameraFacingPlanTest.kt to the iOS selection rules.
//
//  Pure logic only (device-type preference order + position toggle), so it
//  runs on a camera-less CI simulator: no AVCaptureSession is created.
//

import AVFoundation
import XCTest
@testable import ZXingX

final class CameraSelectionTests: XCTestCase {

    func testBackPrefersLogicalMultiCameraThenWideAngle() {
        XCTAssertEqual(
            CameraController.deviceTypes(for: .back),
            [
                .builtInTripleCamera,
                .builtInDualWideCamera,
                .builtInDualCamera,
                .builtInWideAngleCamera
            ]
        )
    }

    func testFrontPrefersTrueDepthThenWideAngle() {
        XCTAssertEqual(
            CameraController.deviceTypes(for: .front),
            [
                .builtInTrueDepthCamera,
                .builtInWideAngleCamera
            ]
        )
    }

    func testWideAngleIsAlwaysTheLastResort() {
        // The fallback chain must always end at the universally present
        // wide-angle camera, for both positions.
        XCTAssertEqual(CameraController.deviceTypes(for: .back).last, .builtInWideAngleCamera)
        XCTAssertEqual(CameraController.deviceTypes(for: .front).last, .builtInWideAngleCamera)
    }

    func testOppositeMapsBackToFrontAndFrontToBack() {
        XCTAssertEqual(CameraController.opposite(of: .back), .front)
        XCTAssertEqual(CameraController.opposite(of: .front), .back)
    }

    func testEveryPositionHasANonEmptyTypeList() {
        for position in [AVCaptureDevice.Position.back, .front, .unspecified] {
            XCTAssertFalse(CameraController.deviceTypes(for: position).isEmpty)
        }
    }
}
