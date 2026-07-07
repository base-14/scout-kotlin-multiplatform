package io.base14.scout.android.instrumentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import io.base14.scout.android.internal.DeviceResources
import io.base14.scout.core.ScoutCore
import java.net.NetworkInterface
import io.base14.scout.core.semantics.ScoutResourceAttributes as R

internal class DynamicAttributes(private val app: Application) {
    @Volatile private var connectionType: String = "unknown"

    fun install(core: ScoutCore) {
        connectionType = runCatching { DeviceResources.networkType(app) }.getOrDefault("unknown")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            refresh()
                        }

                        override fun onLost(network: Network) {
                            refresh()
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: android.net.NetworkCapabilities,
                        ) {
                            refresh()
                        }
                    },
                )
            }
        }
        core.dynamicAttributesProvider = {
            buildMap {
                put(R.NETWORK_CONNECTION_TYPE, connectionType)
                put(R.NETWORK_CONNECTIVITY_STATUS, connectivityStatus())
                networkInterfaces().takeIf { it.isNotEmpty() }?.let { put(R.NETWORK_INTERFACES, it) }
                put(R.DEVICE_ORIENTATION, orientation())
                batteryLevel()?.let { put(R.DEVICE_BATTERY_LEVEL, it.toString()) }
                put(R.DEVICE_BATTERY_STATE, batteryState())
                batteryDischargeRate()?.let { put(R.DEVICE_BATTERY_DISCHARGE_RATE, it.toString()) }
            }
        }
    }

    private fun batteryManager(): BatteryManager? =
        app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    /** Charge level 0..100, or null if unavailable. */
    private fun batteryLevel(): Int? =
        runCatching {
            val level = batteryManager()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            level?.takeIf { it in 0..100 }
        }.getOrNull()

    private fun batteryState(): String =
        runCatching {
            val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }
        }.getOrDefault("unknown")

    /** Instantaneous current in microamps (matches scout-flutter), or null if unavailable. */
    private fun batteryDischargeRate(): Long? =
        runCatching {
            val current = batteryManager()?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            current?.takeIf { it != Long.MIN_VALUE && it != 0L }
        }.getOrNull()

    private fun refresh() {
        connectionType = runCatching { DeviceResources.networkType(app) }.getOrDefault("unknown")
    }

    private fun orientation(): String =
        if (app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"

    /** Whether an active network with internet capability exists. */
    private fun connectivityStatus(): String =
        runCatching {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val connected =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                } else {
                    @Suppress("DEPRECATION")
                    cm.activeNetworkInfo?.isConnected == true
                }
            if (connected) "connected" else "disconnected"
        }.getOrDefault("unknown")

    /** Comma-separated names of the currently up, non-loopback network interfaces. */
    private fun networkInterfaces(): String =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .map { it.name }
                .toList()
                .joinToString(",")
        }.getOrDefault("")
}
