package io.base14.scout.core.platform

import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

actual fun epochNanos(): Long {
    val i = Instant.now()
    return i.epochSecond * 1_000_000_000L + i.nano
}

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun isoUtc(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

actual fun randomUuidString(): String = UUID.randomUUID().toString()

actual fun systemFileSystem(): okio.FileSystem = okio.FileSystem.SYSTEM

actual class ScoutLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
