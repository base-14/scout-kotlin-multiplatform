package io.base14.scout.android.internal

import android.app.Application
import io.base14.scout.android.instrumentation.AnrInstrumentation
import io.base14.scout.android.instrumentation.CrashInstrumentation
import io.base14.scout.android.instrumentation.DynamicAttributes
import io.base14.scout.android.instrumentation.ExitInfoInstrumentation
import io.base14.scout.android.instrumentation.JankInstrumentation
import io.base14.scout.android.instrumentation.LifecycleInstrumentation
import io.base14.scout.android.instrumentation.NativeCrashHandler
import io.base14.scout.android.instrumentation.ScoutMetrics
import io.base14.scout.android.instrumentation.ScreenInstrumentation
import io.base14.scout.android.instrumentation.ScreenTracker
import io.base14.scout.android.instrumentation.StartupInstrumentation
import io.base14.scout.android.instrumentation.TapInstrumentation
import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutCore

internal object ScoutInstrumentation {
    fun installAll(
        app: Application,
        core: ScoutCore,
        config: ScoutConfig,
        screenTracker: ScreenTracker,
    ) {
        if (config.enableLifecycleTracking) safe { LifecycleInstrumentation(core).install() }
        if (config.enableStartupTracking) safe { StartupInstrumentation(app, core).install() }
        if (config.enableScreenTracking) safe { ScreenInstrumentation(app, screenTracker).install() }
        if (config.enableCrashTracking || config.enableErrorTracking) safe { CrashInstrumentation(core).install() }
        if (config.enableAnrTracking) safe { AnrInstrumentation(core).install() }
        if (config.enableCrashTracking) safe { NativeCrashHandler(app, core).install() }
        if (config.enableJankTracking) safe { JankInstrumentation(app, core).install() }
        if (config.enableTapTracking) safe { TapInstrumentation(app, core).install() }
        if (config.enableMetrics) safe { ScoutMetrics(core).install() }
        safe { DynamicAttributes(app).install(core) }
    }

    fun installExitInfoFallback(
        app: Application,
        core: ScoutCore,
        config: ScoutConfig,
    ) {
        if (config.enableCrashTracking) safe { ExitInfoInstrumentation(app, core).install() }
    }

    private inline fun safe(block: () -> Unit) {
        runCatching { block() }
    }
}
