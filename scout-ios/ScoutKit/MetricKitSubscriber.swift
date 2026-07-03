import Foundation
#if canImport(MetricKit)
import MetricKit
#endif
import Scout

/// Subscribes to MetricKit (iOS 14+) diagnostics. The system delivers crash and hang diagnostics
/// (with symbolicated call-stack trees) on a later launch; we forward crashes as `native_crash`
/// and hangs as `anr`. No-op below iOS 14.
final class MetricKitSubscriber: NSObject {
    static let shared = MetricKitSubscriber()

    func start() {
        #if canImport(MetricKit)
        if #available(iOS 14.0, *) {
            MXMetricManager.shared.add(self)
        }
        #endif
    }
}

#if canImport(MetricKit)
@available(iOS 14.0, *)
extension MetricKitSubscriber: MXMetricManagerSubscriber {
    func didReceive(_ payloads: [MXMetricPayload]) {}

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        for payload in payloads {
            for crash in payload.crashDiagnostics ?? [] {
                var attrs: [String: String] = ["crash.type": "metrickit_crash"]
                if let signal = crash.signal { attrs["crash.signal_number"] = signal.stringValue }
                if let exceptionType = crash.exceptionType { attrs["crash.mach_exception_code"] = exceptionType.stringValue }
                if let reason = crash.terminationReason { attrs["error.message"] = reason }
                if let s = String(data: crash.callStackTree.jsonRepresentation(), encoding: .utf8) {
                    attrs["crash.callstack_tree_json"] = s
                }
                ScoutEngine.shared.reportNativeCrash(attributes: attrs)
            }
            for hang in payload.hangDiagnostics ?? [] {
                let durationMs = Int64(hang.hangDuration.converted(to: .milliseconds).value)
                let stack = String(data: hang.callStackTree.jsonRepresentation(), encoding: .utf8) ?? ""
                ScoutEngine.shared.reportAnr(durationMs: durationMs, mainThreadStack: stack)
            }
        }
    }
}
#endif
