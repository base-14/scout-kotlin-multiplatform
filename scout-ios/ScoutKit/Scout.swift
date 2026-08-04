import Foundation
import Scout // the Kotlin/Native engine framework (ScoutEngine, ScoutConfig, ...)

/// Public entry point for the Scout iOS SDK. Call `Scout.start(...)` once at launch
/// (e.g. in `application(_:didFinishLaunchingWithOptions:)`), as early as possible so the
/// crash handler is armed and cold-start timing is accurate.
public enum Scout {
    public static func start(
        serviceName: String,
        endpoint: String,
        serviceVersion: String? = nil,
        environment: String? = nil,
        headers: [String: String] = [:],
        resourceAttributes: [String: String] = [:],
        sessionSampleRate: Double = 1.0,
        alwaysCaptureErrors: Bool = true,
        sessionTimeoutMinutes: Int = 30,
        maxSessionDurationMinutes: Int = 60,
        firstPartyHosts: [String] = [],
        ignoreUrlPatterns: [String] = [],
        enableCrashReporting: Bool = true,
        enableHttpTracking: Bool = true,
        enableErrorTracking: Bool = true,
        enableScreenTracking: Bool = true,
        enableTapTracking: Bool = true,
        enableAnrTracking: Bool = true,
        enableJankTracking: Bool = true,
        enableLifecycleTracking: Bool = true,
        enableStartupTracking: Bool = true,
        enableLogging: Bool = true,
        enableMetrics: Bool = true,
        enableMemoryMetrics: Bool = false,
        enableCpuMetrics: Bool = false,
        enableFrameMetrics: Bool = false,
        exportIntervalSeconds: Int = 30,
        maxExportBatchSize: Int = 512,
        maxQueueSize: Int = 2048,
        maxRetries: Int = 0,
        metricExportIntervalSeconds: Int = -1,
        vitalsCollectionIntervalSeconds: Int = 60,
        offlineBufferEnabled: Bool = false,
        anrThresholdMs: Double = 5000,
        longTaskThresholdMs: Int = 100,
        frozenFrameThresholdMs: Int = 700,
        maxOfflineStorageMb: Int = 5,
        debugLogging: Bool = false
    ) {
        ScoutEngine.shared.configure(
            serviceName: serviceName,
            endpoint: endpoint,
            environment: environment,
            headers: headers,
            sessionSampleRate: sessionSampleRate,
            enableScreenTracking: enableScreenTracking,
            enableTapTracking: enableTapTracking,
            enableStartupTracking: enableStartupTracking,
            resourceAttributes: resourceAttributes,
            enableMemoryMetrics: enableMemoryMetrics,
            enableCpuMetrics: enableCpuMetrics,
            enableFrameMetrics: enableFrameMetrics,
            exportIntervalSeconds: Int32(exportIntervalSeconds),
            maxExportBatchSize: Int32(maxExportBatchSize),
            maxQueueSize: Int32(maxQueueSize),
            maxRetries: Int32(maxRetries),
            vitalsCollectionIntervalSeconds: Int32(vitalsCollectionIntervalSeconds),
            offlineBufferEnabled: offlineBufferEnabled,
            metricExportIntervalSeconds: Int32(metricExportIntervalSeconds),
            enableAnrTracking: false,
            anrThresholdMs: Int64(anrThresholdMs),
            enableCrashTracking: enableCrashReporting,
            serviceVersion: serviceVersion,
            alwaysCaptureErrors: alwaysCaptureErrors,
            sessionTimeoutMinutes: Int32(sessionTimeoutMinutes),
            maxSessionDurationMinutes: Int32(maxSessionDurationMinutes),
            firstPartyHosts: firstPartyHosts,
            ignoreUrlPatterns: ignoreUrlPatterns,
            enableHttpTracking: enableHttpTracking,
            enableErrorTracking: enableErrorTracking,
            enableJankTracking: enableJankTracking,
            enableLifecycleTracking: enableLifecycleTracking,
            enableLogging: enableLogging,
            enableMetrics: enableMetrics,
            longTaskThresholdMs: Int64(longTaskThresholdMs),
            frozenFrameThresholdMs: Int64(frozenFrameThresholdMs),
            maxOfflineStorageMb: Int32(maxOfflineStorageMb),
            debugLogging: debugLogging
        )

        if enableAnrTracking {
            AppHangWatchdog.shared.start(thresholdMs: anrThresholdMs)
        }
    }

    public static func startBridge(
        serviceName: String,
        endpoint: String,
        environment: String? = nil,
        headers: [String: String] = [:],
        sessionSampleRate: Double = 1.0,
        anrThresholdMs: Double = 5000,
        resourceAttributes: [String: String] = [:],
        exportIntervalSeconds: Int = 30,
        maxExportBatchSize: Int = 512,
        maxQueueSize: Int = 2048,
        maxRetries: Int = 0,
        vitalsCollectionIntervalSeconds: Int = 60,
        offlineBufferEnabled: Bool = false,
        enableMemoryMetrics: Bool = false,
        enableCpuMetrics: Bool = false,
        enableFrameMetrics: Bool = false,
        metricExportIntervalSeconds: Int = -1,
        enableScreenTracking: Bool = true,
        enableTapTracking: Bool = false,
        enableStartupTracking: Bool = false,
        enableAnrTracking: Bool = true,
        enableCrashTracking: Bool = true,
        serviceVersion: String? = nil,
        alwaysCaptureErrors: Bool = true,
        sessionTimeoutMinutes: Int = 30,
        maxSessionDurationMinutes: Int = 60,
        firstPartyHosts: [String] = [],
        ignoreUrlPatterns: [String] = [],
        enableHttpTracking: Bool = true,
        enableErrorTracking: Bool = true,
        enableJankTracking: Bool = true,
        enableLifecycleTracking: Bool = true,
        enableLogging: Bool = true,
        enableMetrics: Bool = true,
        longTaskThresholdMs: Int = 100,
        frozenFrameThresholdMs: Int = 700,
        maxOfflineStorageMb: Int = 5,
        debugLogging: Bool = false
    ) {
        ScoutEngine.shared.configure(
            serviceName: serviceName,
            endpoint: endpoint,
            environment: environment,
            headers: headers,
            sessionSampleRate: sessionSampleRate,
            enableScreenTracking: enableScreenTracking,
            enableTapTracking: enableTapTracking,
            enableStartupTracking: enableStartupTracking,
            resourceAttributes: resourceAttributes,
            enableMemoryMetrics: enableMemoryMetrics,
            enableCpuMetrics: enableCpuMetrics,
            enableFrameMetrics: enableFrameMetrics,
            exportIntervalSeconds: Int32(exportIntervalSeconds),
            maxExportBatchSize: Int32(maxExportBatchSize),
            maxQueueSize: Int32(maxQueueSize),
            maxRetries: Int32(maxRetries),
            vitalsCollectionIntervalSeconds: Int32(vitalsCollectionIntervalSeconds),
            offlineBufferEnabled: offlineBufferEnabled,
            metricExportIntervalSeconds: Int32(metricExportIntervalSeconds),
            enableAnrTracking: false,
            anrThresholdMs: Int64(anrThresholdMs),
            enableCrashTracking: enableCrashTracking,
            serviceVersion: serviceVersion,
            alwaysCaptureErrors: alwaysCaptureErrors,
            sessionTimeoutMinutes: Int32(sessionTimeoutMinutes),
            maxSessionDurationMinutes: Int32(maxSessionDurationMinutes),
            firstPartyHosts: firstPartyHosts,
            ignoreUrlPatterns: ignoreUrlPatterns,
            enableHttpTracking: enableHttpTracking,
            enableErrorTracking: enableErrorTracking,
            enableJankTracking: enableJankTracking,
            enableLifecycleTracking: enableLifecycleTracking,
            enableLogging: enableLogging,
            enableMetrics: enableMetrics,
            longTaskThresholdMs: Int64(longTaskThresholdMs),
            frozenFrameThresholdMs: Int64(frozenFrameThresholdMs),
            maxOfflineStorageMb: Int32(maxOfflineStorageMb),
            debugLogging: debugLogging
        )
        if enableAnrTracking {
            AppHangWatchdog.shared.start(thresholdMs: anrThresholdMs)
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
