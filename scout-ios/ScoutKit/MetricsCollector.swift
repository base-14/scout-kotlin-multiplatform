import Foundation
import Darwin
import Scout

/// Periodically samples process memory (resident size) and CPU usage via mach and emits them as
/// gauges through the engine. Runs on a background timer so it fires regardless of runloop state.
final class MetricsCollector {
    static let shared = MetricsCollector()

    private var timer: DispatchSourceTimer?
    private let queue = DispatchQueue(label: "io.base14.scout.metrics", qos: .utility)
    private var memoryEnabled = false
    private var cpuEnabled = false

    func start(memoryEnabled: Bool, cpuEnabled: Bool, intervalSeconds: Int) {
        guard timer == nil else { return }
        guard memoryEnabled || cpuEnabled else { return }
        self.memoryEnabled = memoryEnabled
        self.cpuEnabled = cpuEnabled
        let t = DispatchSource.makeTimerSource(queue: queue)
        t.schedule(deadline: .now() + 1, repeating: .seconds(max(1, intervalSeconds)))
        t.setEventHandler { [weak self] in self?.sample() }
        timer = t
        t.resume()
    }

    func stop() {
        timer?.cancel()
        timer = nil
    }

    private func sample() {
        if memoryEnabled, let bytes = residentMemoryBytes() {
            ScoutEngine.shared.emitGauge(name: "process.memory.usage", value: bytes, unit: "By")
        }
        if cpuEnabled {
            ScoutEngine.shared.emitGauge(name: "process.cpu.usage", value: cpuUsagePercent(), unit: "1")
        }
    }

    private func residentMemoryBytes() -> Double? {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
        let kr = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        return kr == KERN_SUCCESS ? Double(info.resident_size) : nil
    }

    private func cpuUsagePercent() -> Double {
        var threadsList: thread_act_array_t?
        var threadCount: mach_msg_type_number_t = 0
        guard task_threads(mach_task_self_, &threadsList, &threadCount) == KERN_SUCCESS,
              let threads = threadsList else { return 0 }
        defer {
            vm_deallocate(mach_task_self_,
                          vm_address_t(UInt(bitPattern: threads)),
                          vm_size_t(Int(threadCount) * MemoryLayout<thread_t>.size))
        }
        var total: Double = 0
        for i in 0..<Int(threadCount) {
            var info = thread_basic_info()
            var count = mach_msg_type_number_t(MemoryLayout<thread_basic_info_data_t>.size / MemoryLayout<natural_t>.size)
            let kr = withUnsafeMutablePointer(to: &info) {
                $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                    thread_info(threads[i], thread_flavor_t(THREAD_BASIC_INFO), $0, &count)
                }
            }
            if kr == KERN_SUCCESS, (info.flags & TH_FLAGS_IDLE) == 0 {
                total += Double(info.cpu_usage) / Double(TH_USAGE_SCALE)
            }
        }
        return total * 100.0
    }
}
