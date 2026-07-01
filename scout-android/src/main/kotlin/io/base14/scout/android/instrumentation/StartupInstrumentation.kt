package io.base14.scout.android.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Process
import android.os.SystemClock
import io.base14.scout.android.internal.ActivityLifecycleCallbacksAdapter
import io.base14.scout.android.internal.secondsString
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class StartupInstrumentation(
    private val app: Application,
    private val core: ScoutCore,
) {
    fun install() {
        val processStart =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Process.getStartUptimeMillis()
            } else {
                SystemClock.uptimeMillis()
            }
        app.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacksAdapter() {
                private var reported = false

                override fun onActivityResumed(activity: Activity) {
                    if (reported) return
                    reported = true
                    val durMs = SystemClock.uptimeMillis() - processStart
                    core.addBreadcrumb("startup", "cold ${durMs}ms")
                    core.emit(
                        ScoutSpans.APP_STARTUP,
                        mapOf(
                            ScoutAttributes.APP_STARTUP_TYPE to "cold",
                            ScoutAttributes.APP_STARTUP_DURATION to secondsString(durMs),
                        ),
                    )
                    VitalTracker.emitFbc(core, durMs)
                    app.unregisterActivityLifecycleCallbacks(this)
                }
            },
        )
    }
}
