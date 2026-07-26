package io.base14.scout.android.instrumentation

import android.app.Application
import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans
import java.io.File

internal class NativeCrashHandler(private val app: Application, private val core: ScoutCore) {
    private val crashFile = File(app.cacheDir, "scout_native_crash")
    private val imagesFile = File(app.cacheDir, "scout_native_images")

    fun install() {
        if (!loaded) return
        drainPrevious()
        runCatching { nativeInstall(crashFile.absolutePath, imagesFile.absolutePath) }
    }

    private fun drainPrevious() {
        if (!crashFile.exists()) return
        val report = runCatching { crashFile.readText() }.getOrNull()
        val images = runCatching { if (imagesFile.exists()) imagesFile.readText() else null }.getOrNull()
        runCatching { crashFile.delete() }
        if (report.isNullOrBlank()) return

        var signalName = ""
        var signalNum = 0
        var signalCode = 0
        var faultAddr = ""
        var registers = "{}"
        var threadCount = 0
        val frames = StringBuilder()
        val memoryMap = StringBuilder()
        var inMaps = false

        for (line in report.lineSequence()) {
            when {
                line == "maps_begin" -> inMaps = true
                line == "maps_end" -> inMaps = false
                inMaps -> memoryMap.appendLine(line)
                line.startsWith("signal ") -> {
                    val p = line.split(" ")
                    signalNum = p.getOrNull(1)?.toIntOrNull() ?: 0
                    signalName = p.getOrNull(2) ?: ""
                    signalCode = p.getOrNull(4)?.toIntOrNull() ?: 0
                    faultAddr = p.getOrNull(6) ?: ""
                }
                line.startsWith("thread_count ") -> threadCount = line.removePrefix("thread_count ").trim().toIntOrNull() ?: 0
                line.startsWith("registers ") -> registers = registersToJson(line.removePrefix("registers ").trim())
                line.startsWith("#") -> frames.appendLine(line)
            }
        }

        val attrs =
            mutableMapOf<String, Any>(
                ScoutAttributes.CRASH_TYPE to "native_signal",
                ScoutAttributes.CRASH_SIGNAL to signalName,
                ScoutAttributes.CRASH_SIGNAL_NUMBER to signalNum,
                "crash.signal_code" to signalCode,
                "crash.fault_address" to faultAddr,
                ScoutAttributes.CRASH_STACK_TRACE to frames.toString().trim(),
                "crash.registers_json" to registers,
                ScoutAttributes.CRASH_TIMESTAMP to isoUtc(epochMillis()),
                ScoutAttributes.CRASH_LAST_SCREEN to (core.lastPersistedScreenName() ?: CurrentScreen.name ?: ""),
            )
        attrs["crash.signal_code_name"] = io.base14.scout.android.internal.CrashContext.signalCodeName(signalName, signalCode)
        attrs["crash.report_id"] = randomUuidString()
        attrs["crash.report_type"] = "native_signal"
        attrs["crash.report_version"] = "1.0"
        if (threadCount > 0) attrs["crash.thread_count"] = threadCount
        memoryMap.toString().trim().takeIf { it.isNotEmpty() }?.let { attrs["crash.memory_map"] = it }
        attrs.putAll(io.base14.scout.android.internal.CrashContext.collect(app))
        core.breadcrumbs.previousSessionJson.takeIf { it.isNotBlank() && it != "[]" }
            ?.let { attrs[ScoutAttributes.BREADCRUMBS] = it }
        core.lastPersistedSessionAttrs()?.let { attrs.putAll(it) }
        images?.takeIf { it.isNotBlank() }?.let {
            attrs["crash.binary_images_json"] = imagesToJson(it)
            attrs["crash.binary_images_count"] = it.lineSequence().count { l -> l.isNotBlank() }
        }
        runCatching { imagesFile.delete() }

        core.emit(ScoutSpans.NATIVE_CRASH, attrs, errorMessage = "Native crash ($signalName)")
        core.nativeCrashesCapturedThisLaunch++
    }

    private fun registersToJson(regs: String): String {
        val sb = StringBuilder("{")
        regs.split(" ").filter { it.contains("=") }.forEachIndexed { i, kv ->
            val (k, v) = kv.split("=", limit = 2)
            if (i > 0) sb.append(",")
            sb.append("\"").append(k).append("\":\"").append(v).append("\"")
        }
        return sb.append("}").toString()
    }

    private fun imagesToJson(images: String): String {
        val sb = StringBuilder("[")
        var first = true
        for (line in images.lineSequence()) {
            val parts = line.trim().split(" ")
            if (parts.size < 2) continue
            if (!first) sb.append(",")
            first = false
            sb.append("{\"name\":\"").append(parts[0])
                .append("\",\"load_address\":\"").append(parts[1]).append("\"")
            if (parts.size >= 3 && parts[2].isNotEmpty()) {
                sb.append(",\"uuid\":\"").append(parts[2]).append("\"")
            }
            sb.append("}")
        }
        return sb.append("]").toString()
    }

    private external fun nativeInstall(
        crashPath: String,
        imagesPath: String,
    )

    private companion object {
        val loaded: Boolean =
            runCatching {
                System.loadLibrary("scout_crash")
                true
            }.getOrDefault(false)
    }
}
