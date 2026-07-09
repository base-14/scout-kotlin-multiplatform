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

    fun start() {
        if (core.config.enableStartupTracking) {
            emitStartup()
        }
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, queue) { _ ->
            core.sessionManager.onForeground()
            emitLifecycle("active")
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ ->
            core.sessionManager.onBackground()
            emitLifecycle("background")
        }
    }

    private fun emitStartup() {
        core.emit(
            name = ScoutSpans.APP_STARTUP,
            startNanos = processStartNanos,
            endNanos = epochNanos(),
        )
    }

    private fun emitLifecycle(state: String) {
        val previous = lastState
        lastState = state
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
