package io.base14.scout.ios

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.darwin.DISPATCH_QUEUE_PRIORITY_BACKGROUND
import platform.darwin.KERN_SUCCESS
import platform.darwin.TASK_VM_INFO
import platform.darwin.TH_USAGE_SCALE
import platform.darwin.THREAD_BASIC_INFO
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.integer_tVar
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.natural_t
import platform.darwin.task_info
import platform.darwin.task_threads
import platform.darwin.task_vm_info
import platform.darwin.thread_act_array_tVar
import platform.darwin.thread_basic_info
import platform.darwin.thread_info
import platform.darwin.thread_t
import platform.darwin.vm_deallocate
import platform.posix.usleep

/**
 * Periodic process memory (resident size) and CPU gauges via mach, inside the Kotlin engine
 * so KMP apps get vitals from the common init. Port of ScoutKit's `MetricsCollector`.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosMetricsCollector {
    private const val TH_FLAGS_IDLE = 2
    private val running = AtomicInt(0)
    private var memoryEnabled = false
    private var cpuEnabled = false
    private var intervalSeconds = 60

    fun start(memoryEnabled: Boolean, cpuEnabled: Boolean, intervalSeconds: Int) {
        if (!memoryEnabled && !cpuEnabled) return
        if (!running.compareAndSet(0, 1)) return
        this.memoryEnabled = memoryEnabled
        this.cpuEnabled = cpuEnabled
        this.intervalSeconds = maxOf(1, intervalSeconds)
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND.toLong(), 0u)) {
            loop()
        }
    }

    fun stop() {
        running.value = 0
    }

    private fun loop() {
        usleep(1_000_000u)
        while (running.value == 1) {
            sample()
            usleep((intervalSeconds * 1_000_000).toUInt())
        }
    }

    private fun sample() {
        if (memoryEnabled) {
            physFootprintBytes()?.let { ScoutEngine.emitGauge("process.memory.usage", it, "By") }
        }
        if (cpuEnabled) {
            ScoutEngine.emitGauge("process.cpu.usage", cpuUsagePercent(), "1")
        }
    }

    private fun physFootprintBytes(): Double? = memScoped {
        val info = alloc<task_vm_info>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info>() / sizeOf<platform.darwin.natural_tVar>()).toUInt()
        val kr = task_info(
            mach_task_self_,
            TASK_VM_INFO.toUInt(),
            info.ptr.reinterpret<integer_tVar>(),
            count.ptr,
        )
        if (kr == KERN_SUCCESS) info.phys_footprint.toDouble() else null
    }

    private fun cpuUsagePercent(): Double = memScoped {
        val threads = alloc<thread_act_array_tVar>()
        val threadCount = alloc<mach_msg_type_number_tVar>()
        if (task_threads(mach_task_self_, threads.ptr, threadCount.ptr) != KERN_SUCCESS) return@memScoped 0.0
        val list = threads.value ?: return@memScoped 0.0
        var total = 0.0
        try {
            for (i in 0 until threadCount.value.toInt()) {
                val info = alloc<thread_basic_info>()
                val count = alloc<mach_msg_type_number_tVar>()
                count.value = (sizeOf<thread_basic_info>() / sizeOf<platform.darwin.natural_tVar>()).toUInt()
                val kr = thread_info(
                    list[i],
                    THREAD_BASIC_INFO.toUInt(),
                    info.ptr.reinterpret<integer_tVar>(),
                    count.ptr,
                )
                if (kr == KERN_SUCCESS && (info.flags and TH_FLAGS_IDLE) == 0) {
                    total += info.cpu_usage.toDouble() / TH_USAGE_SCALE.toDouble()
                }
            }
        } finally {
            vm_deallocate(
                mach_task_self_,
                list.rawValue.toLong().toULong(),
                (threadCount.value.toLong() * sizeOf<platform.darwin.thread_tVar>()).toULong(),
            )
        }
        total * 100.0
    }
}
