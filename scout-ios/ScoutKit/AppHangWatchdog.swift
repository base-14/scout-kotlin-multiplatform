import Foundation
import Scout

/// Main-thread hang / ANR watchdog. A background queue posts a heartbeat onto the main queue
/// every `pollInterval`; if the main thread doesn't service it within `threshold`, the hang is
/// reported once (SIGKILL may arrive before recovery, so we fire at threshold-cross, not on
/// recovery). Adapted from the scout-flutter iOS `AppHangWatchdog`.
///
/// Follow-up: capture the hung main-thread stack via a mach `thread_get_state` frame-pointer
/// unwind (scout-flutter's `ThreadBacktrace`); for now the ANR carries the duration only.
final class AppHangWatchdog {
    static let shared = AppHangWatchdog()

    private let queue = DispatchQueue(label: "io.base14.scout.hang", qos: .userInitiated)
    private var running = false
    private var thresholdMs: Double = 5000
    private var pollIntervalMs: Double = 1000
    private var lastHeartbeatNs: UInt64 = 0
    private var inHang = false
    private var mainThreadPort: thread_t = thread_t(MACH_PORT_NULL)

    /// Call on the main thread (Scout.start runs at launch): captures the main thread's mach
    /// port so the watchdog can backtrace it from its background queue when it hangs.
    func start(thresholdMs: Double) {
        guard !running else { return }
        running = true
        self.thresholdMs = thresholdMs
        self.pollIntervalMs = max(200, thresholdMs / 5)
        mainThreadPort = ScoutThreadBacktrace.currentPort()
        lastHeartbeatNs = DispatchTime.now().uptimeNanoseconds
        queue.async { [weak self] in self?.loop() }
    }

    func stop() { running = false }

    private func loop() {
        while running {
            DispatchQueue.main.async { [weak self] in
                self?.lastHeartbeatNs = DispatchTime.now().uptimeNanoseconds
            }
            Thread.sleep(forTimeInterval: pollIntervalMs / 1000.0)
            let nowNs = DispatchTime.now().uptimeNanoseconds
            let elapsedMs = Double(nowNs &- lastHeartbeatNs) / 1_000_000.0
            if elapsedMs >= thresholdMs {
                if !inHang {
                    inHang = true
                    let stack = ScoutThreadBacktrace.capture(mainThreadPort).joined(separator: "\n")
                    ScoutEngine.shared.reportAnr(durationMs: Int64(elapsedMs), mainThreadStack: stack)
                }
            } else {
                inHang = false
            }
        }
    }
}
