package io.base14.scout.android.instrumentation

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import io.base14.scout.android.internal.ActivityLifecycleCallbacksAdapter
import io.base14.scout.android.internal.GuestContainers
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class TapInstrumentation(
    private val app: Application,
    private val core: ScoutCore,
) {
    fun install() {
        app.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacksAdapter() {
                override fun onActivityResumed(activity: Activity) {
                    if (GuestContainers.isGuest(activity)) return
                    val window = activity.window
                    val existing = window.callback
                    if (existing == null || existing is ScoutWindowCallback) return
                    window.callback = ScoutWindowCallback(window, existing, core)
                }
            },
        )
    }
}

private class ScoutWindowCallback(
    private val window: Window,
    private val delegate: Window.Callback,
    private val core: ScoutCore,
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            runCatching { handleTap(event) }
        }
        return delegate.dispatchTouchEvent(event)
    }

    private fun handleTap(event: MotionEvent) {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        var label = "unknown"
        var targetType = "unknown"
        var nameSource = "view"

        val compose =
            runCatching {
                findComposeView(window.decorView)?.let { ComposeTapResolver.resolve(it, x, y) }
            }.getOrNull()

        if (compose != null) {
            label = compose.label
            targetType = compose.role
            nameSource = "semantics"
        } else {
            val target = findTarget(window.decorView, x, y)
            if (target != null) {
                label = labelOf(target)
                targetType = target::class.java.simpleName
            }
        }

        VitalTracker.onInteraction()
        core.addBreadcrumb("tap", label)
        core.emit(
            ScoutSpans.USER_INTERACTION,
            mapOf(
                ScoutAttributes.UI_ID to randomUuidString(),
                ScoutAttributes.UI_TYPE to "tap",
                ScoutAttributes.UI_TARGET to label,
                ScoutAttributes.UI_TARGET_TYPE to targetType,
                ScoutAttributes.UI_TARGET_NAME_SOURCE to nameSource,
                ScoutAttributes.UI_TARGET_PERMANENT_ID to permanentIdOf(label, targetType),
                ScoutAttributes.UI_TARGET_X to x,
                ScoutAttributes.UI_TARGET_Y to y,
            ),
        )
    }

    private fun permanentIdOf(label: String, targetType: String): String {
        var h = 0
        for (c in "$label|$targetType") h = h * 31 + c.code
        return (h.toLong() and 0xffffffffL).toString(16)
    }
}

private fun findComposeView(v: View): View? {
    if (v.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return v
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            findComposeView(v.getChildAt(i))?.let { return it }
        }
    }
    return null
}

private fun findTarget(
    root: View,
    x: Int,
    y: Int,
): View? {
    if (root.visibility != View.VISIBLE) return null
    if (root is ViewGroup) {
        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChildAt(i)
            if (hits(child, x, y)) {
                findTarget(child, x, y)?.let { return it }
            }
        }
    }
    return if (root.isClickable) root else null
}

private fun hits(
    v: View,
    x: Int,
    y: Int,
): Boolean {
    if (v.visibility != View.VISIBLE) return false
    val loc = IntArray(2)
    v.getLocationOnScreen(loc)
    return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
}

private fun labelOf(v: View): String {
    v.contentDescription?.let { if (it.isNotBlank()) return it.toString() }
    if (v.id != View.NO_ID) {
        runCatching { return v.resources.getResourceEntryName(v.id) }
    }
    if (v is TextView) v.text?.let { if (it.isNotBlank()) return it.toString() }
    return v::class.java.simpleName
}
