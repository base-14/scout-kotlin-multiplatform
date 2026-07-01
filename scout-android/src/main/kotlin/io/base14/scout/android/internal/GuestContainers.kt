package io.base14.scout.android.internal

import android.app.Activity

internal object GuestContainers {
    private val CLASS_NAMES = setOf("FlutterActivity", "FlutterFragmentActivity")

    fun isGuest(activity: Activity): Boolean {
        var c: Class<*>? = activity::class.java
        while (c != null) {
            if (c.simpleName in CLASS_NAMES) return true
            c = c.superclass
        }
        return false
    }
}
