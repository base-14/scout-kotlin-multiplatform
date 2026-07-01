package io.base14.scout.android.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.Locale
import java.util.TimeZone
import io.base14.scout.core.semantics.ScoutResourceAttributes as R

internal object DeviceResources {
    fun collect(context: Context): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m[R.OS_NAME] = "Android"
        m[R.OS_VERSION] = Build.VERSION.RELEASE ?: ""
        m[R.OS_BUILD] = Build.DISPLAY ?: ""
        m[R.DEVICE_MODEL_NAME] = Build.MODEL ?: ""
        m[R.DEVICE_NAME] = Build.MODEL ?: ""
        m[R.DEVICE_MANUFACTURER] = Build.MANUFACTURER ?: ""
        m[R.DEVICE_BRAND] = Build.BRAND ?: ""
        m[R.DEVICE_TYPE] = "mobile"
        m[R.HOST_ARCH] = normalizeArch(Build.SUPPORTED_ABIS?.firstOrNull())
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
