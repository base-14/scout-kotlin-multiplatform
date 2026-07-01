package io.base14.hybrid_demo

import android.app.Application
import io.base14.scout.android.Scout
import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutRole

/**
 * Initializes the Scout native SDK once, process-wide, in `onCreate` — before any Activity (native
 * `HostActivity` or the embedded `FlutterActivity`). As the host runtime this is the bridge OWNER:
 * it owns the session, identity, and export pipeline; the Flutter SDK attaches to it (Step 2).
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Scout.initialize(
            this,
            ScoutConfig(
                serviceName = "hybrid-demo",
                serviceVersion = "1.0.0",
                endpoint = "https://your-collector.example/otlp",
                headers = mapOf(
                    "Authorization" to "Bearer <YOUR_SCOUT_INGEST_TOKEN>",
                ),
                environment = "production",
                sessionSampleRate = 100.0,
                role = ScoutRole.OWNER,
            ),
        )
    }
}
