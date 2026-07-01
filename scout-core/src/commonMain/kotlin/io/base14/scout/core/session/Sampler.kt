package io.base14.scout.core.session

object Sampler {
    fun decide(sessionId: String, sampleRatePercent: Double): Boolean {
        if (sampleRatePercent >= 100.0) return true
        if (sampleRatePercent <= 0.0) return false
        val bucket = (fnv1a(sessionId) % 10_000UL).toDouble() / 100.0
        return bucket < sampleRatePercent
    }

    private fun fnv1a(s: String): ULong {
        var h = 0xcbf29ce484222325UL
        for (b in s.encodeToByteArray()) {
            h = h xor (b.toULong() and 0xffUL)
            h *= 0x100000001b3UL
        }
        return h
    }
}
