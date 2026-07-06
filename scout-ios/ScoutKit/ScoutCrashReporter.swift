import Foundation
#if canImport(KSCrash)
import KSCrash
#else
import KSCrashRecording
import KSCrashInstallations
#endif
import UIKit
import Scout

/// KSCrash-backed crash reporting. `install()` arms the handlers as early as possible;
/// `drainPending()` reads any report captured on the previous run, flattens it into
/// `crash.*` attributes, and emits it through the engine as a `native_crash` span.
///
/// The `parseReport` mapping is adapted from the scout-flutter iOS reporter (it produces the
/// full rich attribute set: mach_exception, cpu_type, registers, callstack tree, binary images,
/// memory footprint, app-usage counters, idfv/jailbroken/translated, etc.).
enum ScoutCrashReporter {
    private static var isInstalled = false

    static func install() {
        guard !isInstalled else { return }
        isInstalled = true
        let config = KSCrashConfiguration()
        config.monitors = [
            .machException, .signal, .cppException, .nsException,
            .mainThreadDeadlock, .userReported, .system, .applicationState,
        ]
        do {
            try KSCrash.shared.install(with: config)
        } catch {
            NSLog("Scout: KSCrash install failed: \(error)")
        }
    }

    static func drainPending() {
        for report in pendingReports() {
            var attrs: [String: String] = [:]
            for (key, value) in report {
                let mapped = key.hasPrefix("crash_") ? "crash." + key.dropFirst("crash_".count) : key
                attrs[String(mapped)] = stringify(value)
            }
            if attrs["error.message"] == nil {
                attrs["error.message"] = attrs["crash.reason"] ?? "native crash"
            }
            if attrs["error.type"] == nil {
                attrs["error.type"] = attrs["crash.mach_exception"] ?? attrs["crash.signal"]
                    ?? attrs["crash.type"] ?? "crash"
            }
            // native_crash carries the low-level detail; app_crash is the app-level event (parity
            // with Android) with error.type/message + breadcrumbs.
            ScoutEngine.shared.reportNativeCrash(attributes: attrs)
            ScoutEngine.shared.reportAppCrash(attributes: attrs)
        }
    }

    private static func pendingReports() -> [[String: Any]] {
        let store: CrashReportStore
        do {
            store = try CrashReportStore.defaultStore()
        } catch {
            NSLog("Scout: failed to open crash store: \(error)")
            return []
        }
        let ids = store.reportIDs
        if ids.isEmpty { return [] }
        var reports: [[String: Any]] = []
        for id in ids {
            guard let dict = store.report(for: id.int64Value) else { continue }
            if let parsed = parseReport(dict.value as [String: Any]) { reports.append(parsed) }
        }
        store.deleteAllReports()
        return reports
    }

    private static func stringify(_ value: Any) -> String {
        switch value {
        case let s as String: return s
        case let b as Bool: return b ? "true" : "false"
        case let n as NSNumber: return n.stringValue
        default: return String(describing: value)
        }
    }

    // MARK: - Parse (adapted from scout-flutter CrashReporter.parseReport)

    private static func parseReport(_ report: [String: Any]) -> [String: Any]? {
        var out: [String: Any] = [:]

        if let info = report["report"] as? [String: Any] {
            put(&out, "crash_timestamp", info["timestamp"])
            put(&out, "crash_report_id", info["id"])
            put(&out, "crash_report_type", info["type"])
            put(&out, "crash_report_version", info["version"])
        }
        if out["crash_timestamp"] == nil {
            out["crash_timestamp"] = ISO8601DateFormatter().string(from: Date())
        }

        if let system = report["system"] as? [String: Any] {
            put(&out, "crash_os_name", system["system_name"])
            put(&out, "crash_os_version", system["system_version"])
            put(&out, "crash_os_build", system["os_version"])
            put(&out, "crash_kernel_version", system["kernel_version"])
            put(&out, "crash_boot_time", system["boot_time"])
            put(&out, "crash_app_start_time", system["app_start_time"])
            put(&out, "crash_time_zone", system["time_zone"])
            put(&out, "crash_device_model", system["model"])
            put(&out, "crash_machine", system["machine"])
            put(&out, "crash_cpu_arch", system["cpu_arch"])
            put(&out, "crash_cpu_type", system["cpu_type"])
            put(&out, "crash_cpu_subtype", system["cpu_subtype"])
            put(&out, "crash_binary_cpu_type", system["binary_cpu_type"])
            put(&out, "crash_binary_cpu_subtype", system["binary_cpu_subtype"])
            put(&out, "crash_app_name", system["CFBundleName"])
            put(&out, "crash_app_executable", system["CFBundleExecutable"])
            put(&out, "crash_bundle_id", system["CFBundleIdentifier"])
            put(&out, "crash_app_version", system["CFBundleShortVersionString"])
            put(&out, "crash_bundle_version", system["CFBundleVersion"])
            put(&out, "crash_executable_path", system["CFBundleExecutablePath"])
            put(&out, "crash_app_id", system["CFBundleIdentifier"])
            put(&out, "crash_build_type", system["build_type"])
            put(&out, "crash_device_app_hash", system["device_app_hash"])
            put(&out, "crash_app_uuid", system["app_uuid"])
            put(&out, "crash_process_name", system["process_name"])
            put(&out, "crash_pid", system["process_id"])
            put(&out, "crash_parent_pid", system["parent_process_id"])
            if let memory = system["memory"] as? [String: Any] {
                put(&out, "crash_memory_size_bytes", memory["size"])
                put(&out, "crash_memory_free_bytes", memory["free"])
                put(&out, "crash_memory_usable_bytes", memory["usable"])
            }
            if let appMem = system["app_memory"] as? [String: Any] {
                put(&out, "crash_memory_footprint", appMem["memory_footprint"])
                put(&out, "crash_memory_remaining", appMem["memory_remaining"])
                put(&out, "crash_memory_pressure", appMem["memory_pressure"])
                put(&out, "crash_memory_level", appMem["memory_level"])
                put(&out, "crash_memory_limit", appMem["memory_limit"])
                put(&out, "crash_app_transition_state", appMem["app_transition_state"])
            }
            put(&out, "crash_storage_size_bytes", system["storage"])
            if let jailbroken = system["jailbroken"] as? Bool { out["crash_jailbroken"] = jailbroken }
            if let stats = system["application_stats"] as? [String: Any] {
                put(&out, "crash_app_active_time_secs", stats["active_time_since_launch"])
                put(&out, "crash_app_background_time_secs", stats["background_time_since_launch"])
                put(&out, "crash_app_active_time_since_last_crash_secs", stats["active_time_since_last_crash"])
                put(&out, "crash_app_background_time_since_last_crash_secs", stats["background_time_since_last_crash"])
                put(&out, "crash_app_launches_since_last_crash", stats["launches_since_last_crash"])
                put(&out, "crash_app_sessions_since_last_crash", stats["sessions_since_last_crash"])
                put(&out, "crash_app_sessions_since_launch", stats["sessions_since_launch"])
                if let fg = stats["application_in_foreground"] as? Bool { out["crash_app_in_foreground"] = fg }
                if let active = stats["application_active"] as? Bool { out["crash_app_active"] = active }
            }
        }

        addDrainTimeContext(&out)
        addProcessSysctlContext(&out)

        let crash = report["crash"] as? [String: Any]
        let error = crash?["error"] as? [String: Any]
        put(&out, "crash_type", error?["type"])
        put(&out, "crash_diagnosis", crash?["diagnosis"])

        if let mach = error?["mach"] as? [String: Any] {
            if let name = mach["exception_name"] as? String {
                out["crash_mach_exception"] = name
                if out["crash_reason"] == nil { out["crash_reason"] = name }
            }
            put(&out, "crash_mach_code", mach["code"])
            put(&out, "crash_mach_code_name", mach["code_name"])
            put(&out, "crash_mach_subcode", mach["subcode"])
            put(&out, "crash_mach_exception_code", mach["exception"])
        }
        if let signal = error?["signal"] as? [String: Any] {
            if let name = signal["name"] as? String {
                out["crash_signal"] = name
                if out["crash_reason"] == nil { out["crash_reason"] = name }
            }
            put(&out, "crash_signal_code", signal["code"])
            put(&out, "crash_signal_code_name", signal["code_name"])
            if let address = signal["address"] as? NSNumber {
                out["crash_signal_address"] = String(format: "0x%llx", address.uint64Value)
            }
            put(&out, "crash_signal_number", signal["signal"])
        }
        if let nsex = error?["nsexception"] as? [String: Any] {
            put(&out, "crash_nsexception_name", nsex["name"])
            if let reason = nsex["reason"] as? String, out["crash_reason"] == nil { out["crash_reason"] = reason }
        }
        put(&out, "crash_fault_address", error?["address"])
        put(&out, "crash_crashing_thread_index", crash?["crashed_thread"])
        if out["crash_reason"] == nil, let reason = error?["reason"] as? String { out["crash_reason"] = reason }
        if out["crash_reason"] == nil { out["crash_reason"] = "Unknown" }

        if let threads = crash?["threads"] as? [[String: Any]] {
            out["crash_thread_count"] = threads.count
            let crashed = threads.first(where: { ($0["crashed"] as? Bool) == true }) ?? threads.first
            if let threadName = crashed?["name"] as? String, !threadName.isEmpty { out["crash_thread_name"] = threadName }
            put(&out, "crash_thread_index", crashed?["index"])
            if let bt = crashed?["backtrace"] as? [String: Any], let frames = bt["contents"] as? [[String: Any]] {
                out["crash_stack_trace"] = formatStack(frames)
            }
            if let registers = crashed?["registers"] as? [String: Any],
               let data = try? JSONSerialization.data(withJSONObject: registers, options: [.sortedKeys]),
               let s = String(data: data, encoding: .utf8) {
                out["crash_registers_json"] = s
            }
            if let data = try? JSONSerialization.data(withJSONObject: threads, options: []),
               let s = String(data: data, encoding: .utf8) {
                out["crash_callstack_tree_json"] = s
            }
        }
        if let images = report["binary_images"] as? [[String: Any]],
           let data = try? JSONSerialization.data(withJSONObject: images, options: []),
           let s = String(data: data, encoding: .utf8) {
            out["crash_binary_images_json"] = s
            out["crash_binary_images_count"] = images.count
        }
        if let userInfo = report["user"] as? [String: Any],
           let data = try? JSONSerialization.data(withJSONObject: userInfo, options: []),
           let s = String(data: data, encoding: .utf8) {
            out["crash_user_info_json"] = s
        }
        return out.isEmpty ? nil : out
    }

    private static func put(_ dict: inout [String: Any], _ key: String, _ value: Any?) {
        guard let v = value else { return }
        if let s = v as? String, s.isEmpty { return }
        dict[key] = v
    }

    private static func addDrainTimeContext(_ out: inout [String: Any]) {
        if let idfv = UIDevice.current.identifierForVendor?.uuidString { out["crash_idfv"] = idfv }
        out["crash_uid"] = Int(getuid())
        out["crash_gid"] = Int(getgid())
        var bootTime = timeval()
        var btSize = MemoryLayout<timeval>.size
        if sysctlbyname("kern.boottime", &bootTime, &btSize, nil, 0) == 0 {
            let bootDate = Date(timeIntervalSince1970: Double(bootTime.tv_sec))
            out["crash_system_boot_time_iso"] = ISO8601DateFormatter().string(from: bootDate)
            out["crash_time_since_boot_secs"] = Date().timeIntervalSince(bootDate)
        }
        out["crash_drain_uptime_secs"] = ProcessInfo.processInfo.systemUptime
        #if targetEnvironment(simulator)
        out["crash_environment"] = "simulator"
        #else
        out["crash_environment"] = "device"
        #endif
        #if DEBUG
        out["crash_build_configuration"] = "debug"
        #else
        out["crash_build_configuration"] = "release"
        #endif
    }

    private static func addProcessSysctlContext(_ out: inout [String: Any]) {
        var translated: Int32 = 0
        var tsize = MemoryLayout<Int32>.size
        if sysctlbyname("sysctl.proc_translated", &translated, &tsize, nil, 0) == 0 {
            out["crash_translated"] = translated == 1
        }
    }

    private static func formatStack(_ frames: [[String: Any]]) -> String {
        frames.map { frame in
            let symbol = frame["symbol_name"] as? String ?? "??"
            let addr = (frame["instruction_addr"] as? NSNumber)?.uint64Value ?? 0
            let obj = frame["object_name"] as? String ?? "??"
            let offset = (frame["symbol_addr"] as? NSNumber)?.uint64Value ?? 0
            let delta = addr >= offset ? addr - offset : 0
            return "\(obj) 0x\(String(addr, radix: 16)) \(symbol) + \(delta)"
        }.joined(separator: "\n")
    }
}
