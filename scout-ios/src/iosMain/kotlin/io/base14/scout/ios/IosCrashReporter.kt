package io.base14.scout.ios

import io.base14.scout.kscrash.KSCrash
import io.base14.scout.kscrash.KSCrashConfiguration
import io.base14.scout.kscrash.KSCrashMonitorTypeApplicationState
import io.base14.scout.kscrash.KSCrashMonitorTypeCPPException
import io.base14.scout.kscrash.KSCrashMonitorTypeMachException
import io.base14.scout.kscrash.KSCrashMonitorTypeMainThreadDeadlock
import io.base14.scout.kscrash.KSCrashMonitorTypeNSException
import io.base14.scout.kscrash.KSCrashMonitorTypeSignal
import io.base14.scout.kscrash.KSCrashMonitorTypeSystem
import io.base14.scout.kscrash.KSCrashMonitorTypeUserReported
import io.base14.scout.kscrash.KSCrashReportStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create

/**
 * KSCrash-backed crash reporting living inside the Kotlin engine, so KMP apps get crash
 * capture from the single common `Scout.initialize` call. `install()` arms the handlers;
 * `drainPending()` reads reports captured on the previous run, flattens them into
 * `crash.*` attributes, and emits `native_crash` + `app_crash` through the engine.
 * Port of ScoutKit's `ScoutCrashReporter`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosCrashReporter {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val config = KSCrashConfiguration()
        config.monitors =
            KSCrashMonitorTypeMachException or KSCrashMonitorTypeSignal or
            KSCrashMonitorTypeCPPException or KSCrashMonitorTypeNSException or
            KSCrashMonitorTypeMainThreadDeadlock or KSCrashMonitorTypeUserReported or
            KSCrashMonitorTypeSystem or KSCrashMonitorTypeApplicationState
        KSCrash.sharedInstance.installWithConfiguration(config, null)
    }

    fun drainPending() {
        val store = KSCrashReportStore.defaultStoreWithError(null) ?: return
        val ids = store.reportIDs
        if (ids.isEmpty()) return
        for (id in ids) {
            val reportId = (id as? platform.Foundation.NSNumber)?.longLongValue ?: continue
            val dict = store.reportForID(reportId)?.value ?: continue
            val json = nsDictToJson(dict) ?: continue
            val parsed = parseReport(json) ?: continue
            val attrs = LinkedHashMap<String, String>()
            for ((key, value) in parsed) {
                val mapped = if (key.startsWith("crash_")) "crash." + key.removePrefix("crash_") else key
                attrs[mapped] = value
            }
            attrs.getOrPut("error.message") { attrs["crash.reason"] ?: "native crash" }
            attrs.getOrPut("error.type") {
                attrs["crash.mach_exception"] ?: attrs["crash.signal"] ?: attrs["crash.type"] ?: "crash"
            }
            ScoutEngine.reportNativeCrash(attrs)
            ScoutEngine.reportAppCrash(attrs)
        }
        store.deleteAllReports()
    }

    private fun nsDictToJson(dict: Any): JsonObject? {
        val data = NSJSONSerialization.dataWithJSONObject(dict, 0u, null) ?: return null
        val str = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return null
        return runCatching { Json.parseToJsonElement(str) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.prim(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.content.takeIf { it.isNotEmpty() }
    }

    private fun MutableMap<String, String>.put(key: String, source: JsonObject?, field: String) {
        source?.prim(field)?.let { this[key] = it }
    }

    private fun parseReport(report: JsonObject): Map<String, String>? {
        val out = LinkedHashMap<String, String>()

        report.obj("report")?.let { info ->
            out.put("crash_timestamp", info, "timestamp")
            out.put("crash_report_id", info, "id")
            out.put("crash_report_type", info, "type")
            out.put("crash_report_version", info, "version")
        }

        report.obj("system")?.let { system ->
            out.put("crash_os_name", system, "system_name")
            out.put("crash_os_version", system, "system_version")
            out.put("crash_os_build", system, "os_version")
            out.put("crash_kernel_version", system, "kernel_version")
            out.put("crash_boot_time", system, "boot_time")
            out.put("crash_app_start_time", system, "app_start_time")
            out.put("crash_time_zone", system, "time_zone")
            out.put("crash_device_model", system, "model")
            out.put("crash_machine", system, "machine")
            out.put("crash_cpu_arch", system, "cpu_arch")
            out.put("crash_cpu_type", system, "cpu_type")
            out.put("crash_cpu_subtype", system, "cpu_subtype")
            out.put("crash_app_name", system, "CFBundleName")
            out.put("crash_app_executable", system, "CFBundleExecutable")
            out.put("crash_bundle_id", system, "CFBundleIdentifier")
            out.put("crash_app_version", system, "CFBundleShortVersionString")
            out.put("crash_bundle_version", system, "CFBundleVersion")
            out.put("crash_process_name", system, "process_name")
            out.put("crash_pid", system, "process_id")
            system.obj("memory")?.let { memory ->
                out.put("crash_memory_size_bytes", memory, "size")
                out.put("crash_memory_free_bytes", memory, "free")
                out.put("crash_memory_usable_bytes", memory, "usable")
            }
            system.obj("app_memory")?.let { appMem ->
                out.put("crash_memory_footprint", appMem, "memory_footprint")
                out.put("crash_memory_remaining", appMem, "memory_remaining")
                out.put("crash_memory_pressure", appMem, "memory_pressure")
                out.put("crash_memory_level", appMem, "memory_level")
                out.put("crash_memory_limit", appMem, "memory_limit")
            }
            (system["jailbroken"] as? JsonPrimitive)?.booleanOrNull?.let { out["crash_jailbroken"] = it.toString() }
            system.obj("application_stats")?.let { stats ->
                out.put("crash_app_active_time_secs", stats, "active_time_since_launch")
                out.put("crash_app_background_time_secs", stats, "background_time_since_launch")
                out.put("crash_app_launches_since_last_crash", stats, "launches_since_last_crash")
                out.put("crash_app_sessions_since_launch", stats, "sessions_since_launch")
            }
        }

        val crash = report.obj("crash")
        val error = crash?.obj("error")
        out.put("crash_type", error, "type")
        out.put("crash_diagnosis", crash, "diagnosis")

        error?.obj("mach")?.let { mach ->
            mach.prim("exception_name")?.let {
                out["crash_mach_exception"] = it
                out.getOrPut("crash_reason") { it }
            }
            out.put("crash_mach_code", mach, "code")
            out.put("crash_mach_code_name", mach, "code_name")
            out.put("crash_mach_subcode", mach, "subcode")
        }
        error?.obj("signal")?.let { signal ->
            signal.prim("name")?.let {
                out["crash_signal"] = it
                out.getOrPut("crash_reason") { it }
            }
            out.put("crash_signal_code", signal, "code")
            out.put("crash_signal_code_name", signal, "code_name")
            out.put("crash_signal_number", signal, "signal")
        }
        error?.obj("nsexception")?.let { nsex ->
            out.put("crash_nsexception_name", nsex, "name")
            nsex.prim("reason")?.let { out.getOrPut("crash_reason") { it } }
        }
        out.put("crash_fault_address", error, "address")
        out.put("crash_crashing_thread_index", crash, "crashed_thread")
        error?.prim("reason")?.let { out.getOrPut("crash_reason") { it } }
        out.getOrPut("crash_reason") { "Unknown" }

        crash?.arr("threads")?.let { threads ->
            out["crash_thread_count"] = threads.size.toString()
            val objs = threads.filterIsInstance<JsonObject>()
            val crashed = objs.firstOrNull { (it["crashed"] as? JsonPrimitive)?.booleanOrNull == true } ?: objs.firstOrNull()
            crashed?.prim("name")?.let { out["crash_thread_name"] = it }
            out.put("crash_thread_index", crashed, "index")
            crashed?.obj("backtrace")?.arr("contents")?.let { frames ->
                out["crash_stack_trace"] = formatStack(frames)
            }
            crashed?.obj("registers")?.let { out["crash_registers_json"] = it.toString() }
            out["crash_callstack_tree_json"] = threads.toString()
        }
        report.arr("binary_images")?.let { images ->
            out["crash_binary_images_json"] = images.toString()
            out["crash_binary_images_count"] = images.size.toString()
        }
        report.obj("user")?.let { out["crash_user_info_json"] = it.toString() }

        return out.ifEmpty { null }
    }

    private fun formatStack(frames: JsonArray): String =
        frames.filterIsInstance<JsonObject>().joinToString("\n") { frame ->
            val symbol = frame.prim("symbol_name") ?: "??"
            val addr = (frame["instruction_addr"] as? JsonPrimitive)?.longOrNull ?: 0L
            val obj = frame.prim("object_name") ?: "??"
            val symAddr = (frame["symbol_addr"] as? JsonPrimitive)?.longOrNull ?: 0L
            val delta = if (addr >= symAddr) addr - symAddr else 0L
            "$obj 0x${addr.toString(16)} $symbol + $delta"
        }
}
