package io.base14.scout.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.ARM_THREAD_STATE64
import platform.darwin.KERN_SUCCESS
import platform.darwin.arm_thread_state64_t
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_thread_self
import platform.darwin.natural_tVar
import platform.darwin.thread_get_state
import platform.darwin.thread_resume
import platform.darwin.thread_suspend
import platform.darwin.thread_t
import platform.posix.Dl_info
import platform.posix.dladdr
import platform.posix.uintptr_tVar

/**
 * Captures a backtrace of an arbitrary (typically the hung main) thread by suspending it,
 * reading its state via `thread_get_state`, and walking the frame-pointer chain,
 * symbolicating each return address with `dladdr`. Kotlin/Native port of ScoutKit's
 * `ScoutThreadBacktrace`, so the engine-owned ANR watchdog reports stacks on the KMP path.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosThreadBacktrace {
    private const val ADDRESS_MASK: ULong = 0x0000_FFFF_FFFF_FFFFuL
    private const val MAX_FRAMES = 64

    fun currentPort(): thread_t = mach_thread_self()

    fun capture(thread: thread_t): List<String> {
        if (thread == 0u) return emptyList()
        if (thread_suspend(thread) != KERN_SUCCESS) return emptyList()
        try {
            return memScoped {
                val state = alloc<arm_thread_state64_t>()
                val count = alloc<mach_msg_type_number_tVar>()
                count.value = (sizeOf<arm_thread_state64_t>() / 4L).toUInt()
                val kr = thread_get_state(
                    thread,
                    ARM_THREAD_STATE64,
                    state.ptr.reinterpret<natural_tVar>(),
                    count.ptr,
                )
                if (kr != KERN_SUCCESS) return@memScoped emptyList()

                val pc = state.__pc and ADDRESS_MASK
                var fp = state.__fp

                val frames = ArrayList<String>()
                if (pc != 0uL) frames.add(symbolicate(pc))

                var previousFp = 0uL
                var depth = 0
                while (fp != 0uL && depth < MAX_FRAMES) {
                    if (fp <= previousFp) break
                    if (fp % 16u != 0uL) break
                    val slots = fp.toLong().toCPointer<uintptr_tVar>() ?: break
                    val savedFp = slots[0].toULong()
                    val savedLr = slots[1].toULong() and ADDRESS_MASK
                    if (savedLr == 0uL) break
                    frames.add(symbolicate(savedLr))
                    previousFp = fp
                    fp = savedFp
                    depth++
                }
                frames
            }
        } finally {
            thread_resume(thread)
        }
    }

    private fun symbolicate(address: ULong): String = memScoped {
        val hex = "0x" + address.toString(16)
        val info = alloc<Dl_info>()
        val ok = dladdr(address.toLong().toCPointer<kotlinx.cinterop.ByteVar>(), info.ptr)
        val name = if (ok != 0) info.dli_sname?.toKString() else null
        if (name != null) {
            val base = info.dli_saddr?.rawValue?.toLong()?.toULong() ?: 0uL
            val offset = if (address >= base) address - base else 0uL
            "$hex $name + $offset"
        } else {
            hex
        }
    }
}
