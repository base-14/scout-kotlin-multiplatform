package io.base14.scout.android.instrumentation

import android.os.SystemClock
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal object VitalTracker {
    private const val INV_WINDOW_MS = 5_000L

    @Volatile
    private var lastInteractionUptime = 0L

    fun onInteraction() {
        lastInteractionUptime = SystemClock.uptimeMillis()
    }

    fun onScreenChange(
        core: ScoutCore,
        from: String?,
        to: String,
    ) {
        val last = lastInteractionUptime
        if (last == 0L) return
        val deltaMs = SystemClock.uptimeMillis() - last
        lastInteractionUptime = 0L
        if (deltaMs in 0..INV_WINDOW_MS) {
            core.emit(
                ScoutSpans.APP_VITAL,
                mapOf(
                    ScoutAttributes.VITAL_NAME to "inv",
                    ScoutAttributes.VITAL_TYPE to "navigation",
                    ScoutAttributes.VITAL_DURATION to (deltaMs / 1000.0).toString(),
                    ScoutAttributes.VITAL_DURATION_MS to deltaMs,
                    ScoutAttributes.VITAL_FROM_SCREEN to (from ?: ""),
                    ScoutAttributes.VITAL_TO_SCREEN to to,
                ),
            )
        }
    }

    fun emitFbc(
        core: ScoutCore,
        durationMs: Long,
    ) {
        core.emit(
            ScoutSpans.APP_VITAL,
            mapOf(
                ScoutAttributes.VITAL_NAME to "fbc",
                ScoutAttributes.VITAL_TYPE to "startup",
                ScoutAttributes.VITAL_DURATION to (durationMs / 1000.0).toString(),
                ScoutAttributes.VITAL_DURATION_MS to durationMs,
            ),
        )
    }
}
