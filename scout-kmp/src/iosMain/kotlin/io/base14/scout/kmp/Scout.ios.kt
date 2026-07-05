package io.base14.scout.kmp

import io.base14.scout.core.ScoutConfig
import io.base14.scout.ios.ScoutEngine

actual object Scout {
    actual fun initialize(config: ScoutConfig) = ScoutEngine.initialize(config)
    actual fun setScreen(name: String) = ScoutEngine.setScreen(name)

    actual fun setUser(id: String?) { ScoutEngine.setUser(id) }
    actual fun setUser(id: String?, attributes: Map<String, String>) { ScoutEngine.setUser(id, attributes) }
    actual fun setUserAttributes(attributes: Map<String, String>) { ScoutEngine.setUserAttributes(attributes) }
    actual fun clearUser() { ScoutEngine.clearUser() }

    actual fun setSessionAttributes(attributes: Map<String, String>) { ScoutEngine.setSessionAttributes(attributes) }
    actual fun clearSessionAttributes() { ScoutEngine.clearSessionAttributes() }
    actual fun setAccount(id: String, name: String?) { ScoutEngine.setAccount(id, name) }
    actual fun clearAccount() { ScoutEngine.clearAccount() }
    actual fun setFeatureFlag(name: String, value: String) { ScoutEngine.setFeatureFlag(name, value) }
    actual fun clearFeatureFlags() { ScoutEngine.clearFeatureFlags() }

    actual fun addTiming(name: String) { ScoutEngine.addTiming(name) }
    actual fun startVital(name: String) { ScoutEngine.startVital(name) }
    actual fun endVital(name: String, description: String?) { ScoutEngine.endVital(name, description) }
    actual fun recordOperationStep(name: String, step: String, key: String?, failureReason: String?) {
        ScoutEngine.recordOperationStep(name, step, key, failureReason)
    }

    actual fun logInfo(message: String) { ScoutEngine.logInfo(message) }
    actual fun logInfo(message: String, attributes: Map<String, String>) { ScoutEngine.logInfo(message, attributes) }
    actual fun logWarning(message: String) { ScoutEngine.logWarning(message) }
    actual fun logError(message: String) { ScoutEngine.logError(message) }
    actual fun logDebug(message: String) { ScoutEngine.logDebug(message) }
    actual fun logEvent(name: String) { ScoutEngine.logEvent(name) }
    actual fun logEvent(name: String, attributes: Map<String, String>) { ScoutEngine.logEvent(name, attributes) }

    actual fun reportHttp(method: String, url: String, statusCode: Long, startEpochNanos: Long, endEpochNanos: Long) {
        ScoutEngine.reportHttp(method, url, statusCode, startEpochNanos, endEpochNanos)
    }
    actual fun reportLongTask(durationMs: Long) { ScoutEngine.reportLongTask(durationMs) }
    actual fun reportTap(target: String, targetType: String, x: Double, y: Double) {
        ScoutEngine.reportTap(target, targetType, x, y)
    }
    actual fun emitGauge(name: String, value: Double, unit: String) { ScoutEngine.emitGauge(name, value, unit) }

    actual fun recordScreenLoad(name: String, durationMs: Long) { ScoutEngine.recordScreenLoad(name, durationMs) }
    actual fun recordViewSession(name: String, durationMs: Long) { ScoutEngine.recordViewSession(name, durationMs) }
    actual fun recordSpan(name: String, durationMs: Long, attributes: Map<String, String>) {
        ScoutEngine.recordSpan(name, durationMs, attributes)
    }

    actual fun addBreadcrumb(type: String, message: String) = ScoutEngine.addBreadcrumb(type, message)
}
