//
//  CameraSelectionTests.swift
//  ZXingX (iOS) — ports CameraFacingPlanTest.kt to the iOS selection rules.
//
//  Pure logic only (device-type preference order + position toggle), so it
//  runs on a camera-less CI simulator: no AVCaptureSession is created.
//

import AVFoundation
import UIKit
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

    // MARK: - Preview rotation (iPad landscape)

    func testRotationAngleCoversAllInterfaceOrientations() {
        XCTAssertEqual(CameraPreview.rotationAngle(for: .portrait), 90)
        XCTAssertEqual(CameraPreview.rotationAngle(for: .portraitUpsideDown), 270)
        XCTAssertEqual(CameraPreview.rotationAngle(for: .landscapeLeft), 0)
        XCTAssertEqual(CameraPreview.rotationAngle(for: .landscapeRight), 180)
        XCTAssertEqual(CameraPreview.rotationAngle(for: .unknown), 90)
    }

    func testLandscapeAnglesDifferFromPortraitByNinetyDegrees() {
        // Portrait and landscape must be exactly a quarter-turn apart,
        // otherwise the iPad preview comes out squashed instead of rotated.
        let portrait = CameraPreview.rotationAngle(for: .portrait)
        let landscape = CameraPreview.rotationAngle(for: .landscapeRight)
        XCTAssertEqual(abs(portrait - landscape), 90)
    }

    // MARK: - Fill light routing (ports FillLightPlanTest.kt)

    func testCameraWithTorchUsesHardwareTorch() {
        XCTAssertFalse(CameraController.usesScreenFlash(hasTorch: true, isFront: false))
        XCTAssertFalse(CameraController.usesScreenFlash(hasTorch: true, isFront: true))
    }

    func testCameraWithoutTorchUsesScreenFlash() {
        XCTAssertTrue(CameraController.usesScreenFlash(hasTorch: false, isFront: true))
        XCTAssertTrue(CameraController.usesScreenFlash(hasTorch: false, isFront: false))
    }

    func testUnknownTorchFallsBackToPositionHeuristic() {
        XCTAssertTrue(CameraController.usesScreenFlash(hasTorch: nil, isFront: true))
        XCTAssertFalse(CameraController.usesScreenFlash(hasTorch: nil, isFront: false))
    }
}
