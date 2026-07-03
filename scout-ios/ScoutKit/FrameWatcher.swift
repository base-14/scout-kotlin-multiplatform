import UIKit
import QuartzCore
import Scout

/// Detects janky frames via a main-runloop `CADisplayLink`. A frame interval past the long-task
/// threshold emits `long_task`; past the frozen threshold emits `frozen_frame`.
final class FrameWatcher {
    static let shared = FrameWatcher()

    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private let longTaskMs: Double = 100
    private let frozenMs: Double = 700

    func start() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        lastTimestamp = 0
    }

    @objc private func tick(_ link: CADisplayLink) {
        defer { lastTimestamp = link.timestamp }
        guard lastTimestamp != 0 else { return }
        let frameMs = (link.timestamp - lastTimestamp) * 1000.0
        if frameMs >= frozenMs {
            ScoutEngine.shared.reportFrozenFrame(durationMs: Int64(frameMs))
        } else if frameMs >= longTaskMs {
            ScoutEngine.shared.reportLongTask(durationMs: Int64(frameMs))
        }
    }
}
