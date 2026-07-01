package io.base14.scout.android.internal

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StatFs

internal object CrashContext {
    fun collect(app: Application): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        runCatching {
            out["crash.os_name"] = "Android"
            out["crash.os_version"] = Build.VERSION.RELEASE ?: ""
            out["crash.os_build"] = Build.ID ?: ""
            out["crash.device_model"] = Build.MODEL ?: ""
            out["crash.machine"] = "${Build.MANUFACTURER} ${Build.MODEL}"
            out["crash.cpu_arch"] = Build.SUPPORTED_ABIS?.firstOrNull() ?: ""
            out["crash.kernel_version"] = System.getProperty("os.version") ?: ""
            out["crash.build_fingerprint"] = Build.FINGERPRINT ?: ""
            out["crash.bundle_id"] = app.packageName
        }
        runCatching {
            @Suppress("DEPRECATION")
            val pi = app.packageManager.getPackageInfo(app.packageName, 0)
            pi.versionName?.let { out["crash.app_version"] = it }
            out["crash.bundle_version"] = pi.longVersionCodeCompat().toString()
        }
        runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            out["crash.memory_size_bytes"] = mi.totalMem
            out["crash.memory_free_bytes"] = mi.availMem
        }
        runCatching {
            val sf = StatFs(app.filesDir.absolutePath)
            out["crash.storage_size_bytes"] = sf.totalBytes
            out["crash.storage_free_bytes"] = sf.availableBytes
        }
        return out
    }

    fun signalCodeName(
        signal: String,
        code: Int,
    ): String =
        when (signal) {
            "SIGSEGV" ->
                when (code) {
                    1 -> "SEGV_MAPERR"
                    2 -> "SEGV_ACCERR"
                    else -> siUser(code)
                }
            "SIGBUS" ->
                when (code) {
                    1 -> "BUS_ADRALN"
                    2 -> "BUS_ADRERR"
                    3 -> "BUS_OBJERR"
                    else -> siUser(code)
                }
            "SIGFPE" ->
                when (code) {
                    1 -> "FPE_INTDIV"
                    2 -> "FPE_INTOVF"
                    3 -> "FPE_FLTDIV"
                    else -> siUser(code)
                }
            "SIGILL" ->
                when (code) {
                    1 -> "ILL_ILLOPC"
                    2 -> "ILL_ILLOPN"
                    else -> siUser(code)
                }
            else -> siUser(code)
        }

    private fun siUser(code: Int) =
        when (code) {
            0 -> "SI_USER"
            -6 -> "SI_TKILL"
            else -> "code $code"
        }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}
