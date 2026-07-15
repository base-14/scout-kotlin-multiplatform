package io.base14.scout.kmp

import io.base14.scout.core.ScoutConfig
import io.base14.scout.android.Scout as AndroidScout

actual object Scout {
    actual fun initialize(config: ScoutConfig) {
        val app = ScoutAppHolder.application
            ?: error("Scout: Application not available (ScoutInitProvider not registered?)")
        AndroidScout.initialize(app, config.withKmpVersion())
    }

    actual fun setScreen(name: String) = AndroidScout.setScreen(name)

    actual fun setUser(id: String?) {
        AndroidScout.setUser(id)
    }
    actual fun setUser(id: String?, attributes: Map<String, String>) {
        AndroidScout.setUser(id, attributes)
    }
    actual fun setUserAttributes(attributes: Map<String, String>) {
        AndroidScout.setUserAttributes(attributes)
    }
    actual fun clearUser() {
        AndroidScout.clearUser()
    }

    actual fun setSessionAttributes(attributes: Map<String, String>) {
        AndroidScout.setSessionAttributes(attributes)
    }
    actual fun clearSessionAttributes() {
        AndroidScout.clearSessionAttributes()
    }
    actual fun setAccount(id: String, name: String?) {
        AndroidScout.setAccount(id, name)
    }
    actual fun clearAccount() {
        AndroidScout.clearAccount()
    }
    actual fun setFeatureFlag(name: String, value: String) {
        AndroidScout.setFeatureFlag(name, value)
    }
    actual fun clearFeatureFlags() {
        AndroidScout.clearFeatureFlags()
    }

    actual fun addTiming(name: String) {
        AndroidScout.addTiming(name)
    }
    actual fun startVital(name: String) {
        AndroidScout.startVital(name)
    }
    actual fun endVital(name: String, description: String?) {
        AndroidScout.endVital(name, description)
    }
    actual fun recordOperationStep(name: String, step: String, key: String?, failureReason: String?) {
        AndroidScout.recordOperationStep(name, step, key, failureReason)
    }

    actual fun logInfo(message: String) {
        AndroidScout.logInfo(message)
    }
    actual fun logInfo(message: String, attributes: Map<String, String>) {
        AndroidScout.logInfo(message, attributes)
    }
    actual fun logWarning(message: String) {
        AndroidScout.logWarning(message)
    }
    actual fun logError(message: String) {
        AndroidScout.logError(message)
    }
    actual fun logDebug(message: String) {
        AndroidScout.logDebug(message)
    }
    actual fun logEvent(name: String) {
        AndroidScout.logEvent(name)
    }
    actual fun logEvent(name: String, attributes: Map<String, String>) {
        AndroidScout.logEvent(name, attributes)
    }

    actual fun reportHttp(method: String, url: String, statusCode: Long, startEpochNanos: Long, endEpochNanos: Long) {
        AndroidScout.reportHttp(method, url, statusCode, startEpochNanos, endEpochNanos)
    }
    actual fun reportLongTask(durationMs: Long) {
        AndroidScout.reportLongTask(durationMs)
    }
    actual fun reportTap(target: String, targetType: String, x: Double, y: Double) {
        AndroidScout.reportTap(target, targetType, x, y)
    }
    actual fun emitGauge(name: String, value: Double, unit: String) {
        AndroidScout.emitGauge(name, value, unit)
    }

    actual fun recordScreenLoad(name: String, durationMs: Long) {
        AndroidScout.recordScreenLoad(name, durationMs)
    }
    actual fun recordViewSession(name: String, durationMs: Long) {
        AndroidScout.recordViewSession(name, durationMs)
    }
    actual fun recordSpan(name: String, durationMs: Long, attributes: Map<String, String>) {
        AndroidScout.recordSpan(name, durationMs, attributes)
    }

    actual fun addBreadcrumb(type: String, message: String) = AndroidScout.addBreadcrumb(type, message)
}
