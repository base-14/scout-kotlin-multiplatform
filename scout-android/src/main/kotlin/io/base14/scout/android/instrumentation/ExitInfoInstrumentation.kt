package io.base14.scout.android.instrumentation

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class ExitInfoInstrumentation(
    private val app: Application,
    private val core: ScoutCore,
) {
    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val prefs = app.getSharedPreferences("scout_rum", Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST, 0L)
            var newest = last
            var skipNative = core.nativeCrashesCapturedThisLaunch
            for (info in am.getHistoricalProcessExitReasons(app.packageName, 0, 20)) {
                if (info.timestamp <= last) continue
                if (info.timestamp > newest) newest = info.timestamp
                when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE ->
                        if (skipNative > 0) {
                            skipNative--
                        } else {
                            emitCrash(info, ScoutSpans.NATIVE_CRASH, "native_signal")
                        }
                    ApplicationExitInfo.REASON_CRASH ->
                        if (!core.crashAlreadyReported(info.timestamp, CRASH_DEDUP_WINDOW_MS)) {
                            emitCrash(info, ScoutSpans.APP_CRASH, "jvm_exception")
                            core.markCrashReported(info.timestamp)
                        }
                    ApplicationExitInfo.REASON_ANR -> emitAnr(info)
                }
            }
            prefs.edit().putLong(KEY_LAST, newest).apply()
        }
    }

    private fun emitCrash(
        info: ApplicationExitInfo,
        spanName: String,
        type: String,
    ) {
        core.emit(
            spanName,
            mapOf(
                ScoutAttributes.CRASH_TYPE to type,
                ScoutAttributes.CRASH_REASON to (info.description ?: "exit reason ${info.reason}"),
                ScoutAttributes.CRASH_TIMESTAMP to isoUtc(info.timestamp),
                "crash.importance" to info.importance,
                "crash.pss_kb" to info.pss,
                "crash.rss_kb" to info.rss,
            ),
            errorMessage = info.description ?: "crash (ApplicationExitInfo)",
        )
    }

    private fun emitAnr(info: ApplicationExitInfo) {
        val tombstone = readTrace(info)
        val attrs =
            mutableMapOf<String, Any>(
                ScoutAttributes.ANR_DURATION to "0.0",
                ScoutAttributes.ANR_THRESHOLD to "5.0",
                ScoutAttributes.CRASH_REASON to (info.description ?: "ANR"),
                ScoutAttributes.CRASH_TIMESTAMP to isoUtc(info.timestamp),
                ScoutAttributes.CRASH_TYPE to "anr",
                ScoutAttributes.ERROR_MESSAGE to (info.description ?: "Application Not Responding"),
                ScoutAttributes.CRASH_LAST_SCREEN to (core.lastPersistedScreenName() ?: ""),
                ScoutAttributes.SCREEN_NAME to (core.lastPersistedScreenName() ?: ""),
            )
        if (!tombstone.isNullOrBlank()) {
            attrs["crash.tombstone"] = tombstone
            val threads = AnrTombstone.parse(tombstone)
            if (threads.isNotEmpty()) {
                attrs[ScoutAttributes.ANR_THREADS_JSON] = AnrTombstone.toJson(threads)
                attrs[ScoutAttributes.ANR_THREAD_COUNT] = threads.size
                AnrTombstone.mainStack(threads)?.let { attrs[ScoutAttributes.ANR_MAIN_THREAD_STACK] = it }
            }
        }
        core.breadcrumbs.previousSessionJson.takeIf { it.isNotBlank() && it != "[]" }
            ?.let { attrs[ScoutAttributes.BREADCRUMBS] = it }
        core.lastPersistedSessionAttrs()?.let { attrs.putAll(it) }
        core.emit(ScoutSpans.ANR, attrs, errorMessage = "Application Not Responding")
    }

    private fun readTrace(info: ApplicationExitInfo): String? =
        runCatching {
            info.traceInputStream?.bufferedReader()?.use { it.readText().take(MAX_TOMBSTONE_BYTES) }
        }.getOrNull()

    companion object {
        private const val KEY_LAST = "scout.last_exit_ts"
        private const val MAX_TOMBSTONE_BYTES = 128_000
        private const val CRASH_DEDUP_WINDOW_MS = 10_000L
    }
}
