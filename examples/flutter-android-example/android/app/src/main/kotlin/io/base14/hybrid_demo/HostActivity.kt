package io.base14.hybrid_demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.base14.hybrid_demo.shared.HostScreen
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Native Kotlin host. This is the app's launcher activity (a plain
 * [ComponentActivity]). Its UI is [HostScreen] — a Compose Multiplatform
 * composable from the KMP `:shared` module. The platform-specific trigger
 * effects are implemented here and handed to the shared composable as callbacks.
 * "Open Flutter Screen" launches [MainActivity] (a FlutterActivity) in the same
 * process.
 */
class HostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HostScreen(
                onNativeAnr = {
                    // Block the main thread past the ANR threshold. Bounded so the
                    // tester recovers without force-quitting.
                    Thread.sleep(5000)
                },
                onNativeCrash = {
                    // Real native crash: deliver SIGSEGV to our own process → the NDK signal handler
                    // captures it (registers, signal, binary images, device/memory state) → native_crash.
                    android.os.Process.sendSignal(android.os.Process.myPid(), 11)
                },
                onNativeException = {
                    // Uncaught on the main thread → the SDK's global handler auto-captures it
                    // (app_crash + error with stack, replayed on next launch). Fatal, like a crash.
                    throw IllegalStateException("Test native exception from HostActivity")
                },
                onHttpCall = {
                    // Network GET off the main thread.
                    thread(name = "native-http") {
                        var conn: HttpURLConnection? = null
                        try {
                            conn = (URL("https://httpbin.org/get").openConnection()
                                as HttpURLConnection)
                            conn.requestMethod = "GET"
                            val code = conn.responseCode
                            conn.inputStream.use { it.readBytes() }
                            Log.i("HostActivity", "HTTP GET -> $code")
                        } catch (e: Exception) {
                            Log.w("HostActivity", "HTTP call failed", e)
                        } finally {
                            conn?.disconnect()
                        }
                    }
                },
                onOpenFlutter = { openFlutter() },
            )
        }
    }

    private fun openFlutter() {
        // Same-process Intent launch of our MainActivity (a FlutterActivity that
        // also wires up the native_triggers MethodChannel). We target MainActivity
        // explicitly rather than FlutterActivity.createDefaultIntent(...), which
        // would point at the undeclared base FlutterActivity class. No
        // android:process is set on either activity, so both share this app's
        // default process.
        startActivity(Intent(this, MainActivity::class.java))
    }
}
