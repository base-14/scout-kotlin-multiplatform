package io.base14.scout.ios

import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.epochNanos
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

internal class IosInstrumentation(
    private val core: ScoutCore,
    private val processStartNanos: Long,
) {
    private var lastState: String = "inactive"
    private var anrWatchdog: IosAnrWatchdog? = null

    fun start() {
        if (core.config.enableStartupTracking) {
            emitStartup()
        }
        if (core.config.enableAnrTracking) {
            anrWatchdog = IosAnrWatchdog(core.config.anrThresholdMs) { durationMs, stack ->
                ScoutEngine.reportAnr(durationMs, stack)
            }.also { it.start() }
        }
        if (core.config.enableTapTracking) {
            IosTapTracking.install()
        }
        if (core.config.enableHttpTracking) {
            IosHttpTracking.install()
        }
        if (core.config.enableJankTracking) {
            IosFrameWatcher.start(
                longTaskMs = core.config.longTaskThresholdMs.toDouble(),
                frozenMs = core.config.frozenFrameThresholdMs.toDouble(),
            )
        }
        if (core.config.enableScreenTracking) {
            IosScreenTracking.install()
        }
        IosMetricsCollector.start(
            memoryEnabled = core.config.enableMemoryMetrics,
            cpuEnabled = core.config.enableCpuMetrics,
            intervalSeconds = core.config.effectiveVitalsCollectionIntervalSeconds,
        )
        if (core.config.enableLifecycleTracking) {
            val center = NSNotificationCenter.defaultCenter
            val queue = NSOperationQueue.mainQueue
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, queue) { _ ->
                core.sessionManager.onForeground()
                emitLifecycle("active")
            }
            center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ ->
                core.sessionManager.onBackground()
                emitLifecycle("background")
                core.forceFlush()
            }
        }
    }

    private fun emitStartup() {
        val durMs = (epochNanos() - processStartNanos) / 1_000_000L
        core.addBreadcrumb("startup", "cold ${durMs}ms")
        core.emit(
            name = ScoutSpans.APP_STARTUP,
            startNanos = processStartNanos,
            endNanos = epochNanos(),
        )
    }

    private fun emitLifecycle(state: String) {
        val previous = lastState
        lastState = state
        core.addBreadcrumb("lifecycle", state)
        core.emit(
            name = ScoutSpans.APP_LIFECYCLE_CHANGED,
            attributes = mapOf(
                ScoutAttributes.APP_LIFECYCLE_ID to randomUuidString(),
                ScoutAttributes.APP_LIFECYCLE_STATE to state,
                ScoutAttributes.APP_LIFECYCLE_PREVIOUS_STATE to previous,
                ScoutAttributes.APP_LIFECYCLE_TIMESTAMP to isoUtc(epochMillis()),
            ),
        )
    }
}
