package io.base14.scout.ios

import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.export.ScoutLogLevel
import io.base14.scout.core.platform.epochNanos
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * The Kotlin/Native bridge that the Swift `ScoutKit` layer drives. It owns the shared engine
 * (`ScoutCore`) and exposes the entry points Swift auto-instrumentation calls into: screen
 * changes (UIViewController swizzling), HTTP (URLProtocol), and native-crash ingest. Lifecycle
 * and startup are captured here in Kotlin/Native via NSNotificationCenter.
 */
object ScoutEngine {
    private val processStartNanos = epochNanos()
    private var core: ScoutCore? = null
    private var instrumentation: IosInstrumentation? = null

    // The collector endpoint, exposed so HTTP instrumentation can skip the SDK's own
    // telemetry uploads (otherwise every export is captured as a new http.request → loop).
    var collectorEndpoint: String = ""
        private set

    fun configure(
        serviceName: String,
        endpoint: String,
        environment: String?,
        headers: Map<String, String>,
        sessionSampleRate: Double,
    ) {
        configure(serviceName, endpoint, environment, headers, sessionSampleRate, true, true, true)
    }

    fun configure(
        serviceName: String,
        endpoint: String,
        environment: String?,
        headers: Map<String, String>,
        sessionSampleRate: Double,
        enableScreenTracking: Boolean,
        enableTapTracking: Boolean,
        enableStartupTracking: Boolean,
        resourceAttributes: Map<String, String> = emptyMap(),
        enableMemoryMetrics: Boolean = false,
        enableCpuMetrics: Boolean = false,
        enableFrameMetrics: Boolean = false,
        exportIntervalSeconds: Int = 30,
        maxExportBatchSize: Int = 512,
        maxQueueSize: Int = 2048,
        maxRetries: Int = 0,
        vitalsCollectionIntervalSeconds: Int = 60,
        offlineBufferEnabled: Boolean = false,
        metricExportIntervalSeconds: Int = -1,
        offlineMaxTraceItems: Int = 0,
        offlineMaxMetricItems: Int = 0,
        offlineMaxLogItems: Int = 0,
    ) {
        initialize(
            ScoutConfig(
                serviceName = serviceName,
                endpoint = endpoint,
                environment = environment,
                headers = headers,
                sessionSampleRate = sessionSampleRate,
                enableScreenTracking = enableScreenTracking,
                enableTapTracking = enableTapTracking,
                enableStartupTracking = enableStartupTracking,
                resourceAttributes = resourceAttributes,
                enableMemoryMetrics = enableMemoryMetrics,
                enableCpuMetrics = enableCpuMetrics,
                enableFrameMetrics = enableFrameMetrics,
                exportIntervalSeconds = exportIntervalSeconds,
                maxExportBatchSize = maxExportBatchSize,
                maxQueueSize = maxQueueSize,
                maxRetries = maxRetries,
                vitalsCollectionIntervalSeconds = vitalsCollectionIntervalSeconds,
                offlineBufferEnabled = offlineBufferEnabled,
                metricExportIntervalSeconds = metricExportIntervalSeconds.takeIf { it > 0 },
                offlineMaxTraceItems = offlineMaxTraceItems,
                offlineMaxMetricItems = offlineMaxMetricItems,
                offlineMaxLogItems = offlineMaxLogItems,
            ),
        )
    }

    fun initialize(config: ScoutConfig) {
        if (core != null) return
        collectorEndpoint = config.endpoint
        val created = ScoutCore(
            config = config,
            store = IosKeyValueStore(),
            httpClient = HttpClient(Darwin),
            platformResourceAttributes = iosResourceAttributes(),
        )
        core = created
        IosDynamicAttributes.enableMonitoring()
        created.dynamicAttributesProvider = {
            val m = IosDynamicAttributes.collect().toMutableMap()
            currentScreenName?.let { m[ScoutAttributes.SCREEN_NAME] = it }
            m
        }
        instrumentation = IosInstrumentation(created, processStartNanos).also { it.start() }
    }

    private var currentScreenSpan: ScoutCore.ScoutSpan? = null
    private var currentScreenName: String? = null
    private var screenEnterNanos: Long = 0
    private var lastTapNanos: Long? = null
    private var fbcEmitted: Boolean = false

    // Live breadcrumb trail for the current session; previous-session trail (survives a crash).
    private fun breadcrumbsJson(): String = core?.breadcrumbs?.toJson() ?: "[]"
    private fun previousBreadcrumbsJson(): String = core?.breadcrumbs?.previousSessionJson ?: "[]"

    fun setScreen(name: String) {
        val c = core ?: return
        val previous = currentScreenName
        val nowNanos = epochNanos()
        if (c.config.enableScreenTracking) {
            if (previous != null) {
                val timeSpentMs = (nowNanos - screenEnterNanos) / 1_000_000L
                c.emit(
                    ScoutSpans.VIEW_SESSION,
                    mapOf(
                        ScoutAttributes.SCREEN_NAME to previous,
                        ScoutAttributes.VIEW_TIME_SPENT to (timeSpentMs / 1000.0).toString(),
                    ),
                )
            }
            if (!fbcEmitted) {
                fbcEmitted = true
                val fbcMs = (nowNanos - processStartNanos) / 1_000_000L
                c.emit(
                    ScoutSpans.APP_VITAL,
                    mapOf(
                        ScoutAttributes.VITAL_NAME to "fbc",
                        ScoutAttributes.VITAL_TYPE to "startup",
                        ScoutAttributes.VITAL_DURATION to (fbcMs / 1000.0).toString(),
                        ScoutAttributes.VITAL_DURATION_MS to fbcMs,
                    ),
                )
            }
            val tapNanos = lastTapNanos
            if (previous != null && tapNanos != null && (nowNanos - tapNanos) in 0..5_000_000_000L) {
                val invMs = (nowNanos - tapNanos) / 1_000_000L
                c.emit(
                    ScoutSpans.APP_VITAL,
                    mapOf(
                        ScoutAttributes.VITAL_NAME to "inv",
                        ScoutAttributes.VITAL_TYPE to "navigation",
                        ScoutAttributes.VITAL_DURATION to (invMs / 1000.0).toString(),
                        ScoutAttributes.VITAL_DURATION_MS to invMs,
                        ScoutAttributes.VITAL_FROM_SCREEN to previous,
                        ScoutAttributes.VITAL_TO_SCREEN to name,
                    ),
                )
            }
            currentScreenSpan?.end()
            val attrs = LinkedHashMap<String, Any>()
            attrs[ScoutAttributes.SCREEN_NAME] = name
            attrs[ScoutAttributes.VIEW_ID] = randomUuidString()
            attrs[ScoutAttributes.VIEW_LOADING_TYPE] = if (previous == null) "initial_load" else "route_change"
            attrs[ScoutAttributes.VIEW_IS_ACTIVE] = true
            previous?.let { attrs[ScoutAttributes.VIEW_REFERRER] = it }
            currentScreenSpan = c.beginScreen(name = ScoutSpans.SCREEN_VIEW, attributes = attrs)
        }
        lastTapNanos = null
        screenEnterNanos = nowNanos
        currentScreenName = name
        c.addBreadcrumb("navigation", name)
    }

    fun reportHttp(
        method: String,
        url: String,
        statusCode: Long,
        responseSize: Long,
        errorMessage: String?,
        startEpochNanos: Long,
        endEpochNanos: Long,
    ) {
        val attrs = LinkedHashMap<String, Any>()
        attrs[ScoutAttributes.HTTP_METHOD] = method
        attrs[ScoutAttributes.URL_FULL] = url
        attrs[ScoutAttributes.HTTP_STATUS_CODE] = statusCode.toString()
        attrs[ScoutAttributes.HTTP_BODY_SIZE] = (if (responseSize < 0) 0 else responseSize).toString()
        attrs[ScoutAttributes.HTTP_DURATION_MS] = ((endEpochNanos - startEpochNanos) / 1_000_000L).toString()
        attrs[ScoutAttributes.HTTP_ROUTE] = routeOf(url)
        errorMessage?.takeIf { it.isNotEmpty() }?.let { attrs[ScoutAttributes.HTTP_ERROR] = it }
        core?.emit(
            name = ScoutSpans.HTTP_REQUEST,
            attributes = attrs,
            startNanos = startEpochNanos,
            endNanos = endEpochNanos,
            errorMessage = errorMessage?.takeIf { it.isNotEmpty() },
            isClient = true,
        )
        core?.addBreadcrumb("http", "$method $url")
    }

    private fun routeOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val path = afterScheme.substringAfter("/", "").substringBefore("?").substringBefore("#")
        return if (path.isEmpty()) "/" else "/$path"
    }

    fun reportNativeCrash(attributes: Map<String, String>) {
        val attrs = LinkedHashMap<String, Any>(attributes)
        // The crash killed the previous session, so use that session's persisted breadcrumb trail.
        attrs[ScoutAttributes.BREADCRUMBS] = previousBreadcrumbsJson()
        core?.lastPersistedScreenName()?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        core?.emit(
            name = ScoutSpans.NATIVE_CRASH,
            attributes = attrs,
            errorMessage = attributes[ScoutAttributes.ERROR_MESSAGE] ?: "native crash",
        )
    }

    /** App-level crash span (parity with Android's app_crash), emitted from the drained crash report. */
    fun reportAppCrash(attributes: Map<String, String>) {
        val attrs = LinkedHashMap<String, Any>(attributes)
        attrs[ScoutAttributes.ERROR_ID] = randomUuidString()
        attrs[ScoutAttributes.ERROR_HANDLED] = "false"
        attrs[ScoutAttributes.ERROR_HANDLING] = "unhandled"
        attrs[ScoutAttributes.ERROR_SOURCE_TYPE] = "ios"
        attrs[ScoutAttributes.BREADCRUMBS] = previousBreadcrumbsJson()
        core?.lastPersistedScreenName()?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        core?.emit(
            name = ScoutSpans.APP_CRASH,
            attributes = attrs,
            errorMessage = attributes[ScoutAttributes.ERROR_MESSAGE] ?: "app crash",
        )
    }

    fun reportAnr(durationMs: Long, mainThreadStack: String) {
        val attrs = LinkedHashMap<String, Any>()
        attrs[ScoutAttributes.CRASH_TYPE] = "anr"
        attrs[ScoutAttributes.ERROR_MESSAGE] = "Application Not Responding"
        attrs[ScoutAttributes.ANR_DURATION] = (durationMs / 1000.0).toString()
        attrs[ScoutAttributes.ANR_THRESHOLD] = ((core?.config?.anrThresholdMs ?: 5000L) / 1000.0).toString()
        attrs[ScoutAttributes.ANR_MAIN_THREAD_STACK] = mainThreadStack
        attrs[ScoutAttributes.BREADCRUMBS] = breadcrumbsJson()
        currentScreenName?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        core?.emit(
            name = ScoutSpans.ANR,
            attributes = attrs,
            errorMessage = "Application Not Responding",
        )
    }

    fun reportError(type: String, message: String, stackTrace: String) {
        // Record the error as a breadcrumb first, then attach the trail (matches scout-flutter).
        core?.addBreadcrumb("error", message.ifEmpty { type })
        core?.emit(
            name = ScoutSpans.ERROR,
            attributes = mapOf(
                ScoutAttributes.ERROR_ID to randomUuidString(),
                ScoutAttributes.ERROR_TYPE to type,
                ScoutAttributes.ERROR_MESSAGE to message,
                ScoutAttributes.ERROR_STACK_TRACE to stackTrace,
                ScoutAttributes.ERROR_HANDLED to "true",
                ScoutAttributes.ERROR_HANDLING to "handled",
                ScoutAttributes.ERROR_SOURCE_TYPE to "ios",
                ScoutAttributes.BREADCRUMBS to breadcrumbsJson(),
            ),
            errorMessage = message.ifEmpty { type },
        )
    }

    fun emitGauge(name: String, value: Double, unit: String) {
        core?.emitGauge(name, value, unit)
    }

    fun reportLongTask(durationMs: Long) {
        val attrs = LinkedHashMap<String, Any>()
        attrs[ScoutAttributes.LONG_TASK_DURATION] = (durationMs / 1000.0).toString()
        attrs[ScoutAttributes.LONG_TASK_THRESHOLD] = ((core?.config?.longTaskThresholdMs ?: 100L) / 1000.0).toString()
        currentScreenName?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        core?.emit(name = ScoutSpans.LONG_TASK, attributes = attrs)
    }

    fun reportFrozenFrame(durationMs: Long) {
        val attrs = LinkedHashMap<String, Any>()
        attrs[ScoutAttributes.FROZEN_FRAME_DURATION] = (durationMs / 1000.0).toString()
        currentScreenName?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        core?.emit(name = ScoutSpans.FROZEN_FRAME, attributes = attrs)
    }

    fun reportTap(target: String, targetType: String, x: Double, y: Double) {
        core?.emit(
            name = ScoutSpans.USER_INTERACTION,
            attributes = mapOf(
                ScoutAttributes.UI_ID to randomUuidString(),
                ScoutAttributes.UI_TYPE to "tap",
                ScoutAttributes.UI_TARGET to target,
                ScoutAttributes.UI_TARGET_TYPE to targetType,
                ScoutAttributes.UI_TARGET_NAME_SOURCE to "accessibility",
                ScoutAttributes.UI_TARGET_PERMANENT_ID to permanentId(target, targetType),
                ScoutAttributes.UI_TARGET_X to x.toString(),
                ScoutAttributes.UI_TARGET_Y to y.toString(),
            ),
        )
        lastTapNanos = epochNanos()
        core?.addBreadcrumb("tap", target)
    }

    private fun permanentId(label: String, targetType: String): String {
        var h = 0
        for (c in "$label|$targetType") h = h * 31 + c.code
        return (h.toLong() and 0xffffffffL).toString(16)
    }

    fun logInfo(message: String) = core?.log(ScoutLogLevel.INFO, message) ?: Unit

    fun logInfo(message: String, attributes: Map<String, String>) =
        core?.log(ScoutLogLevel.INFO, message, attributes) ?: Unit

    fun logError(message: String) = core?.log(ScoutLogLevel.ERROR, message) ?: Unit

    fun logError(message: String, attributes: Map<String, String>) =
        core?.log(ScoutLogLevel.ERROR, message, attributes) ?: Unit

    fun logWarning(message: String) = core?.log(ScoutLogLevel.WARNING, message) ?: Unit

    fun logWarning(message: String, attributes: Map<String, String>) =
        core?.log(ScoutLogLevel.WARNING, message, attributes) ?: Unit

    fun logDebug(message: String) = core?.log(ScoutLogLevel.DEBUG, message) ?: Unit

    fun logDebug(message: String, attributes: Map<String, String>) =
        core?.log(ScoutLogLevel.DEBUG, message, attributes) ?: Unit

    fun logEvent(name: String) = core?.logEvent(name) ?: Unit

    fun logEvent(name: String, attributes: Map<String, String>) =
        core?.logEvent(name, attributes) ?: Unit

    fun setUser(id: String?) = core?.setUser(id) ?: Unit

    fun setUser(id: String?, attributes: Map<String, String>) = core?.setUser(id, attributes) ?: Unit

    fun setUserAttributes(attributes: Map<String, String>) = core?.setUserAttributes(attributes) ?: Unit

    fun clearUser() = core?.clearUser() ?: Unit

    fun setSessionAttributes(attributes: Map<String, String>) =
        core?.setSessionAttributes(attributes) ?: Unit

    fun clearSessionAttributes() = core?.clearSessionAttributes() ?: Unit

    fun setAccount(id: String, name: String?) = core?.setAccount(id, name) ?: Unit

    fun clearAccount() = core?.clearAccount() ?: Unit

    fun setFeatureFlag(name: String, value: String) = core?.setFeatureFlag(name, value) ?: Unit

    fun clearFeatureFlags() = core?.clearFeatureFlags() ?: Unit

    fun addTiming(name: String) = core?.addTiming(name) ?: Unit

    fun startVital(name: String) = core?.startVital(name) ?: Unit

    fun endVital(name: String, description: String?) = core?.endVital(name, description) ?: Unit

    fun recordOperationStep(name: String, step: String, key: String?, failureReason: String?) =
        core?.recordOperationStep(name, step, key, failureReason) ?: Unit

    fun recordScreenLoad(name: String, durationMs: Long) = core?.recordScreenLoad(name, durationMs) ?: Unit

    fun recordViewSession(name: String, durationMs: Long) = core?.recordViewSession(name, durationMs) ?: Unit

    fun recordSpan(name: String, durationMs: Long, attributes: Map<String, String>) =
        core?.recordSpan(name, durationMs, attributes) ?: Unit

    fun addBreadcrumb(type: String, message: String) = core?.addBreadcrumb(type, message) ?: Unit

    fun ingestForwardedSpans(payloadJson: String) = core?.ingestForwardedSpans(payloadJson) ?: Unit

    fun ingestForwardedLogs(payloadJson: String) = core?.ingestForwardedLogs(payloadJson) ?: Unit

    fun ingestForwardedMetrics(payloadJson: String) = core?.ingestForwardedMetrics(payloadJson) ?: Unit

    fun pushBreadcrumbs(payloadJson: String) =
        core?.mergeBreadcrumbs(BridgeCodec.decodeBreadcrumbs(payloadJson)) ?: Unit

    fun setBreadcrumbs(payloadJson: String) =
        core?.replaceBreadcrumbs(BridgeCodec.decodeBreadcrumbsArray(payloadJson)) ?: Unit

    fun adoptExternalSessionId(id: String, startIso: String, sampled: Boolean) =
        core?.adoptExternalSessionId(id, startIso, sampled) ?: Unit

    fun bridgeContext(): String = core?.let { BridgeCodec.encodeContext(it.bridgeContext()) } ?: ""
}
