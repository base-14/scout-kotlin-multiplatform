package io.base14.scout.core

import io.base14.scout.core.breadcrumb.Breadcrumb
import io.base14.scout.core.breadcrumb.BreadcrumbBuffer
import io.base14.scout.core.export.MetricPoint
import io.base14.scout.core.export.OtlpJsonSerializer
import io.base14.scout.core.export.ScoutLogLevel
import io.base14.scout.core.export.ScoutMetricEmitter
import io.base14.scout.core.export.ScoutNoopLogRecordProcessor
import io.base14.scout.core.export.ScoutNoopSpanProcessor
import io.base14.scout.core.export.ScoutOtlpJsonLogRecordExporter
import io.base14.scout.core.export.ScoutOtlpJsonSpanExporter
import io.base14.scout.core.export.SpanResurrector
import io.base14.scout.core.identity.Identity
import io.base14.scout.core.internal.putAny
import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.platform.ScoutLock
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.epochNanos
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.platform.systemFileSystem
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutResourceAttributes
import io.base14.scout.core.semantics.ScoutSpans
import io.base14.scout.core.session.SessionManager
import io.ktor.client.HttpClient
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.export.batchLogRecordProcessor
import io.opentelemetry.kotlin.logging.export.persistingLogRecordProcessor
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.persistingSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile

class ScoutCore(
    val config: ScoutConfig,
    private val store: KeyValueStore,
    private val httpClient: HttpClient,
    platformResourceAttributes: Map<String, String> = emptyMap(),
    cacheDirPath: String? = null,
) {
    private val cacheDir: Path? = cacheDirPath?.toPath()
    val sessionManager = SessionManager(config, store)
    val identity = Identity(store)
    val breadcrumbs = BreadcrumbBuffer(store = store)

    private val resourceAttrs: Map<String, String> = buildResourceAttrs(platformResourceAttributes)

    private val flushScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var spanProcessor: SpanProcessor? = null
    private var logProcessor: LogRecordProcessor? = null

    private val otel: OpenTelemetry = createOpenTelemetry {
        serviceName = config.serviceName
        resource {
            for ((k, v) in resourceAttrs) setStringAttribute(k, v)
        }
        tracerProvider {
            export {
                val exporter =
                    ScoutOtlpJsonSpanExporter(config.endpoint, exportHeaders(), httpClient, config.debugLogging, config.effectiveMaxRetries)
                val dir = cacheDir
                val processor = if (config.offlineBufferEnabled && dir != null) {
                    persistingSpanProcessor(
                        ScoutNoopSpanProcessor,
                        exporter,
                        dir,
                        maxQueueSize = config.effectiveMaxQueueSize,
                        scheduleDelayMs = config.effectiveExportIntervalSeconds * 1000L,
                        maxExportBatchSize = config.effectiveMaxExportBatchSize,
                    )
                } else {
                    batchSpanProcessor(
                        exporter,
                        maxQueueSize = config.effectiveMaxQueueSize,
                        scheduleDelayMs = config.effectiveExportIntervalSeconds * 1000L,
                        maxExportBatchSize = config.effectiveMaxExportBatchSize,
                    )
                }
                spanProcessor = processor
                processor
            }
        }
        if (config.enableLogging) {
            loggerProvider {
                export {
                    val logExporter =
                        ScoutOtlpJsonLogRecordExporter(
                            config.endpoint,
                            exportHeaders(),
                            httpClient,
                            config.debugLogging,
                            config.effectiveMaxRetries,
                        )
                    val dir = cacheDir
                    val processor = if (config.offlineBufferEnabled && dir != null) {
                        persistingLogRecordProcessor(
                            ScoutNoopLogRecordProcessor,
                            logExporter,
                            dir,
                            maxQueueSize = config.effectiveMaxQueueSize,
                            scheduleDelayMs = config.effectiveExportIntervalSeconds * 1000L,
                            maxExportBatchSize = config.effectiveMaxExportBatchSize,
                        )
                    } else {
                        batchLogRecordProcessor(
                            logExporter,
                            maxQueueSize = config.effectiveMaxQueueSize,
                            scheduleDelayMs = config.effectiveExportIntervalSeconds * 1000L,
                            maxExportBatchSize = config.effectiveMaxExportBatchSize,
                        )
                    }
                    logProcessor = processor
                    processor
                }
            }
        }
    }

    private val tracer: Tracer =
        otel.tracerProvider.getTracer(SCOUT_SCOPE_NAME, version = SCOUT_SDK_VERSION)

    private val logger: Logger =
        otel.loggerProvider.getLogger(SCOUT_SCOPE_NAME, version = SCOUT_SDK_VERSION)

    private val metricEmitter = ScoutMetricEmitter(
        endpoint = config.endpoint,
        extraHeaders = exportHeaders(),
        httpClient = httpClient,
        resourceAttrs = resourceAttrs + (ScoutResourceAttributes.SERVICE_NAME to config.serviceName),
        scopeName = SCOUT_SCOPE_NAME,
        scopeVersion = SCOUT_SDK_VERSION,
        maxExportBatchSize = config.effectiveMaxExportBatchSize,
        maxQueueSize = config.effectiveMaxQueueSize,
        exportIntervalSeconds = config.effectiveMetricExportIntervalSeconds,
        maxRetries = config.effectiveMaxRetries,
        debug = config.debugLogging,
    )

    private val resurrector = SpanResurrector(config.endpoint, exportHeaders(), httpClient)

    fun forceFlush() {
        flushScope.launch {
            runCatching { spanProcessor?.forceFlush() }
            runCatching { logProcessor?.forceFlush() }
        }
        runCatching { metricEmitter.flush() }
    }

    fun emitGauge(name: String, value: Double, unit: String, attributes: Map<String, Any> = emptyMap()) {
        if (!config.enableMetrics || !sessionManager.sampled()) return
        val attrs = LinkedHashMap(attributes)
        attrs[ScoutAttributes.SESSION_ID] = sessionManager.sessionId()
        metricEmitter.emit(listOf(MetricPoint(name, value, unit, attrs, epochNanos())))
    }

    private fun buildResourceAttrs(platform: Map<String, String>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m.putAll(platform)
        m.putAll(config.resourceAttributes)
        m[ScoutResourceAttributes.SCOUT_CORE_VERSION] = ScoutBuildInfo.CORE_VERSION
        config.environment?.let { m[ScoutResourceAttributes.ENVIRONMENT] = it }
        m[ScoutResourceAttributes.SERVICE_VERSION] = config.serviceVersion ?: nativeServiceVersion(platform)
        m.remove(ScoutResourceAttributes.SERVICE_NAME)
        return m
    }

    private fun nativeServiceVersion(platform: Map<String, String>): String {
        val version = platform[ScoutResourceAttributes.APP_VERSION]?.takeIf { it.isNotBlank() }
        val build = platform[ScoutResourceAttributes.APP_BUILD]?.takeIf { it.isNotBlank() }
        return when {
            version != null && build != null -> "$version+$build"
            version != null -> version
            else -> SCOUT_SDK_VERSION
        }
    }

    private fun exportHeaders(): Map<String, String> = config.headers

    private val runtimeAttrs = LinkedHashMap<String, String>()
    private val sessionAttrKeys = HashSet<String>()
    private val vitalStarts = LinkedHashMap<String, Long>()
    private val attrLock = ScoutLock()

    @Volatile
    var dynamicAttributesProvider: (() -> Map<String, Any>)? = null

    @Volatile private var currentRootContext: Context? = null

    private fun commonAttributes(sampled: Boolean): LinkedHashMap<String, Any> {
        val all = LinkedHashMap<String, Any>()
        all[ScoutAttributes.SESSION_ID] = sessionManager.sessionId()
        all[ScoutAttributes.SESSION_TYPE] = "user"
        all[ScoutAttributes.SESSION_SAMPLE_RATE] = config.sessionSampleRate.toString()
        all[ScoutAttributes.SESSION_START_TIME] = sessionManager.startTimeIso()
        sessionManager.previousId()?.let { all[ScoutAttributes.SESSION_PREVIOUS_ID] = it }
        if (!sampled) all[ScoutAttributes.SESSION_SAMPLED] = "false"
        all[ScoutAttributes.USER_ANONYMOUS_ID] = identity.anonymousId
        identity.userId?.let { all[ScoutAttributes.USER_ID] = it }
        for ((k, v) in identity.userAttributes) all[k] = v
        attrLock.withLock { for ((k, v) in runtimeAttrs) all[k] = v }
        dynamicAttributesProvider?.let { provider ->
            runCatching { provider() }.getOrNull()?.let { for ((k, v) in it) all[k] = v }
        }
        return all
    }

    private fun shouldEmit(name: String, sampled: Boolean): Boolean =
        sampled || (name in ScoutSpans.ERROR_CLASS && config.alwaysCaptureErrors)

    inner class ScoutSpan internal constructor(
        private val span: Span,
        private val sampled: Boolean,
        private val isRoot: Boolean = false,
    ) {
        val traceparent: String
            get() = "00-${span.spanContext.traceId}-${span.spanContext.spanId}-" + if (sampled) "01" else "00"

        fun end(
            endNanos: Long = epochNanos(),
            attributes: Map<String, Any> = emptyMap(),
            errorMessage: String? = null,
        ) {
            for ((k, v) in attributes) span.putAny(k, v)
            if (errorMessage != null) span.setStatus(StatusData.Error(errorMessage))
            span.end(endNanos)
            if (isRoot) {
                currentRootContext = null
                clearActiveRoot()
            }
            sessionManager.touch()
        }
    }

    fun beginSpan(
        name: String,
        attributes: Map<String, Any> = emptyMap(),
        startNanos: Long = epochNanos(),
        isClient: Boolean = false,
    ): ScoutSpan? {
        val sampled = sessionManager.sampled()
        if (!shouldEmit(name, sampled)) return null
        val all = commonAttributes(sampled).apply { putAll(attributes) }
        if (config.beforeSend?.invoke(name, all) == false) return null
        val span = tracer.startSpan(
            name,
            parentContext = currentRootContext,
            spanKind = if (isClient) SpanKind.CLIENT else SpanKind.INTERNAL,
            startTimestamp = startNanos,
        ) { for ((k, v) in all) putAny(k, v) }
        return ScoutSpan(span, sampled)
    }

    fun beginScreen(name: String, attributes: Map<String, Any> = emptyMap(), startNanos: Long = epochNanos()): ScoutSpan? {
        val sampled = sessionManager.sampled()
        if (!shouldEmit(name, sampled)) return null
        val all = commonAttributes(sampled).apply { putAll(attributes) }
        if (config.beforeSend?.invoke(name, all) == false) return null
        val span = tracer.startSpan(name, parentContext = null, startTimestamp = startNanos) {
            for ((k, v) in all) putAny(k, v)
        }
        currentRootContext = otel.context.root().storeSpan(span)
        persistActiveRoot(span.spanContext.traceId, span.spanContext.spanId, name, all, startNanos)
        return ScoutSpan(span, sampled, isRoot = true)
    }

    fun emitSpan(
        name: String,
        attributes: Map<String, Any> = emptyMap(),
        startNanos: Long = epochNanos(),
        endNanos: Long = startNanos,
        status: StatusData = StatusData.Unset,
        kind: SpanKind = SpanKind.INTERNAL,
    ) {
        val sampled = sessionManager.sampled()
        if (!shouldEmit(name, sampled)) return
        val all = commonAttributes(sampled).apply { putAll(attributes) }
        if (config.beforeSend?.invoke(name, all) == false) return
        val span = tracer.startSpan(name, parentContext = currentRootContext, spanKind = kind, startTimestamp = startNanos) {
            for ((k, v) in all) putAny(k, v)
        }
        span.setStatus(status)
        span.end(endNanos)
        sessionManager.touch()
    }

    fun setAccount(id: String, name: String? = null) = attrLock.withLock {
        if (id.isNotBlank()) runtimeAttrs["account.id"] = id
        name?.takeIf { it.isNotBlank() }?.let { runtimeAttrs["account.name"] = it }
        Unit
    }
    fun clearAccount() = attrLock.withLock {
        runtimeAttrs.remove("account.id")
        runtimeAttrs.remove("account.name")
        Unit
    }
    fun setFeatureFlag(name: String, value: String) = attrLock.withLock {
        runtimeAttrs["feature_flag.$name"] = value
        Unit
    }
    fun clearFeatureFlags() = attrLock.withLock {
        runtimeAttrs.keys.removeAll { it.startsWith("feature_flag.") }
        Unit
    }

    fun setSessionAttributes(attributes: Map<String, String>) = attrLock.withLock {
        runtimeAttrs.keys.removeAll(sessionAttrKeys)
        sessionAttrKeys.clear()
        for ((k, v) in attributes) {
            runtimeAttrs[k] = v
            sessionAttrKeys.add(k)
        }
    }

    fun clearSessionAttributes() = attrLock.withLock {
        runtimeAttrs.keys.removeAll(sessionAttrKeys)
        sessionAttrKeys.clear()
    }

    fun setUserAttributes(attributes: Map<String, String>) = identity.setUserAttributes(attributes)

    fun setBreadcrumbs(crumbs: List<Pair<String, String>>) = breadcrumbs.setAll(crumbs)

    @Volatile
    var onBridgeContextChanged: ((io.base14.scout.core.bridge.SessionContext) -> Unit)? = null
        set(value) {
            field = value
            sessionManager.onSessionChanged = { value?.invoke(bridgeContext()) }
        }

    fun bridgeContext(): io.base14.scout.core.bridge.SessionContext = attrLock.withLock {
        val sessionAttrs = LinkedHashMap<String, String>()
        for (k in sessionAttrKeys) runtimeAttrs[k]?.let { sessionAttrs[k] = it }
        io.base14.scout.core.bridge.SessionContext(
            sessionId = sessionManager.sessionId(),
            sessionStartTime = sessionManager.startTimeIso(),
            sampleRate = config.sessionSampleRate.toString(),
            sampled = sessionManager.sampled(),
            anonymousId = identity.anonymousId,
            userId = identity.userId,
            userAttributes = LinkedHashMap(identity.userAttributes),
            sessionAttributes = sessionAttrs,
        )
    }

    fun adoptExternalSessionId(id: String, startIso: String, sampled: Boolean) =
        sessionManager.adoptExternal(id, sampled, startIso)

    private val forwardedTracers = LinkedHashMap<String, Tracer>()
    private fun tracerFor(scope: String): Tracer = attrLock.withLock {
        forwardedTracers.getOrPut(scope) { otel.tracerProvider.getTracer(scope, version = SCOUT_SDK_VERSION) }
    }

    fun ingestForwardedSpan(fwd: io.base14.scout.core.bridge.ForwardedSpan) {
        val sampled = sessionManager.sampled()
        if (!shouldEmit(fwd.name, sampled)) return
        val all = commonAttributes(sampled).apply { for ((k, v) in fwd.attributes) put(k, v) }
        if (config.beforeSend?.invoke(fwd.name, all) == false) return
        val kind = when (fwd.kind) {
            "CLIENT" -> SpanKind.CLIENT
            "SERVER" -> SpanKind.SERVER
            else -> SpanKind.INTERNAL
        }
        val start = fwd.startUnixNano.toLongOrNull() ?: epochNanos()
        val end = fwd.endUnixNano.toLongOrNull() ?: start
        val span = tracerFor(fwd.scope).startSpan(fwd.name, parentContext = currentRootContext, spanKind = kind, startTimestamp = start) {
            for ((k, v) in all) putAny(k, v)
        }
        if (fwd.status.code == "ERROR") span.setStatus(StatusData.Error(fwd.status.message))
        span.end(end)
        sessionManager.touch()
    }

    fun ingestForwardedSpans(payloadJson: String) {
        for (s in io.base14.scout.core.bridge.BridgeCodec.decodeSpans(payloadJson)) {
            runCatching { ingestForwardedSpan(s) }
        }
    }

    fun mergeBreadcrumbs(crumbs: List<io.base14.scout.core.bridge.BridgeBreadcrumb>) {
        val now = isoUtc(epochMillis())
        for (c in crumbs) breadcrumbs.addAt(c.type, c.message, c.time ?: now)
    }

    fun replaceBreadcrumbs(crumbs: List<io.base14.scout.core.bridge.BridgeBreadcrumb>) {
        val now = isoUtc(epochMillis())
        breadcrumbs.replaceAll(crumbs.map { Breadcrumb(it.type, it.message, it.time ?: now) })
    }

    private val forwardedLoggers = LinkedHashMap<String, Logger>()
    private fun loggerFor(scope: String): Logger = attrLock.withLock {
        forwardedLoggers.getOrPut(scope) { otel.loggerProvider.getLogger(scope, version = SCOUT_SDK_VERSION) }
    }

    fun ingestForwardedLogs(payloadJson: String) {
        if (!config.enableLogging) return
        for (l in io.base14.scout.core.bridge.BridgeCodec.decodeLogs(payloadJson)) {
            runCatching {
                loggerFor(l.scope).emit(
                    body = l.body,
                    severityNumber = severityFromInt(l.severityNumber),
                    severityText = l.severityText,
                    attributes = {
                        setStringAttribute(ScoutAttributes.SESSION_ID, sessionManager.sessionId())
                        setStringAttribute(ScoutAttributes.SESSION_TYPE, "user")
                        setStringAttribute(ScoutAttributes.USER_ANONYMOUS_ID, identity.anonymousId)
                        identity.userId?.let { setStringAttribute(ScoutAttributes.USER_ID, it) }
                        for ((k, v) in l.attributes) putAny(k, v)
                    },
                )
            }
        }
    }

    fun ingestForwardedMetrics(payloadJson: String) {
        if (!config.enableMetrics) return
        val points =
            io.base14.scout.core.bridge.BridgeCodec.decodeMetrics(payloadJson).map { m ->
                val attrs = LinkedHashMap<String, Any>(m.attributes)
                attrs[ScoutAttributes.SESSION_ID] = sessionManager.sessionId()
                MetricPoint(m.name, m.value, m.unit, attrs, m.timestampUnixNano?.toLongOrNull() ?: epochNanos())
            }
        if (points.isNotEmpty()) runCatching { metricEmitter.emit(points) }
    }

    private fun severityFromInt(n: Int): SeverityNumber = when {
        n < 9 -> SeverityNumber.DEBUG
        n < 13 -> SeverityNumber.INFO
        n < 17 -> SeverityNumber.WARN
        else -> SeverityNumber.ERROR
    }

    fun addTiming(name: String) = emitSpan("custom_timing", mapOf("timing.name" to name))

    fun startVital(name: String) {
        attrLock.withLock { vitalStarts[name] = epochNanos() }
    }

    fun endVital(name: String, description: String? = null) {
        val start = attrLock.withLock { vitalStarts.remove(name) } ?: return
        val end = epochNanos()
        val durMs = (end - start) / 1_000_000.0
        val attrs = mutableMapOf<String, Any>(
            ScoutAttributes.VITAL_NAME to name,
            ScoutAttributes.VITAL_DURATION to (durMs / 1000.0).toString(),
            ScoutAttributes.VITAL_DURATION_MS to durMs.toLong(),
        )
        description?.let { attrs["vital.description"] = it }
        emitSpan(ScoutSpans.APP_VITAL, attrs, startNanos = start, endNanos = end)
    }

    fun recordOperationStep(name: String, step: String, key: String? = null, failureReason: String? = null) {
        val attrs = mutableMapOf<String, Any>("operation.name" to name, "operation.step_type" to step)
        key?.let { attrs["operation.key"] = it }
        failureReason?.let { attrs["operation.failure_reason"] = it }
        emitSpan("operation_step", attrs)
    }

    /** Manually record how long a screen took to load (e.g. navigation → first content). */
    fun recordScreenLoad(name: String, durationMs: Long) {
        val end = epochNanos()
        emitSpan(
            ScoutSpans.SCREEN_LOAD,
            mapOf(
                ScoutAttributes.SCREEN_NAME to name,
                ScoutAttributes.SCREEN_LOAD_TIME to (durationMs / 1000.0).toString(),
            ),
            startNanos = end - durationMs * 1_000_000L,
            endNanos = end,
        )
    }

    /** Manually record how long the user spent on a screen/view (dwell time). */
    fun recordViewSession(name: String, durationMs: Long) {
        val end = epochNanos()
        emitSpan(
            ScoutSpans.VIEW_SESSION,
            mapOf(
                ScoutAttributes.SCREEN_NAME to name,
                ScoutAttributes.VIEW_TIME_SPENT to (durationMs / 1000.0).toString(),
            ),
            startNanos = end - durationMs * 1_000_000L,
            endNanos = end,
        )
    }

    /** Manually record a custom span of a given duration with arbitrary attributes. */
    fun recordSpan(name: String, durationMs: Long, attributes: Map<String, Any> = emptyMap()) {
        val end = epochNanos()
        emitSpan(name, attributes, startNanos = end - durationMs * 1_000_000L, endNanos = end)
    }

    private val crashJson = Json { ignoreUnknownKeys = true }

    @Volatile
    var jvmCrashesCapturedThisLaunch: Int = 0

    @Volatile
    var nativeCrashesCapturedThisLaunch: Int = 0

    private val initEpochMillis = epochMillis()

    fun msSinceInit(): Long = epochMillis() - initEpochMillis

    fun sessionIdentityAttrs(): Map<String, Any> = mapOf(
        ScoutAttributes.SESSION_ID to sessionManager.sessionId(),
        ScoutAttributes.SESSION_START_TIME to sessionManager.startTimeIso(),
        ScoutAttributes.SESSION_SAMPLE_RATE to config.sessionSampleRate.toString(),
    )

    fun lastPersistedSessionAttrs(): Map<String, Any>? = runCatching {
        val raw = store.getString(KEY_ACTIVE_ROOT) ?: return null
        val snap = crashJson.decodeFromString(RootSnapshot.serializer(), raw)
        val out = LinkedHashMap<String, Any>()
        snap.attrs[ScoutAttributes.SESSION_ID]?.let { out[ScoutAttributes.SESSION_ID] = it }
        snap.attrs[ScoutAttributes.SESSION_START_TIME]?.let { out[ScoutAttributes.SESSION_START_TIME] = it }
        snap.attrs[ScoutAttributes.SESSION_SAMPLE_RATE]?.let { out[ScoutAttributes.SESSION_SAMPLE_RATE] = it }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private val pendingCrashDir: Path? = cacheDir?.div("scout_pending_crash")

    fun persistCrash(attributes: Map<String, Any>) {
        val obj = buildJsonObject { for ((k, v) in attributes) put(k, v.toString()) }
        val payload = obj.toString()
        val dir = pendingCrashDir
        if (dir != null) {
            val written = runCatching {
                systemFileSystem().createDirectories(dir)
                val name = randomUuidString()
                val tmp = dir / "$name.tmp"
                val bytes = payload.encodeToByteArray()
                val handle = systemFileSystem().openReadWrite(tmp)
                try {
                    handle.write(0L, bytes, 0, bytes.size)
                    handle.flush()
                } finally {
                    handle.close()
                }
                systemFileSystem().atomicMove(tmp, dir / "$name.json")
            }.isSuccess
            if (written) return
        }
        store.putStringDurable(KEY_PENDING_CRASH, payload)
    }

    fun replayPendingCrash() {
        val payloads = ArrayList<String>()
        store.getString(KEY_PENDING_CRASH)?.let {
            store.remove(KEY_PENDING_CRASH)
            payloads.add(it)
        }
        pendingCrashDir?.let { dir ->
            runCatching {
                for (f in systemFileSystem().list(dir)) {
                    if (!f.name.endsWith(".json")) continue
                    runCatching { systemFileSystem().read(f) { readUtf8() } }
                        .getOrNull()?.takeIf { it.isNotBlank() }?.let { payloads.add(it) }
                    runCatching { systemFileSystem().delete(f) }
                }
            }
        }
        for (raw in payloads) replayCrashPayload(raw)
    }

    private fun replayCrashPayload(raw: String) {
        val obj = runCatching { crashJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val attrs = LinkedHashMap<String, Any>()
        for ((k, v) in obj) attrs[k] = v.jsonPrimitive.content
        val type = attrs[ScoutAttributes.ERROR_TYPE]?.toString()
        val message = attrs[ScoutAttributes.ERROR_MESSAGE]?.toString()
        attrs[ScoutAttributes.ERROR_ID] = randomUuidString()
        attrs[ScoutAttributes.ERROR_HANDLED] = "false"
        attrs[ScoutAttributes.ERROR_HANDLING] = "unhandled"
        attrs[ScoutAttributes.ERROR_SOURCE_TYPE] = "android"
        attrs[ScoutAttributes.CRASH_TYPE] = "jvm_crash"
        attrs[ScoutAttributes.CRASH_LAST_SCREEN]?.let { attrs[ScoutAttributes.SCREEN_NAME] = it }
        val statusMessage = message?.takeIf { it.isNotBlank() } ?: type ?: "app crash"
        emitSpan(ScoutSpans.APP_CRASH, attrs, status = StatusData.Error(statusMessage))
        jvmCrashesCapturedThisLaunch++
    }

    @Serializable
    private data class RootSnapshot(
        val traceId: String,
        val spanId: String,
        val name: String,
        val startNanos: Long,
        val attrs: Map<String, String>,
    )

    private fun persistActiveRoot(traceId: String, spanId: String, name: String, attrs: Map<String, Any>, startNanos: Long) {
        runCatching {
            val snap = RootSnapshot(traceId, spanId, name, startNanos, attrs.mapValues { it.value.toString() })
            store.putString(KEY_ACTIVE_ROOT, crashJson.encodeToString(RootSnapshot.serializer(), snap))
        }
    }

    private fun clearActiveRoot() {
        runCatching { store.remove(KEY_ACTIVE_ROOT) }
    }

    fun lastPersistedScreenName(): String? = runCatching {
        val raw = store.getString(KEY_ACTIVE_ROOT) ?: return null
        val snap = crashJson.decodeFromString(RootSnapshot.serializer(), raw)
        snap.attrs[ScoutAttributes.SCREEN_NAME]?.takeIf { it.isNotBlank() } ?: snap.name
    }.getOrNull()

    fun resurrectRootSpan() {
        val raw = store.getString(KEY_ACTIVE_ROOT) ?: return
        store.remove(KEY_ACTIVE_ROOT)
        val snap = runCatching { crashJson.decodeFromString(RootSnapshot.serializer(), raw) }.getOrNull() ?: return
        val body = OtlpJsonSerializer.serializeRaw(
            resourceAttrs = resourceAttrs + (ScoutResourceAttributes.SERVICE_NAME to config.serviceName),
            scopeName = SCOUT_SCOPE_NAME,
            scopeVersion = SCOUT_SDK_VERSION,
            traceId = snap.traceId,
            spanId = snap.spanId,
            name = snap.name,
            attributes = snap.attrs + ("scout.resurrected" to "true"),
            startNanos = snap.startNanos,
            endNanos = epochNanos(),
            statusMessage = "resurrected: app terminated while on screen",
        )
        resurrector.post(body)
    }

    fun log(level: ScoutLogLevel, message: String, attributes: Map<String, Any> = emptyMap()) {
        if (!config.enableLogging) return
        val severity = when (level) {
            ScoutLogLevel.DEBUG -> SeverityNumber.DEBUG
            ScoutLogLevel.INFO -> SeverityNumber.INFO
            ScoutLogLevel.WARNING -> SeverityNumber.WARN
            ScoutLogLevel.ERROR -> SeverityNumber.ERROR
        }
        logger.emit(
            body = message,
            severityNumber = severity,
            severityText = level.name,
            attributes = {
                setStringAttribute(ScoutAttributes.SESSION_ID, sessionManager.sessionId())
                setStringAttribute(ScoutAttributes.SESSION_TYPE, "user")
                setStringAttribute(ScoutAttributes.USER_ANONYMOUS_ID, identity.anonymousId)
                identity.userId?.let { setStringAttribute(ScoutAttributes.USER_ID, it) }
                for ((k, v) in attributes) putAny(k, v)
            },
        )
    }

    fun emit(
        name: String,
        attributes: Map<String, Any> = emptyMap(),
        startNanos: Long = epochNanos(),
        endNanos: Long = startNanos,
        errorMessage: String? = null,
        isClient: Boolean = false,
    ) {
        emitSpan(
            name = name,
            attributes = attributes,
            startNanos = startNanos,
            endNanos = endNanos,
            status = if (errorMessage != null) StatusData.Error(errorMessage) else StatusData.Unset,
            kind = if (isClient) SpanKind.CLIENT else SpanKind.INTERNAL,
        )
    }

    fun logEvent(name: String, attributes: Map<String, Any> = emptyMap()) = emitSpan(name, attributes)

    fun setUser(id: String?, attributes: Map<String, String> = emptyMap()) = identity.setUser(id, attributes)
    fun clearUser() = identity.clearUser()
    fun addBreadcrumb(type: String, message: String) = breadcrumbs.add(type, message)

    val sessionId: String get() = sessionManager.sessionId()
    val anonymousId: String get() = identity.anonymousId
    val userId: String? get() = identity.userId

    private companion object {
        const val KEY_PENDING_CRASH = "scout.pending_crash"
        const val KEY_ACTIVE_ROOT = "scout.active_root"
    }
}
