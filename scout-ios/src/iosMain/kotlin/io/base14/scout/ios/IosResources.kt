package io.base14.scout.ios

import io.base14.scout.core.semantics.ScoutResourceAttributes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
private fun deviceMachine(): String? = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) != 0) return null
    info.machine.toKString().takeIf { it.isNotBlank() }
}

fun iosResourceAttributes(): Map<String, String> {
    val device = UIDevice.currentDevice
    val machine = deviceMachine()
    return buildMap {
        put(ScoutResourceAttributes.OS_NAME, "iOS")
        put(ScoutResourceAttributes.OS_VERSION, device.systemVersion)
        put(ScoutResourceAttributes.DEVICE_MANUFACTURER, "Apple")
        put(ScoutResourceAttributes.DEVICE_BRAND, "Apple")
        put(ScoutResourceAttributes.DEVICE_NAME, device.name)
        put(ScoutResourceAttributes.DEVICE_MODEL_NAME, machine ?: device.model)
        put(ScoutResourceAttributes.DEVICE_TYPE, "mobile")
        machine?.let { put(ScoutResourceAttributes.HOST_ARCH, it) }
        put(ScoutResourceAttributes.DEVICE_LOCALE, NSLocale.currentLocale.localeIdentifier)
        put(ScoutResourceAttributes.DEVICE_TIMEZONE, NSTimeZone.localTimeZone.name)
    }
}
