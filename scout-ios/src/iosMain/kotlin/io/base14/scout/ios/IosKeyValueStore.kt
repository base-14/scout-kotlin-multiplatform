package io.base14.scout.ios

import io.base14.scout.core.platform.KeyValueStore
import platform.Foundation.NSUserDefaults

class IosKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override fun putStringDurable(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }
}
