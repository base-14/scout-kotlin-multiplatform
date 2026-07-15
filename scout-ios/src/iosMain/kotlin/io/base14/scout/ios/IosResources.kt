package io.base14.scout.ios

import io.base14.scout.core.semantics.ScoutResourceAttributes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
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

/** Darwin kernel version (uname release), used as os.build to mirror Android's Build.DISPLAY. */
@OptIn(ExperimentalForeignApi::class)
private fun osBuild(): String? = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) != 0) return null
    info.release.toKString().takeIf { it.isNotBlank() }
}

private fun isPhysicalDevice(): Boolean =
    NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] == null

/**
 * Best-effort jailbreak detection (matches scout-flutter's `isJailbroken`): known jailbreak app /
 * MobileSubstrate paths, an injected-dylib env var, and a write outside the app sandbox. Returns
 * false on the Simulator (which runs on the host Mac and would otherwise false-positive the probe).
 */
@OptIn(ExperimentalForeignApi::class)
private fun isJailbroken(): Boolean {
    val env = NSProcessInfo.processInfo.environment
    if (env["SIMULATOR_DEVICE_NAME"] != null) return false
    val fm = NSFileManager.defaultManager
    val paths = listOf(
        "/Applications/Cydia.app",
        "/Library/MobileSubstrate/MobileSubstrate.dylib",
        "/bin/bash",
        "/usr/sbin/sshd",
        "/etc/apt",
        "/private/var/lib/apt/",
        "/private/var/lib/cydia",
        "/usr/libexec/ssh-keysign",
        "/usr/libexec/sftp-server",
        "/Applications/Sileo.app",
        "/Applications/Zebra.app",
    )
    for (path in paths) {
        if (fm.fileExistsAtPath(path)) return true
    }
    if (env["DYLD_INSERT_LIBRARIES"] != null) return true
    // Writing outside the sandbox only succeeds on a jailbroken device.
    val probe = "/private/scout_jb_probe.txt"
    if (fm.createFileAtPath(probe, null, null)) {
        fm.removeItemAtPath(probe, null)
        return true
    }
    return false
}

fun iosResourceAttributes(): Map<String, String> {
    val device = UIDevice.currentDevice
    val machine = deviceMachine()
    return buildMap {
        put(ScoutResourceAttributes.OS_NAME, "iOS")
        put(ScoutResourceAttributes.SCOUT_IOS_VERSION, ScoutIosBuildInfo.IOS_VERSION)
        put(ScoutResourceAttributes.OS_VERSION, device.systemVersion)
        device.systemVersion.substringBefore(".").takeIf { it.isNotBlank() }?.let {
            put(ScoutResourceAttributes.OS_VERSION_MAJOR, it)
        }
        osBuild()?.let { put(ScoutResourceAttributes.OS_BUILD, it) }
        val bundle = NSBundle.mainBundle
        bundle.bundleIdentifier?.let { put(ScoutResourceAttributes.APP_BUNDLE_ID, it) }
        (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)?.let {
            put(ScoutResourceAttributes.APP_VERSION, it)
        }
        (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.let {
            put(ScoutResourceAttributes.APP_BUILD, it)
        }
        put(ScoutResourceAttributes.DEVICE_IS_PHYSICAL, isPhysicalDevice().toString())
        put(ScoutResourceAttributes.DEVICE_MANUFACTURER, "Apple")
        put(ScoutResourceAttributes.DEVICE_BRAND, "Apple")
        put(ScoutResourceAttributes.DEVICE_NAME, device.name)
        put(ScoutResourceAttributes.DEVICE_MODEL_NAME, machine ?: device.model)
        put(ScoutResourceAttributes.DEVICE_TYPE, "mobile")
        put(ScoutResourceAttributes.DEVICE_IS_JAIL_BROKEN, isJailbroken().toString())
        machine?.let { put(ScoutResourceAttributes.HOST_ARCH, it) }
        put(ScoutResourceAttributes.DEVICE_LOCALE, NSLocale.currentLocale.localeIdentifier)
        put(ScoutResourceAttributes.DEVICE_TIMEZONE, NSTimeZone.localTimeZone.name)
    }
}
