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

    func testFrontPrefersWideAngleThenTrueDepth() {
        // Wide first: TrueDepth brings up the depth ISP and is the slow
        // path the user sees as "flip finished, picture still black."
        // QR scanning does not need depth.
        XCTAssertEqual(
            CameraController.deviceTypes(for: .front),
            [
                .builtInWideAngleCamera,
                .builtInTrueDepthCamera
            ]
        )
    }

    func testFrontSessionPresetIs720pNotHigh() {
        XCTAssertEqual(CameraController.sessionPreset(for: .front), .hd1280x720)
        XCTAssertEqual(CameraController.sessionPreset(for: .back), .high)
    }

    func testBackFallbackEndsAtWideAngle() {
        XCTAssertEqual(CameraController.deviceTypes(for: .back).last, .builtInWideAngleCamera)
    }

    func testFrontFallbackEndsAtTrueDepth() {
        // Wide is preferred; TrueDepth is last-resort only (depth ISP is slow
        // and unused for QR). A TrueDepth-only front camera still opens.
        XCTAssertEqual(CameraController.deviceTypes(for: .front).last, .builtInTrueDepthCamera)
        XCTAssertEqual(CameraController.deviceTypes(for: .front).first, .builtInWideAngleCamera)
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

    // MARK: - Flip reveal (ports CameraSwitchTimingTest.kt)

    func testDoesNotRevealBeforeFirstHalfEvenIfPreviewReady() {
        XCTAssertFalse(CameraController.shouldReveal(firstHalfDone: false, previewReady: true, elapsedMs: 2000))
        XCTAssertFalse(CameraController.shouldReveal(
            firstHalfDone: false,
            previewReady: true,
            elapsedMs: CameraController.revealTimeoutMs
        ))
    }

    func testRevealsWhenFirstHalfDoneAndPreviewReady() {
        XCTAssertTrue(CameraController.shouldReveal(
            firstHalfDone: true,
            previewReady: true,
            elapsedMs: CameraController.flipHalfDurationMs
        ))
    }

    func testRevealsOnTimeoutAfterFirstHalfWithoutAFrame() {
        XCTAssertTrue(CameraController.shouldReveal(
            firstHalfDone: true,
            previewReady: false,
            elapsedMs: CameraController.revealTimeoutMs
        ))
    }

    func testDoesNotRevealJustBeforeTimeoutWithoutAFrame() {
        XCTAssertFalse(CameraController.shouldReveal(
            firstHalfDone: true,
            previewReady: false,
            elapsedMs: CameraController.revealTimeoutMs - 1
        ))
    }

    func testTimeoutIsLongerThanOneFlipHalf() {
        XCTAssertGreaterThan(CameraController.revealTimeoutMs, CameraController.flipHalfDurationMs)
    }
}
