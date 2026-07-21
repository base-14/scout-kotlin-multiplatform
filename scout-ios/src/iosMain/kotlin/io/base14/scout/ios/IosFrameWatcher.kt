package io.base14.scout.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

/**
 * Detects janky frames via a main-runloop `CADisplayLink`, inside the Kotlin engine so KMP
 * apps get `long_task`/`frozen_frame` from the single common init. Port of ScoutKit's
 * `FrameWatcher`: a frame interval past 100ms emits `long_task`; past 700ms, `frozen_frame`.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosFrameWatcher {
    private const val LONG_TASK_MS = 100.0
    private const val FROZEN_MS = 700.0
    private var displayLink: CADisplayLink? = null
    private val target = Ticker()

    fun start() {
        if (displayLink != null) return
        val link = CADisplayLink.displayLinkWithTarget(target, platform.Foundation.NSSelectorFromString("scoutTick:"))
        link.addToRunLoop(NSRunLoop.mainRunLoop, forMode = NSRunLoopCommonModes)
        displayLink = link
    }

    fun stop() {
        displayLink?.invalidate()
        displayLink = null
        target.lastTimestamp = 0.0
    }

    internal class Ticker : NSObject() {
        var lastTimestamp: Double = 0.0

        @kotlinx.cinterop.ObjCAction
        fun scoutTick(link: CADisplayLink) {
            val previous = lastTimestamp
            lastTimestamp = link.timestamp
            if (previous == 0.0) return
            val frameMs = (link.timestamp - previous) * 1000.0
            if (frameMs >= FROZEN_MS) {
                ScoutEngine.reportFrozenFrame(frameMs.toLong())
            } else if (frameMs >= LONG_TASK_MS) {
                ScoutEngine.reportLongTask(frameMs.toLong())
            }
        }
    }
}
