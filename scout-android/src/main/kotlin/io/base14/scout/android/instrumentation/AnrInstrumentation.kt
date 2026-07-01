package io.base14.scout.android.instrumentation

import android.os.Handler
import android.os.Looper
import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.android.internal.secondsString
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal class AnrInstrumentation(private val core: ScoutCore) {
    private val thresholdMs = core.config.anrThresholdMs

    private val pollIntervalMs = (thresholdMs / 5).coerceIn(200L, 1_000L)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = true

    fun install() {
        val watchdog =
            Thread {
                val responded = AtomicBoolean(true)
                var blockedFor = 0L
                var reported = false
                while (running) {
                    if (responded.compareAndSet(true, false)) {
                        blockedFor = 0L
                        reported = false
                        mainHandler.post { responded.set(true) }
                    } else {
                        blockedFor += pollIntervalMs
                        if (blockedFor >= thresholdMs && !reported) {
                            val stalled = blockedFor
                            runCatching { report(stalled) }
                            reported = true
                        }
                    }
                    Thread.sleep(pollIntervalMs)
                }
            }
        watchdog.isDaemon = true
        watchdog.name = "scout-anr-watchdog"
        watchdog.start()
    }

    private fun report(blockedMs: Long) {
        val mainThread = Looper.getMainLooper().thread
        val allStacks = runCatching { Thread.getAllStackTraces() }.getOrDefault(emptyMap())
        val mainStack = (allStacks[mainThread] ?: mainThread.stackTrace).joinToString("\n") { it.toString() }
        core.addBreadcrumb("anr", "App not responding: ${blockedMs}ms")
        val message = "Application Not Responding"
        core.emit(
            ScoutSpans.ANR,
            mapOf(
                ScoutAttributes.ANR_DURATION to secondsString(blockedMs),
                ScoutAttributes.ANR_THRESHOLD to secondsString(thresholdMs),
                ScoutAttributes.SCREEN_NAME to (CurrentScreen.name ?: core.lastPersistedScreenName() ?: ""),
                ScoutAttributes.ANR_MAIN_THREAD_STACK to mainStack,
                ScoutAttributes.ANR_THREADS_JSON to threadsJson(allStacks),
                ScoutAttributes.ANR_THREAD_COUNT to allStacks.size,
                ScoutAttributes.BREADCRUMBS to core.breadcrumbs.toJson(),
                ScoutAttributes.CRASH_TYPE to "anr",
                ScoutAttributes.ERROR_MESSAGE to message,
            ),
            errorMessage = message,
        )
    }

    private fun threadsJson(stacks: Map<Thread, Array<StackTraceElement>>): String {
        val arr = JSONArray()
        var totalBytes = 0
        for ((thread, frames) in stacks) {
            val obj = JSONObject()
            obj.put("name", thread.name)
            obj.put("state", thread.state.name)
            obj.put("priority", thread.priority)
            obj.put("daemon", thread.isDaemon)
            val frameArr = JSONArray()
            for ((i, frame) in frames.withIndex()) {
                if (i >= MAX_FRAMES_PER_THREAD) break
                frameArr.put(frame.toString())
            }
            obj.put("frames", frameArr)
            val rendered = obj.toString()
            if (totalBytes + rendered.length > MAX_THREADS_JSON_BYTES) break
            arr.put(obj)
            totalBytes += rendered.length
        }
        return arr.toString()
    }

    private companion object {
        const val MAX_FRAMES_PER_THREAD = 64
        const val MAX_THREADS_JSON_BYTES = 32_000
    }
}
