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
        enableTapTracking: Bool = true,
        enableMetrics: Bool = true,
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
            MetricKitSubscriber.shared.start()
        }
        if enableHttpTracking { HttpTracking.install() }
        if enableScreenTracking { ScreenTracking.install() }
        if enableTapTracking { TapTracking.install() }
        if enableMetrics {
            MetricsCollector.shared.start()
            FrameWatcher.shared.start()
        }
    }

    public static func setScreen(_ name: String) { ScoutEngine.shared.setScreen(name: name) }
    public static func reportError(_ error: Error, stackTrace: [String] = Thread.callStackSymbols) {
        ScoutEngine.shared.reportError(
            type: "\(Swift.type(of: error))",
            message: (error as NSError).localizedDescription,
            stackTrace: stackTrace.joined(separator: "\n")
        )
    }
    // MARK: Logs
    public static func logInfo(_ message: String, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.logInfo(message: message)
            : ScoutEngine.shared.logInfo(message: message, attributes: attributes)
    }
    public static func logError(_ message: String, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.logError(message: message)
            : ScoutEngine.shared.logError(message: message, attributes: attributes)
    }
    public static func logWarning(_ message: String, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.logWarning(message: message)
            : ScoutEngine.shared.logWarning(message: message, attributes: attributes)
    }
    public static func logDebug(_ message: String, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.logDebug(message: message)
            : ScoutEngine.shared.logDebug(message: message, attributes: attributes)
    }
    public static func logEvent(_ name: String, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.logEvent(name: name)
            : ScoutEngine.shared.logEvent(name: name, attributes: attributes)
    }

    // MARK: User / identity
    public static func setUser(id: String?, attributes: [String: String] = [:]) {
        attributes.isEmpty ? ScoutEngine.shared.setUser(id: id)
            : ScoutEngine.shared.setUser(id: id, attributes: attributes)
    }
    public static func setUserAttributes(_ attributes: [String: String]) {
        ScoutEngine.shared.setUserAttributes(attributes: attributes)
    }
    public static func clearUser() { ScoutEngine.shared.clearUser() }

    // MARK: Session / account / feature flags
    public static func setSessionAttributes(_ attributes: [String: String]) {
        ScoutEngine.shared.setSessionAttributes(attributes: attributes)
    }
    public static func clearSessionAttributes() { ScoutEngine.shared.clearSessionAttributes() }
    public static func setAccount(id: String, name: String? = nil) {
        ScoutEngine.shared.setAccount(id: id, name: name)
    }
    public static func clearAccount() { ScoutEngine.shared.clearAccount() }
    public static func setFeatureFlag(name: String, value: String) {
        ScoutEngine.shared.setFeatureFlag(name: name, value: value)
    }
    public static func clearFeatureFlags() { ScoutEngine.shared.clearFeatureFlags() }

    // MARK: Timings / vitals / operations
    public static func addTiming(_ name: String) { ScoutEngine.shared.addTiming(name: name) }
    public static func startVital(_ name: String) { ScoutEngine.shared.startVital(name: name) }
    public static func endVital(_ name: String, description: String? = nil) {
        ScoutEngine.shared.endVital(name: name, description: description)
    }
    public static func recordOperationStep(name: String, step: String, key: String? = nil, failureReason: String? = nil) {
        ScoutEngine.shared.recordOperationStep(name: name, step: step, key: key, failureReason: failureReason)
    }

    // MARK: Manual spans (network / long task / interaction / metric)
    public static func reportHttp(method: String, url: String, statusCode: Int, responseSize: Int = -1, errorMessage: String? = nil, startEpochNanos: Int64, endEpochNanos: Int64) {
        ScoutEngine.shared.reportHttp(method: method, url: url, statusCode: Int64(statusCode),
                                      responseSize: Int64(responseSize), errorMessage: errorMessage,
                                      startEpochNanos: startEpochNanos, endEpochNanos: endEpochNanos)
    }
    public static func reportLongTask(durationMs: Int64) { ScoutEngine.shared.reportLongTask(durationMs: durationMs) }
    public static func reportTap(target: String, targetType: String, x: Double, y: Double) {
        ScoutEngine.shared.reportTap(target: target, targetType: targetType, x: x, y: y)
    }
    public static func emitGauge(name: String, value: Double, unit: String) {
        ScoutEngine.shared.emitGauge(name: name, value: value, unit: unit)
    }

    // MARK: Screen load / view session / custom span
    public static func recordScreenLoad(name: String, durationMs: Int64) {
        ScoutEngine.shared.recordScreenLoad(name: name, durationMs: durationMs)
    }
    public static func recordViewSession(name: String, durationMs: Int64) {
        ScoutEngine.shared.recordViewSession(name: name, durationMs: durationMs)
    }
    public static func recordSpan(name: String, durationMs: Int64, attributes: [String: String] = [:]) {
        ScoutEngine.shared.recordSpan(name: name, durationMs: durationMs, attributes: attributes)
    }

    // MARK: Breadcrumbs
    public static func addBreadcrumb(type: String, message: String) {
        ScoutEngine.shared.addBreadcrumb(type: type, message: message)
    }
}
