package io.base14.hybrid_demo

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread

/**
 * Flutter UI host. Launched from [HostActivity] in the same process. Registers
 * the `hybrid_demo/native_triggers` MethodChannel so the Flutter screen's
 * "Native ANR" / "Native Crash" buttons can run real native code — mirroring
 * the `crash_harness` channel pattern in the platform_design sample.
 */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "hybrid_demo/native_triggers",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "simulateAnr" -> {
                    // Block the platform main thread. Bounded so the tester
                    // recovers without force-quitting.
                    val durationMs = call.argument<Int>("durationMs")?.toLong() ?: 5000L
                    Thread.sleep(durationMs)
                    result.success(null)
                }
                "simulateCrash" -> {
                    // Uncaught NPE on a background thread -> JVM crash.
                    thread(name = "native-crash") {
                        Thread.sleep(50)
                        val s: String? = null
                        s!!.length
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
