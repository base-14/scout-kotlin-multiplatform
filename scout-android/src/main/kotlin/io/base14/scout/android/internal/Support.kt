package io.base14.scout.android.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

internal fun createScoutHttpClient(keepAliveSeconds: Long = 65): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                connectionPool(ConnectionPool(5, keepAliveSeconds, TimeUnit.SECONDS))
                retryOnConnectionFailure(true)
            }
        }
    }

internal fun secondsString(ms: Long): String = (ms / 1000.0).toString()

internal object CurrentScreen {
    @Volatile var name: String? = null
}

internal abstract class ActivityLifecycleCallbacksAdapter : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {}

    override fun onActivityDestroyed(activity: Activity) {}
}

internal fun screenNameOf(activity: Activity): String = activity::class.simpleName ?: activity::class.qualifiedName ?: "Unknown"
