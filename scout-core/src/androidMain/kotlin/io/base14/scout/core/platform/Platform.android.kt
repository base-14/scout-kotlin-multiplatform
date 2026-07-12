package io.base14.scout.core.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

actual fun epochNanos(): Long = System.currentTimeMillis() * 1_000_000L

actual fun epochMillis(): Long = System.currentTimeMillis()

private val isoFormat: SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

actual fun isoUtc(epochMillis: Long): String = synchronized(isoFormat) { isoFormat.format(Date(epochMillis)) }

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
