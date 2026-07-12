package io.base14.scout.core.platform

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatWithFractionalSeconds
import platform.Foundation.NSISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun epochNanos(): Long = (NSDate().timeIntervalSince1970 * 1_000_000_000.0).toLong()

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

actual fun isoUtc(epochMillis: Long): String {
    val formatter = NSISO8601DateFormatter()
    formatter.formatOptions =
        NSISO8601DateFormatWithInternetDateTime or NSISO8601DateFormatWithFractionalSeconds
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1_000.0))
}

actual fun randomUuidString(): String = NSUUID().UUIDString()

actual fun systemFileSystem(): okio.FileSystem = okio.FileSystem.SYSTEM

actual class ScoutLock actual constructor() {
    private val lock = NSRecursiveLock()
    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
