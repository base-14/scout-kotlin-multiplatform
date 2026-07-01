package io.base14.scout.android.internal

import android.content.Context
import io.base14.scout.core.platform.KeyValueStore

internal class AndroidKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("scout_rum", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    override fun putStringDurable(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
