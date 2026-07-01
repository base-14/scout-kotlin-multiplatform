package io.base14.scout.android.instrumentation

internal object FrameStats {
    private val lock = Any()
    private var count = 0L
    private var sumMs = 0.0

    fun record(durationMs: Double) =
        synchronized(lock) {
            count++
            sumMs += durationMs
        }

    fun drainAverageMs(): Double? =
        synchronized(lock) {
            if (count == 0L) {
                null
            } else {
                (sumMs / count).also {
                    count = 0L
                    sumMs = 0.0
                }
            }
        }
}
