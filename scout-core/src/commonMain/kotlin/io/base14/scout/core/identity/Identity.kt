package io.base14.scout.core.identity

import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.platform.randomUuidString

class Identity(private val store: KeyValueStore) {

    val anonymousId: String =
        store.getString(KEY_ANON) ?: randomUuidString().also { store.putString(KEY_ANON, it) }

    var userId: String? = null
        private set

    private val attrs = LinkedHashMap<String, String>()
    val userAttributes: Map<String, String> get() = attrs

    fun setUser(id: String?, attributes: Map<String, String> = emptyMap()) {
        userId = id?.takeIf { it.isNotBlank() }
        attrs.clear()
        for ((k, v) in attributes) {
            val key = if (k.startsWith("user.")) k else "user.$k"
            attrs[key] = v
        }
    }

    fun setUserAttributes(attributes: Map<String, String>) {
        for ((k, v) in attributes) {
            val key = if (k.startsWith("user.")) k else "user.$k"
            attrs[key] = v
        }
    }

    fun clearUser() {
        userId = null
        attrs.clear()
    }

    companion object {
        private const val KEY_ANON = "scout.anonymous_id"
    }
}
