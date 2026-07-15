package io.base14.scout.android.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.base14.scout.android.BuildConfig
import io.base14.scout.android.instrumentation.NativeLibInfo
import java.util.Locale
import java.util.TimeZone
import io.base14.scout.core.semantics.ScoutResourceAttributes as R

internal object DeviceResources {
    fun collect(context: Context): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m[R.OS_NAME] = "Android"
        m[R.SCOUT_ANDROID_VERSION] = BuildConfig.SCOUT_ANDROID_VERSION
        m[R.OS_VERSION] = Build.VERSION.RELEASE ?: ""
        (Build.VERSION.RELEASE ?: "").substringBefore(".").takeIf { it.isNotBlank() }?.let { m[R.OS_VERSION_MAJOR] = it }
        m[R.OS_BUILD] = Build.DISPLAY ?: ""
        m[R.DEVICE_MODEL_NAME] = Build.MODEL ?: ""
        m[R.DEVICE_NAME] = Build.MODEL ?: ""
        m[R.DEVICE_MANUFACTURER] = Build.MANUFACTURER ?: ""
        m[R.DEVICE_BRAND] = Build.BRAND ?: ""
        m[R.DEVICE_TYPE] = "mobile"
        m[R.DEVICE_IS_PHYSICAL] = (!isEmulator()).toString()
        m[R.DEVICE_IS_JAIL_BROKEN] = isDeviceRooted(context).toString()
        m[R.HOST_ARCH] = normalizeArch(Build.SUPPORTED_ABIS?.firstOrNull())
        NativeLibInfo.buildId()?.let { m[R.NDK_BUILD_ID] = it }
        m[R.APP_BUNDLE_ID] = context.packageName
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            m[R.APP_VERSION] = pi.versionName ?: ""
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            m[R.APP_BUILD] = code.toString()
        }
        m[R.DEVICE_LOCALE] = Locale.getDefault().toLanguageTag()
        m[R.DEVICE_TIMEZONE] = TimeZone.getDefault().id
        m[R.NETWORK_CONNECTION_TYPE] = networkType(context)
        return m
    }

    internal fun networkType(context: Context): String =
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork ?: return "none"
                val caps = cm.getNetworkCapabilities(active) ?: return "unknown"
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "unknown"
                }
            } else {
                @Suppress("DEPRECATION")
                when (cm.activeNetworkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "wifi"
                    ConnectivityManager.TYPE_MOBILE -> "cellular"
                    ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                    null -> "none"
                    else -> "unknown"
                }
            }
        }.getOrDefault("unknown")

    /** Heuristic emulator detection (device.is_physical = !emulator), matching common RUM SDKs. */
    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val hardware = Build.HARDWARE ?: ""
        return fp.startsWith("generic") || fp.startsWith("unknown") || fp.contains("emulator") ||
            model.contains("google_sdk") || model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            (Build.MANUFACTURER ?: "").contains("Genymotion") ||
            hardware.contains("goldfish") || hardware.contains("ranchu") ||
            Build.PRODUCT == "google_sdk" || Build.PRODUCT == "sdk_gphone64_arm64" ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    /**
     * Best-effort root detection (matches scout-flutter's `isDeviceRooted`): `test-keys` build tag,
     * known su / Magisk binaries, `which su`, and installed root-management packages. Any failure
     * is treated as "not rooted" so instrumentation never crashes the host app.
     */
    private fun isDeviceRooted(context: Context): Boolean {
        return try {
            if (Build.TAGS?.contains("test-keys") == true) return true
            val rootPaths = arrayOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/app/Superuser.apk", "/data/local/su",
                "/data/local/bin/su", "/data/local/xbin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su",
                "/su/bin/su", "/sbin/.magisk", "/cache/.disable_magisk",
                "/dev/.magisk.unblock",
            )
            for (path in rootPaths) {
                if (java.io.File(path).exists()) return true
            }
            var p: Process? = null
            try {
                p = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
                val found = p.inputStream.bufferedReader().use { it.readLine() != null }
                if (found) return true
            } catch (_: Throwable) {
            } finally {
                try {
                    p?.destroy()
                } catch (_: Throwable) {}
            }
            val rootPackages = arrayOf(
                "com.devadvance.rootcloak",
                "com.devadvance.rootcloakplus",
                "com.koushikdutta.superuser",
                "com.thirdparty.superuser",
                "eu.chainfire.supersu",
                "com.noshufou.android.su",
                "com.topjohnwu.magisk",
            )
            val pm = context.packageManager
            for (pkg in rootPackages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun normalizeArch(abi: String?): String =
        when {
            abi == null -> "unknown"
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm32"
            abi == "x86_64" -> "amd64"
            abi == "x86" -> "x86"
            else -> abi
        }
}
