//
//  BeepPlayer.swift
//  ZXingX (iOS)
//
//  CameraScan.setPlayBeep(true) equivalent. Plays a short synthesized beep
//  through AVAudioPlayer (no undocumented AudioServices sound IDs, no bundled
//  asset — the 16-bit PCM WAV is generated in memory).
//

import AVFoundation
import Foundation

enum BeepPlayer {

    private static var player: AVAudioPlayer?

    @MainActor
    static func play() {
        if player == nil {
            player = try? AVAudioPlayer(data: makeBeepWAV())
            player?.prepareToPlay()
        }
        player?.currentTime = 0
        player?.play()
    }

    /// 1050 Hz sine, 120 ms, 44.1 kHz mono PCM16, short fades to avoid clicks.
    static func makeBeepWAV(
        frequency: Double = 1050,
        duration: Double = 0.12,
        sampleRate: Int = 44100
    ) -> Data {
        let sampleCount = Int(Double(sampleRate) * duration)
        let fadeSamples = 64
        var pcm = [UInt8]()
        pcm.reserveCapacity(sampleCount * 2)

        for i in 0..<sampleCount {
            var amplitude = 0.6
            if i < fadeSamples {
                amplitude *= Double(i) / Double(fadeSamples)
            } else if i >= sampleCount - fadeSamples {
                amplitude *= Double(sampleCount - 1 - i) / Double(fadeSamples)
            }
            let phase = 2.0 * Double.pi * frequency * Double(i) / Double(sampleRate)
            let value = Int16(sin(phase) * amplitude * Double(Int16.max))
            pcm.append(UInt8(truncatingIfNeeded: value))
            pcm.append(UInt8(truncatingIfNeeded: value >> 8))
        }

        let dataSize = UInt32(pcm.count)
        let byteRate = UInt32(sampleRate * 2)
        var wav = Data()
        wav.append(contentsOf: [0x52, 0x49, 0x46, 0x46]) // "RIFF"
        wav.appendLE(36 + dataSize)
        wav.append(contentsOf: [0x57, 0x41, 0x56, 0x45]) // "WAVE"
        wav.append(contentsOf: [0x66, 0x6D, 0x74, 0x20]) // "fmt "
        wav.appendLE(UInt32(16))                           // PCM chunk size
        wav.appendLE(UInt16(1))                            // PCM format
        wav.appendLE(UInt16(1))                            // mono
        wav.appendLE(UInt32(sampleRate))
        wav.appendLE(byteRate)
        wav.appendLE(UInt16(2))                            // block align
        wav.appendLE(UInt16(16))                           // bits per sample
        wav.append(contentsOf: [0x64, 0x61, 0x74, 0x61]) // "data"
        wav.appendLE(dataSize)
        wav.append(contentsOf: pcm)
        return wav
    }
}

private extension Data {
    mutating func appendLE(_ value: UInt32) {
        // Swift.-qualified: Data has an instance method with the same base name.
        Swift.withUnsafeBytes(of: value.littleEndian) { append(contentsOf: $0) }
    }

    mutating func appendLE(_ value: UInt16) {
        Swift.withUnsafeBytes(of: value.littleEndian) { append(contentsOf: $0) }
    }
}
