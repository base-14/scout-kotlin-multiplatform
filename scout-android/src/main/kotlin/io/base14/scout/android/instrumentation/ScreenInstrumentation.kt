package io.base14.scout.android.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.base14.scout.android.internal.ActivityLifecycleCallbacksAdapter
import io.base14.scout.android.internal.screenNameOf

internal class ScreenInstrumentation(
    private val app: Application,
    private val tracker: ScreenTracker,
) {
    private val createdAt = HashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())

    fun install() {
        app.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacksAdapter() {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    createdAt[keyOf(activity)] = SystemClock.uptimeMillis()
                }

                override fun onActivityResumed(activity: Activity) {
                    val created = createdAt.remove(keyOf(activity))
                    if (isGuestContainer(activity)) return
                    val loadMs = created?.let { SystemClock.uptimeMillis() - it }
                    val name = screenNameOf(activity)
                    handler.postDelayed({ tracker.enterFromActivity(name, loadMs) }, 250)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (isGuestContainer(activity)) return
                    tracker.exitFromActivity()
                }
            },
        )
    }

    private fun keyOf(a: Activity) = a::class.qualifiedName + "@" + System.identityHashCode(a)

    private fun isGuestContainer(activity: Activity) = io.base14.scout.android.internal.GuestContainers.isGuest(activity)
}
