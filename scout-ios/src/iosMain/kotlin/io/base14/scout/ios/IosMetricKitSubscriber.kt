package io.base14.scout.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUnitDuration
import platform.Foundation.create
import platform.MetricKit.MXCrashDiagnostic
import platform.MetricKit.MXDiagnosticPayload
import platform.MetricKit.MXHangDiagnostic
import platform.MetricKit.MXMetricManager
import platform.MetricKit.MXMetricManagerSubscriberProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosMetricKitSubscriber {
    private var subscriber: Subscriber? = null

    fun start() {
        if (subscriber != null) return
        if (!isIos14OrLater()) return
        val s = Subscriber()
        subscriber = s
        MXMetricManager.sharedManager.addSubscriber(s)
    }

    private fun isIos14OrLater(): Boolean =
        NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 14 }

    private class Subscriber : NSObject(), MXMetricManagerSubscriberProtocol {
        override fun didReceiveMetricPayloads(payloads: List<*>) {}

        override fun didReceiveDiagnosticPayloads(payloads: List<*>) {
            for (p in payloads) {
                val payload = p as? MXDiagnosticPayload ?: continue
                payload.crashDiagnostics?.forEach { c ->
                    (c as? MXCrashDiagnostic)?.let { reportCrash(it) }
                }
                payload.hangDiagnostics?.forEach { h ->
                    (h as? MXHangDiagnostic)?.let { reportHang(it) }
                }
            }
        }

        private fun reportCrash(crash: MXCrashDiagnostic) {
            val attrs = mutableMapOf("crash.type" to "metrickit_crash")
            crash.signal?.let { attrs["crash.signal_number"] = it.stringValue }
            crash.exceptionType?.let { attrs["crash.mach_exception_code"] = it.stringValue }
            crash.terminationReason?.let { attrs["error.message"] = it }
            dataToString(crash.callStackTree.JSONRepresentation())?.let {
                attrs["crash.callstack_tree_json"] = it
            }
            ScoutEngine.reportNativeCrash(attrs)
        }

        private fun reportHang(hang: MXHangDiagnostic) {
            val durationMs =
                (hang.hangDuration.measurementByConvertingToUnit(NSUnitDuration.seconds).doubleValue * 1000).toLong()
            val stack = dataToString(hang.callStackTree.JSONRepresentation()) ?: ""
            ScoutEngine.reportAnr(durationMs, stack)
        }

        private fun dataToString(data: NSData): String? =
            NSString.create(data, NSUTF8StringEncoding) as String?
    }
}
