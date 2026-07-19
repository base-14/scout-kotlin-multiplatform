package io.base14.scout.android.instrumentation

import android.os.SystemClock
import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.semantics.ScoutAttributes
import java.io.File

internal class ScoutMetrics(private val core: ScoutCore) {
    @Volatile private var running = true
    private var lastCpuTicks = -1L
    private var lastCpuWallMs = 0L

    fun install() {
        val config = core.config
        val memoryOn = config.enableMemoryMetrics
        val cpuOn = config.enableCpuMetrics
        val frameOn = config.enableFrameMetrics
        if (!memoryOn && !cpuOn && !frameOn) return
        val intervalMs = config.effectiveVitalsCollectionIntervalSeconds * 1000L
        val thread =
            Thread {
                val runtime = Runtime.getRuntime()
                while (running) {
                    val screenAttr = mapOf<String, Any>(ScoutAttributes.SCREEN_NAME to (CurrentScreen.name ?: ""))
                    if (memoryOn) {
                        runCatching {
                            val usedBytes = (runtime.totalMemory() - runtime.freeMemory()).toDouble()
                            core.emitGauge("android.memory.usage", usedBytes, "By", screenAttr)
                        }
                    }
                    if (cpuOn) {
                        runCatching { cpuPercent()?.let { core.emitGauge("android.cpu.usage", it, "%", screenAttr) } }
                    }
                    if (frameOn) {
                        runCatching {
                            FrameStats.drainAverageMs()?.let { core.emitGauge("android.frame.build_time", it, "ms", screenAttr) }
                        }
                    }
                    Thread.sleep(intervalMs)
                }
            }
        thread.isDaemon = true
        thread.name = "scout-metrics"
        thread.start()
    }

    private fun cpuPercent(): Double? {
        val text = File("/proc/self/stat").readText()
        val after = text.substring(text.lastIndexOf(") ") + 2).trim().split(Regex("\\s+"))
        val ticks = after[11].toLong() + after[12].toLong()
        val nowMs = SystemClock.elapsedRealtime()
        if (lastCpuTicks < 0) {
            lastCpuTicks = ticks
            lastCpuWallMs = nowMs
            return null
        }
        val deltaTicks = ticks - lastCpuTicks
        val deltaWallMs = nowMs - lastCpuWallMs
        lastCpuTicks = ticks
        lastCpuWallMs = nowMs
        if (deltaWallMs <= 0) return null
        return (deltaTicks.toDouble() / CLK_TCK) / (deltaWallMs / 1000.0) * 100.0
    }

    private companion object {
        const val CLK_TCK = 100.0
    }
}
