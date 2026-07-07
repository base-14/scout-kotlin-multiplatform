package io.base14.scout.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithAddress
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceOrientation
import platform.posix.AF_INET
import platform.posix.sockaddr_in
import io.base14.scout.core.semantics.ScoutResourceAttributes as R

/**
 * Per-span dynamic attributes for iOS (mirrors Android's DynamicAttributes): live network
 * connection type, device orientation, and battery level/state. Wired into
 * ScoutCore.dynamicAttributesProvider so every span/session/error carries the current values.
 */
internal object IosDynamicAttributes {

    /** Enable UIKit monitoring once (battery + orientation) before the first read. */
    fun enableMonitoring() {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        device.beginGeneratingDeviceOrientationNotifications()
    }

    fun collect(): Map<String, Any> = buildMap {
        put(R.NETWORK_CONNECTION_TYPE, connectionType())
        put(R.NETWORK_CONNECTIVITY_STATUS, connectivityStatus())
        put(R.DEVICE_ORIENTATION, orientation())
        batteryLevel()?.let { put(R.DEVICE_BATTERY_LEVEL, it.toString()) }
        put(R.DEVICE_BATTERY_STATE, batteryState())
    }

    private fun orientation(): String =
        when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft,
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight,
            -> "landscape"
            else -> "portrait"
        }

    /** Charge level 0..100, or null if unavailable (the Simulator reports -1). */
    private fun batteryLevel(): Int? {
        val level = UIDevice.currentDevice.batteryLevel
        return if (level < 0f) null else (level * 100f).toInt()
    }

    private fun batteryState(): String =
        when (UIDevice.currentDevice.batteryState) {
            UIDeviceBatteryState.UIDeviceBatteryStateCharging -> "charging"
            UIDeviceBatteryState.UIDeviceBatteryStateFull -> "full"
            UIDeviceBatteryState.UIDeviceBatteryStateUnplugged -> "discharging"
            else -> "unknown"
        }

    /**
     * Synchronous reachability of a zero address (mirrors Apple's Reachability sample). Returns the
     * raw SCNetworkReachabilityFlags, or null on any failure.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun reachabilityFlags(): UInt? = memScoped {
        val addr = alloc<sockaddr_in>()
        addr.sin_len = sizeOf<sockaddr_in>().convert()
        addr.sin_family = AF_INET.convert()
        val reachability = SCNetworkReachabilityCreateWithAddress(null, addr.ptr.reinterpret())
            ?: return@memScoped null
        try {
            val flags = alloc<UIntVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr.reinterpret())) null else flags.value
        } finally {
            CFRelease(reachability)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun connectionType(): String {
        val f = reachabilityFlags() ?: return "unknown"
        return when {
            (f and kSCNetworkReachabilityFlagsReachable.convert<UInt>()) == 0u -> "none"
            (f and kSCNetworkReachabilityFlagsIsWWAN.convert<UInt>()) != 0u -> "cellular"
            else -> "wifi"
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun connectivityStatus(): String {
        val f = reachabilityFlags() ?: return "unknown"
        return if ((f and kSCNetworkReachabilityFlagsReachable.convert<UInt>()) != 0u) "connected" else "disconnected"
    }
}
