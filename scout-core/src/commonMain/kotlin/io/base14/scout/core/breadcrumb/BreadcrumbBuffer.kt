package io.base14.scout.core.breadcrumb

import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.platform.ScoutLock
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

data class Breadcrumb(val type: String, val message: String, val timeIso: String)

class BreadcrumbBuffer(private val max: Int = 100, private val store: KeyValueStore? = null) {
    private val items = ArrayDeque<Breadcrumb>()
    private val lock = ScoutLock()

    val previousSessionJson: String = store?.getString(KEY)?.takeIf { it.isNotBlank() } ?: "[]"

    fun add(type: String, message: String): Unit = addAt(type, message, isoUtc(epochMillis()))

    fun addAt(type: String, message: String, timeIso: String): Unit = lock.withLock {
        if (items.size >= max) items.removeFirst()
        items.addLast(Breadcrumb(type, message, timeIso))
        store?.putString(KEY, jsonOf(items))
    }

    fun setAll(crumbs: List<Pair<String, String>>): Unit = lock.withLock {
        items.clear()
        val now = isoUtc(epochMillis())
        for ((type, message) in crumbs.takeLast(max)) items.addLast(Breadcrumb(type, message, now))
        store?.putString(KEY, jsonOf(items))
    }

    fun snapshot(): List<Breadcrumb> = lock.withLock { items.toList() }

    fun toJson(): String = lock.withLock { jsonOf(items) }

    private fun jsonOf(list: Collection<Breadcrumb>): String =
        buildJsonArray {
            for (b in list) {
                addJsonObject {
                    put("type", b.type)
                    put("message", b.message)
                    put("time", b.timeIso)
                }
            }
        }.toString()

    companion object {
        const val KEY = "scout.breadcrumbs"
    }
}
