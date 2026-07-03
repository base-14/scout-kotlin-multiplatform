package io.base14.scout.ios

import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutCore
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
        initialize(
            ScoutConfig(
                serviceName = serviceName,
                endpoint = endpoint,
                environment = environment,
                headers = headers,
                sessionSampleRate = sessionSampleRate,
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
        instrumentation = IosInstrumentation(created, processStartNanos).also { it.start() }
    }

    fun setScreen(name: String) {
        core?.emit(
            name = ScoutSpans.SCREEN_VIEW,
            attributes = mapOf(
                ScoutAttributes.SCREEN_NAME to name,
                ScoutAttributes.VIEW_LOADING_TYPE to "initial_load",
            ),
        )
    }

    fun reportHttp(
        method: String,
        url: String,
        statusCode: Long,
        startEpochNanos: Long,
        endEpochNanos: Long,
    ) {
        core?.emit(
            name = ScoutSpans.HTTP_REQUEST,
            attributes = mapOf(
                ScoutAttributes.HTTP_METHOD to method,
                ScoutAttributes.URL_FULL to url,
                ScoutAttributes.HTTP_STATUS_CODE to statusCode.toString(),
            ),
            startNanos = startEpochNanos,
            endNanos = endEpochNanos,
            isClient = true,
        )
    }

    fun reportNativeCrash(attributes: Map<String, String>) {
        core?.emit(
            name = ScoutSpans.NATIVE_CRASH,
            attributes = attributes,
            errorMessage = attributes[ScoutAttributes.ERROR_MESSAGE] ?: "native crash",
        )
    }

    fun reportAnr(durationMs: Long, mainThreadStack: String) {
        core?.emit(
            name = ScoutSpans.ANR,
            attributes = mapOf(
                ScoutAttributes.CRASH_TYPE to "anr",
                ScoutAttributes.ERROR_MESSAGE to "Application Not Responding",
                "anr.duration_ms" to durationMs.toString(),
                "anr.main_thread_stack" to mainThreadStack,
            ),
            errorMessage = "Application Not Responding",
        )
    }

    fun reportError(type: String, message: String, stackTrace: String) {
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
            ),
            errorMessage = message.ifEmpty { type },
        )
    }

    fun emitGauge(name: String, value: Double, unit: String) {
        core?.emitGauge(name, value, unit)
    }

    fun reportLongTask(durationMs: Long) {
        core?.emit(
            name = ScoutSpans.LONG_TASK,
            attributes = mapOf(ScoutAttributes.LONG_TASK_DURATION to durationMs.toString()),
        )
    }

    fun reportFrozenFrame(durationMs: Long) {
        core?.emit(
            name = ScoutSpans.FROZEN_FRAME,
            attributes = mapOf(ScoutAttributes.FROZEN_FRAME_DURATION to durationMs.toString()),
        )
    }

    fun reportTap(target: String, targetType: String, x: Double, y: Double) {
        core?.emit(
            name = ScoutSpans.USER_INTERACTION,
            attributes = mapOf(
                ScoutAttributes.UI_TARGET to target,
                ScoutAttributes.UI_TARGET_TYPE to targetType,
                ScoutAttributes.UI_TARGET_X to x.toString(),
                ScoutAttributes.UI_TARGET_Y to y.toString(),
            ),
        )
    }

    fun logInfo(message: String) = core?.log(ScoutLogLevel.INFO, message) ?: Unit

    fun logError(message: String) = core?.log(ScoutLogLevel.ERROR, message) ?: Unit

    fun logEvent(name: String) = core?.logEvent(name) ?: Unit

    fun setUser(id: String?) = core?.setUser(id) ?: Unit

    fun addBreadcrumb(type: String, message: String) = core?.addBreadcrumb(type, message) ?: Unit
}
