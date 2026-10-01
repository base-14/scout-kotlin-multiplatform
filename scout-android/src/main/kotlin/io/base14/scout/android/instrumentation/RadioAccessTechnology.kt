package io.base14.scout.android.instrumentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

internal class RadioAccessTechnology(private val context: Context) {
    @Volatile private var fromDisplayInfo: String = ""

    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val telephony = context.getSystemService(TelephonyManager::class.java) ?: return
            telephony.registerTelephonyCallback(context.mainExecutor, DisplayInfoCallback())
        }
    }

    fun current(): String = fromDisplayInfo.ifEmpty { fromDataNetworkType() }

    private fun fromDataNetworkType(): String {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ""
        }
        return runCatching {
            val telephony = context.getSystemService(TelephonyManager::class.java)
            if (telephony == null) "" else subtypeOf(telephony.dataNetworkType)
        }.getOrDefault("")
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private inner class DisplayInfoCallback : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            fromDisplayInfo =
                subtypeOf(telephonyDisplayInfo.networkType, telephonyDisplayInfo.overrideNetworkType)
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        @android.annotation.SuppressLint("InlinedApi")
        fun subtypeOf(
            networkType: Int,
            overrideNetworkType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
        ): String {
            val overridden =
                when (overrideNetworkType) {
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                    -> "lte_ca"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                    -> "nrnsa"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "nr"
                    else -> ""
                }
            if (overridden.isNotEmpty()) return overridden
            return when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "nr"
                TelephonyManager.NETWORK_TYPE_LTE -> "lte"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "hspap"
                TelephonyManager.NETWORK_TYPE_HSPA -> "hspa"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "hsdpa"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "hsupa"
                TelephonyManager.NETWORK_TYPE_UMTS -> "umts"
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "td_scdma"
                TelephonyManager.NETWORK_TYPE_EDGE -> "edge"
                TelephonyManager.NETWORK_TYPE_GPRS -> "gprs"
                TelephonyManager.NETWORK_TYPE_GSM -> "gsm"
                TelephonyManager.NETWORK_TYPE_CDMA -> "cdma"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "cdma2000_1xrtt"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "evdo_0"
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "evdo_a"
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "evdo_b"
                TelephonyManager.NETWORK_TYPE_EHRPD -> "ehrpd"
                TelephonyManager.NETWORK_TYPE_IDEN -> "iden"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "iwlan"
                else -> ""
            }
        }
    }
}
