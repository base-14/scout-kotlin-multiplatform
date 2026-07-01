package io.base14.scout.android.instrumentation

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import io.base14.scout.android.internal.DeviceResources
import io.base14.scout.core.ScoutCore

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
            mapOf(
                "network.connection.type" to connectionType,
                "device.orientation" to orientation(),
            )
        }
    }

    private fun refresh() {
        connectionType = runCatching { DeviceResources.networkType(app) }.getOrDefault("unknown")
    }

    private fun orientation(): String =
        if (app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
}
