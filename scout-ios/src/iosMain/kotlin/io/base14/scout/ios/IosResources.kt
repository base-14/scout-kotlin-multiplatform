package io.base14.scout.ios

import io.base14.scout.core.semantics.ScoutResourceAttributes
import platform.UIKit.UIDevice

fun iosResourceAttributes(): Map<String, String> {
    val device = UIDevice.currentDevice
    return mapOf(
        ScoutResourceAttributes.OS_NAME to "iOS",
        ScoutResourceAttributes.OS_VERSION to device.systemVersion,
        ScoutResourceAttributes.DEVICE_MANUFACTURER to "Apple",
        ScoutResourceAttributes.DEVICE_BRAND to "Apple",
        ScoutResourceAttributes.DEVICE_NAME to device.name,
        ScoutResourceAttributes.DEVICE_MODEL_NAME to device.model,
        ScoutResourceAttributes.DEVICE_TYPE to "mobile",
    )
}
