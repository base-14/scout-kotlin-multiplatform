package io.base14.scout.android.instrumentation

import android.os.SystemClock
import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.android.internal.secondsString
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class ScreenTracker(private val core: ScoutCore) {
    private var current: String? = null
    private var enterUptime = 0L
    private var first = true

    @Volatile private var manualMode = false
    private var rootSpan: ScoutCore.ScoutSpan? = null

    fun enter(
        name: String,
        loadTimeMs: Long? = null,
    ) {
        manualMode = true
        doEnter(name, loadTimeMs)
    }

    fun enterFromActivity(
        name: String,
        loadTimeMs: Long? = null,
    ) {
        if (manualMode) return
        doEnter(name, loadTimeMs)
    }

    fun exitFromActivity() {
        if (manualMode) return
        exitCurrent()
    }

    private fun doEnter(
        name: String,
        loadTimeMs: Long?,
    ) {
        if (name == current) return
        val referrer = current
        exitCurrent()

        val attrs =
            mutableMapOf<String, Any>(
                ScoutAttributes.SCREEN_NAME to name,
                ScoutAttributes.VIEW_ID to randomUuidString(),
                ScoutAttributes.VIEW_LOADING_TYPE to if (first) "initial_load" else "route_change",
                ScoutAttributes.VIEW_IS_ACTIVE to true,
            )
        referrer?.let { attrs[ScoutAttributes.VIEW_REFERRER] = it }
        core.addBreadcrumb("navigation", name)
        rootSpan = core.beginScreen(ScoutSpans.SCREEN_VIEW, attrs)

        if (loadTimeMs != null) {
            core.emit(
                ScoutSpans.SCREEN_LOAD,
                mapOf(
                    ScoutAttributes.SCREEN_NAME to name,
                    ScoutAttributes.SCREEN_LOAD_TIME to secondsString(loadTimeMs),
                ),
            )
        }

        VitalTracker.onScreenChange(core, referrer, name)

        current = name
        CurrentScreen.name = name
        enterUptime = SystemClock.uptimeMillis()
        first = false
    }

    fun exitCurrent() {
        val s = current ?: return
        core.emit(
            ScoutSpans.VIEW_SESSION,
            mapOf(
                ScoutAttributes.SCREEN_NAME to s,
                ScoutAttributes.VIEW_TIME_SPENT to secondsString(SystemClock.uptimeMillis() - enterUptime),
            ),
        )
        rootSpan?.end()
        rootSpan = null
        current = null
    }
}
