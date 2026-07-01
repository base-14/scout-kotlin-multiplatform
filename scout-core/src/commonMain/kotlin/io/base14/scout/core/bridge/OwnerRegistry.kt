package io.base14.scout.core.bridge

import io.base14.scout.core.platform.ScoutLock

interface OwnerRegistry {
    fun read(): OwnerRecord?

    fun claim(record: OwnerRecord): Boolean

    fun update(record: OwnerRecord)

    fun clear()
}

class InMemoryOwnerRegistry : OwnerRegistry {
    private val lock = ScoutLock()
    private var record: OwnerRecord? = null

    override fun read(): OwnerRecord? = lock.withLock { record }

    override fun claim(record: OwnerRecord): Boolean = lock.withLock {
        if (this.record?.isOwned == true) {
            false
        } else {
            this.record = record
            true
        }
    }

    override fun update(record: OwnerRecord): Unit = lock.withLock { this.record = record }

    override fun clear(): Unit = lock.withLock { record = null }
}
