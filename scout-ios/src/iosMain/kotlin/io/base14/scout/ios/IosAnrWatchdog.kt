package io.base14.scout.ios

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.usleep

/**
 * Main-thread hang / ANR watchdog living inside the Kotlin engine, so KMP apps get ANR
 * detection from the single common `Scout.initialize` call (no Swift layer required).
 * A background queue posts a heartbeat onto the main queue every poll interval; if the
 * main thread doesn't service it within the threshold the hang is reported once at
 * threshold-cross. Mirrors ScoutKit's `AppHangWatchdog`.
 *
 * Follow-up (matches the Swift version): capture the hung main-thread stack via a mach
 * frame-pointer unwind; for now the ANR carries the duration only.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosAnrWatchdog(
    private val thresholdMs: Long,
    private val report: (durationMs: Long, mainThreadStack: String) -> Unit,
) {
    private val running = AtomicInt(0)
    private val lastHeartbeatMs = AtomicLong(0)
    private val inHang = AtomicInt(0)
    private val pollIntervalMs: Long = maxOf(200L, thresholdMs / 5)
    private var mainThreadPort: platform.darwin.thread_t = 0u

    private fun nowMs(): Long = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

    fun start() {
        if (!running.compareAndSet(0, 1)) return
        mainThreadPort = IosThreadBacktrace.currentPort()
        lastHeartbeatMs.value = nowMs()
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
            loop()
        }
    }

    fun stop() {
        running.value = 0
    }

    private fun loop() {
        while (running.value == 1) {
            dispatch_async(dispatch_get_main_queue()) {
                lastHeartbeatMs.value = nowMs()
            }
            usleep((pollIntervalMs * 1000).toUInt())
            val elapsedMs = nowMs() - lastHeartbeatMs.value
            if (elapsedMs >= thresholdMs) {
                if (inHang.compareAndSet(0, 1)) {
                    val stack = IosThreadBacktrace.capture(mainThreadPort).joinToString("\n")
                    report(elapsedMs, stack)
                }
            } else {
                inHang.value = 0
            }
        }
    }
}
