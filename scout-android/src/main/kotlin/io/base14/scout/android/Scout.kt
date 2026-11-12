package io.base14.scout.android

import android.app.Application
import android.content.Context
import io.base14.scout.android.instrumentation.CrashInstrumentation
import io.base14.scout.android.instrumentation.ScreenTracker
import io.base14.scout.android.internal.AndroidKeyValueStore
import io.base14.scout.android.internal.DeviceResources
import io.base14.scout.android.internal.ScoutInstrumentation
import io.base14.scout.android.internal.createScoutHttpClient
import io.base14.scout.core.PendingEnabled
import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.export.ScoutLogLevel
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

object Scout {
    @Volatile
    private var core: ScoutCore? = null

    @Volatile
    private var screenTracker: ScreenTracker? = null

    /** A [setEnabled] call made before [initialize]; it wins over `ScoutConfig.enabled`. */
    private val pendingEnabled = PendingEnabled()

    val isInitialized: Boolean get() = core != null

    /** The current session id, or null before [initialize] and while RUM is disabled. */
    val sessionId: String? get() = core?.takeIf { it.isEnabled }?.sessionId

    /** Whether RUM is collecting. False before [initialize]. See [setEnabled]. */
    @JvmStatic
    val isEnabled: Boolean get() = core?.isEnabled ?: false
    val userId: String? get() = core?.userId

    val anonymousId: String? get() = core?.identity?.anonymousId

    @JvmStatic
    fun initialize(
        context: Context,
        config: ScoutConfig,
    ) {
        if (core != null) return
        synchronized(this) {
            if (core != null) return
            val application = context.applicationContext as Application
            val effective = pendingEnabled.applyTo(config)
            val created =
                ScoutCore(
                    config = effective,
                    store = AndroidKeyValueStore(application),
                    httpClient = createScoutHttpClient(effective.effectiveExportIntervalSeconds + 35L),
                    platformResourceAttributes = DeviceResources.collect(application),
                    cacheDirPath = application.cacheDir.absolutePath,
                )
            core = created
            runCatching { ScoutBridge.resolveOnInit(application, created, effective.role) }
            val tracker = ScreenTracker(created, effective.enableScreenTracking)
            screenTracker = tracker
            ScoutInstrumentation.installAll(application, created, effective, tracker)
            // Reports from an earlier enabled run wait for an enabled point when this launch starts disabled.
            created.runWhenEnabled {
                runCatching { created.replayPendingCrash() }
                runCatching { created.resurrectRootSpan() }
                runCatching { ScoutInstrumentation.installExitInfoFallback(application, created, effective) }
            }
        }
    }

    /**
     * Runtime kill switch, e.g. to keep RUM off outside working hours. `false`
     * flushes what was already recorded, ends the session and then drops
     * everything (errors and crashes included) and sends nothing. `true` starts
     * a fresh session. Repeating the current value is a no-op. Not persisted:
     * pass `ScoutConfig.enabled` on every launch. A call before [initialize] is
     * held and wins over the config value.
     */
    @JvmStatic
    fun setEnabled(value: Boolean) {
        val current = core
        if (current != null) {
            current.setEnabled(value)
            return
        }
        synchronized(this) {
            val c = core
            if (c == null) pendingEnabled.hold(value) else c.setEnabled(value)
        }
    }

    @JvmStatic
    fun setScreen(name: String) {
        screenTracker?.enter(name)
    }

    @JvmStatic
    @JvmOverloads
    fun logDebug(
        message: String,
        attributes: Map<String, Any> = emptyMap(),
    ) = core?.log(ScoutLogLevel.DEBUG, message, attributes) ?: Unit

    @JvmStatic
    @JvmOverloads
    fun logInfo(
        message: String,
        attributes: Map<String, Any> = emptyMap(),
    ) = core?.log(ScoutLogLevel.INFO, message, attributes) ?: Unit

    @JvmStatic
    @JvmOverloads
    fun logWarning(
        message: String,
        attributes: Map<String, Any> = emptyMap(),
    ) = core?.log(ScoutLogLevel.WARNING, message, attributes) ?: Unit

    @JvmStatic
    @JvmOverloads
    fun logError(
        message: String,
        attributes: Map<String, Any> = emptyMap(),
    ) = core?.log(ScoutLogLevel.ERROR, message, attributes) ?: Unit

    @JvmStatic
    @JvmOverloads
    fun setUser(
        id: String?,
        attributes: Map<String, String> = emptyMap(),
    ) {
        core?.setUser(id, attributes)
    }

    @JvmStatic
    fun setUserAttributes(attributes: Map<String, String>) {
        core?.setUserAttributes(attributes)
    }

    @JvmStatic
    fun clearUser() {
        core?.clearUser()
    }

    /**
     * Per-user sampling decided by your app, e.g. from your remote config.
     * `true` keeps this user's sessions, `false` drops their spans (logs and
     * metrics still flow), and `null` falls back to `sessionSampleRate`. It
     * applies from the next session start, never to the current one, and is
     * persisted across launches.
     */
    @JvmStatic
    fun setUserSampling(value: Boolean?) {
        ScoutBridge.setUserSampling(value)
    }

    @JvmStatic
    @JvmOverloads
    fun log(
        level: ScoutLogLevel,
        message: String,
        attributes: Map<String, Any> = emptyMap(),
    ) = core?.log(level, message, attributes) ?: Unit

    @JvmStatic
    @JvmOverloads
    fun logEvent(
        name: String,
        attributes: Map<String, Any> = emptyMap(),
    ) {
        core?.logEvent(name, attributes)
    }

    @JvmStatic
    fun addBreadcrumb(
        type: String,
        message: String,
    ) {
        core?.addBreadcrumb(type, message)
    }

    @JvmStatic
    @JvmOverloads
    fun reportError(
        throwable: Throwable,
        handled: Boolean = true,
    ) {
        core?.let { if (it.config.enableErrorTracking) CrashInstrumentation.reportHandled(it, throwable, handled) }
    }

    @JvmStatic
    fun reportError(
        type: String,
        message: String,
        stackTrace: String,
    ) {
        core?.let { if (it.config.enableErrorTracking) CrashInstrumentation.reportHandled(it, type, message, stackTrace) }
    }

    @JvmStatic
    @JvmOverloads
    fun setAccount(
        id: String,
        name: String? = null,
    ) {
        core?.setAccount(id, name)
    }

    @JvmStatic
    fun clearAccount() {
        core?.clearAccount()
    }

    @JvmStatic
    fun setFeatureFlag(
        name: String,
        value: String,
    ) {
        core?.setFeatureFlag(name, value)
    }

    @JvmStatic
    fun clearFeatureFlags() {
        core?.clearFeatureFlags()
    }

    @JvmStatic
    fun setSessionAttributes(attributes: Map<String, String>) {
        core?.setSessionAttributes(attributes)
    }

    @JvmStatic
    fun clearSessionAttributes() {
        core?.clearSessionAttributes()
    }

    @JvmStatic
    fun setBreadcrumbs(breadcrumbs: List<Pair<String, String>>) {
        core?.setBreadcrumbs(breadcrumbs)
    }

    @JvmStatic
    fun addTiming(name: String) {
        core?.addTiming(name)
    }

    @JvmStatic
    fun startVital(name: String) {
        core?.startVital(name)
    }

    @JvmStatic
    @JvmOverloads
    fun endVital(
        name: String,
        description: String? = null,
    ) {
        core?.endVital(name, description)
    }

    @JvmStatic
    @JvmOverloads
    fun recordOperationStep(
        name: String,
        step: String,
        key: String? = null,
        failureReason: String? = null,
    ) {
        core?.recordOperationStep(name, step, key, failureReason)
    }

    @JvmStatic
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

    @JvmStatic
    fun reportLongTask(durationMs: Long) {
        core?.emit(
            name = ScoutSpans.LONG_TASK,
            attributes = mapOf(ScoutAttributes.LONG_TASK_DURATION to durationMs.toString()),
        )
    }

    @JvmStatic
    fun reportTap(
        target: String,
        targetType: String,
        x: Double,
        y: Double,
    ) {
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

    @JvmStatic
    fun emitGauge(
        name: String,
        value: Double,
        unit: String,
    ) {
        core?.emitGauge(name, value, unit)
    }

    @JvmStatic
    fun recordScreenLoad(
        name: String,
        durationMs: Long,
    ) {
        core?.recordScreenLoad(name, durationMs)
    }

    @JvmStatic
    fun recordViewSession(
        name: String,
        durationMs: Long,
    ) {
        core?.recordViewSession(name, durationMs)
    }

    @JvmStatic
    @JvmOverloads
    fun recordSpan(
        name: String,
        durationMs: Long,
        attributes: Map<String, Any> = emptyMap(),
    ) {
        core?.recordSpan(name, durationMs, attributes)
    }

    internal fun coreInternal(): ScoutCore? = core
}
