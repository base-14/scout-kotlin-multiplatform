package io.base14.scout.core.platform

expect fun epochNanos(): Long

expect fun epochMillis(): Long

expect fun isoUtc(epochMillis: Long): String

expect fun randomUuidString(): String

expect fun systemFileSystem(): okio.FileSystem

expect class ScoutLock() {
    fun <T> withLock(block: () -> T): T
}

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)

    fun putStringDurable(key: String, value: String) = putString(key, value)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
    override fun remove(key: String) {
        map.remove(key)
    }
}
