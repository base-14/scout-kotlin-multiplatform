package io.base14.scout.android.instrumentation

import android.app.Activity
import android.app.Application
import androidx.metrics.performance.JankStats
import io.base14.scout.android.internal.ActivityLifecycleCallbacksAdapter
import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.android.internal.secondsString
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class JankInstrumentation(
    private val app: Application,
    private val core: ScoutCore,
) {
    private val longTaskMs = core.config.longTaskThresholdMs
    private val frozenMs = core.config.frozenFrameThresholdMs
    private val tracked = HashMap<String, JankStats>()

    fun install() {
        app.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacksAdapter() {
                override fun onActivityResumed(activity: Activity) {
                    val stats =
                        JankStats.createAndTrack(activity.window) { frameData ->
                            val durMs = frameData.frameDurationUiNanos / 1_000_000.0
                            FrameStats.record(durMs)
                            when {
                                durMs >= frozenMs ->
                                    core.emit(
                                        ScoutSpans.FROZEN_FRAME,
                                        mapOf(
                                            ScoutAttributes.FROZEN_FRAME_DURATION to (durMs / 1000.0).toString(),
                                            ScoutAttributes.SCREEN_NAME to (CurrentScreen.name ?: ""),
                                        ),
                                    )
                                frameData.isJank && durMs >= longTaskMs ->
                                    core.emit(
                                        ScoutSpans.LONG_TASK,
                                        mapOf(
                                            ScoutAttributes.LONG_TASK_DURATION to (durMs / 1000.0).toString(),
                                            ScoutAttributes.LONG_TASK_THRESHOLD to secondsString(longTaskMs),
                                            ScoutAttributes.SCREEN_NAME to (CurrentScreen.name ?: ""),
                                        ),
                                    )
                            }
                        }
                    tracked[keyOf(activity)] = stats
                }

                override fun onActivityPaused(activity: Activity) {
                    tracked.remove(keyOf(activity))?.isTrackingEnabled = false
                }
            },
        )
    }

    private fun keyOf(a: Activity) = a::class.qualifiedName + "@" + System.identityHashCode(a)
}
