package io.base14.scout.kmp

import io.base14.scout.core.ScoutConfig

/**
 * Unified Scout RUM entry point for Kotlin Multiplatform apps. Write one call in `commonMain`
 * and it delegates to the full native SDK per platform:
 *  - **Android** → `scout-android` (Activity/Compose screens, OkHttp interceptor, native crash).
 *  - **iOS** → `scout-core` engine directly via Kotlin/Native (lifecycle + startup); deep Swift
 *    hooks (UIKit swizzling, KSCrash) are only in the pure-Swift `ScoutKit` product.
 *
 * The Android context is captured automatically by a ContentProvider, so `initialize` needs no
 * platform argument.
 */
expect object Scout {
    fun initialize(config: ScoutConfig)
    fun setScreen(name: String)

    // User / identity
    fun setUser(id: String?)
    fun setUser(id: String?, attributes: Map<String, String>)
    fun setUserAttributes(attributes: Map<String, String>)
    fun clearUser()

    // Session / account / feature flags
    fun setSessionAttributes(attributes: Map<String, String>)
    fun clearSessionAttributes()
    fun setAccount(id: String, name: String?)
    fun clearAccount()
    fun setFeatureFlag(name: String, value: String)
    fun clearFeatureFlags()

    // Timings / vitals / operations
    fun addTiming(name: String)
    fun startVital(name: String)
    fun endVital(name: String, description: String?)
    fun recordOperationStep(name: String, step: String, key: String?, failureReason: String?)

    // Logs / events
    fun logInfo(message: String)
    fun logInfo(message: String, attributes: Map<String, String>)
    fun logWarning(message: String)
    fun logError(message: String)
    fun logDebug(message: String)
    fun logEvent(name: String)
    fun logEvent(name: String, attributes: Map<String, String>)

    // Manual spans (network / long task / interaction / metric)
    fun reportHttp(method: String, url: String, statusCode: Long, startEpochNanos: Long, endEpochNanos: Long)
    fun reportLongTask(durationMs: Long)
    fun reportTap(target: String, targetType: String, x: Double, y: Double)
    fun emitGauge(name: String, value: Double, unit: String)

    // Screen load / view session / custom span
    fun recordScreenLoad(name: String, durationMs: Long)
    fun recordViewSession(name: String, durationMs: Long)
    fun recordSpan(name: String, durationMs: Long, attributes: Map<String, String>)

    // Breadcrumbs
    fun addBreadcrumb(type: String, message: String)
}
