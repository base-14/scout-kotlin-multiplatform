package io.base14.scout.android.instrumentation

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.base14.scout.android.internal.secondsString
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class LifecycleInstrumentation(private val core: ScoutCore) : DefaultLifecycleObserver {
    private var firstStart = true
    private var backgroundedUptime = 0L

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        core.sessionManager.onForeground()
        if (!firstStart) {
            val durMs = if (backgroundedUptime > 0) SystemClock.uptimeMillis() - backgroundedUptime else 0
            core.emit(
                ScoutSpans.APP_STARTUP,
                mapOf(
                    ScoutAttributes.APP_STARTUP_TYPE to "warm",
                    ScoutAttributes.APP_STARTUP_DURATION to secondsString(durMs),
                ),
            )
        }
        firstStart = false
        emit("device.app.lifecycle.resumed")
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedUptime = SystemClock.uptimeMillis()
        emit("device.app.lifecycle.paused")
        core.sessionManager.onBackground()
    }

    private fun emit(state: String) {
        core.addBreadcrumb("lifecycle", state)
        core.emit(
            ScoutSpans.APP_LIFECYCLE_CHANGED,
            mapOf(
                ScoutAttributes.APP_LIFECYCLE_ID to randomUuidString(),
                ScoutAttributes.APP_LIFECYCLE_STATE to state,
                ScoutAttributes.APP_LIFECYCLE_TIMESTAMP to isoUtc(epochMillis()),
            ),
        )
    }
}
