import Foundation
import Scout // the Kotlin/Native engine framework (ScoutEngine, ScoutConfig, ...)

/// Public entry point for the Scout iOS SDK. Call `Scout.start(...)` once at launch
/// (e.g. in `application(_:didFinishLaunchingWithOptions:)`), as early as possible so the
/// crash handler is armed and cold-start timing is accurate.
public enum Scout {
    public static func start(
        serviceName: String,
        endpoint: String,
        environment: String? = nil,
        headers: [String: String] = [:],
        sessionSampleRate: Double = 1.0,
        enableCrashReporting: Bool = true,
        enableHttpTracking: Bool = true,
        enableScreenTracking: Bool = true,
        anrThresholdMs: Double = 5000
    ) {
        // Arm the crash handler before anything else so an early crash is still captured.
        if enableCrashReporting {
            ScoutCrashReporter.install()
        }

        ScoutEngine.shared.configure(
            serviceName: serviceName,
            endpoint: endpoint,
            environment: environment,
            headers: headers,
            sessionSampleRate: sessionSampleRate
        )

        // Now that the engine exists, emit any crash captured on the previous run and start
        // the main-thread hang watchdog.
        if enableCrashReporting {
            ScoutCrashReporter.drainPending()
            AppHangWatchdog.shared.start(thresholdMs: anrThresholdMs)
        }
        if enableHttpTracking { HttpTracking.install() }
        if enableScreenTracking { ScreenTracking.install() }
    }

    public static func setScreen(_ name: String) { ScoutEngine.shared.setScreen(name: name) }
    public static func logInfo(_ message: String) { ScoutEngine.shared.logInfo(message: message) }
    public static func logError(_ message: String) { ScoutEngine.shared.logError(message: message) }
    public static func logEvent(_ name: String) { ScoutEngine.shared.logEvent(name: name) }
    public static func setUser(_ id: String?) { ScoutEngine.shared.setUser(id: id) }
    public static func addBreadcrumb(type: String, message: String) {
        ScoutEngine.shared.addBreadcrumb(type: type, message: message)
    }
}
